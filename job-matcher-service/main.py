"""Job matcher FastAPI microservice."""

from __future__ import annotations

import json
import logging
import os
from copy import deepcopy
from decimal import Decimal, ROUND_HALF_UP
from typing import Any

from anthropic import Anthropic, APIError, APIStatusError
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("job-matcher-service")

DEFAULT_CLAUDE_MODEL = "claude-sonnet-5-5"
SCORE_TOOL = "score_candidate"
COMPARE_TOOL = "compare_candidates"
FORMULA_VERSION = "v1"
MOCK_MODEL = "mock"
SKILLS_WEIGHT = Decimal("0.50")
EXPERIENCE_WEIGHT = Decimal("0.35")
EDUCATION_WEIGHT = Decimal("0.15")
MOCK_SKILLS_SCORE = 80.0
MOCK_EXPERIENCE_SCORE = 60.0
MOCK_EDUCATION_SCORE = 40.0
MOCK_COMPARE_REASON = "Mock comparison keeps the input order."
MIN_COMPARE_CANDIDATES = 2
MAX_COMPARE_CANDIDATES = 5

_SCHEMA_KEY_ORDER = (
    "description",
    "type",
    "properties",
    "items",
    "required",
    "additionalProperties",
)


class WorkExperience(BaseModel):
    model_config = ConfigDict(extra="forbid")

    company: str = Field(..., description="Employer or organization name")
    role: str = Field(..., description="Job title or role")
    duration: str = Field(..., description="Employment period, e.g. 'Jan 2020 - Present'")
    description: str = Field(..., description="Summary of responsibilities and achievements")


class Education(BaseModel):
    model_config = ConfigDict(extra="forbid")

    institution: str = Field(..., description="School, university, or training organization")
    degree: str = Field(..., description="Degree or credential, e.g. 'B.S.' or 'Bootcamp Certificate'")
    field_of_study: str = Field(..., description="Major or field of study, or empty string if not found")
    duration: str = Field(..., description="Study period, e.g. '2016 - 2020'")


class BlindCandidate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    skills: list[str] = Field(..., description="Skills used for scoring. Name and contact are not included.")
    years_of_experience: float = Field(..., description="Total years of professional experience")
    work_history: list[WorkExperience] = Field(..., description="Work history without identity fields")
    education: list[Education] = Field(..., description="Education history without identity fields")


class ScoreRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    candidate_id: str = Field(..., min_length=1, description="Caller-supplied id echoed on the score result")
    job_description: str = Field(..., min_length=1, description="Job the candidate is scored against")
    candidate: BlindCandidate = Field(..., description="Blind candidate profile")

    @field_validator("candidate_id", "job_description")
    @classmethod
    def reject_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


class ScoreAssessment(BaseModel):
    model_config = ConfigDict(extra="forbid")

    skills_score: float = Field(..., ge=0, le=100, description="Skills match from 0 to 100")
    experience_score: float = Field(..., ge=0, le=100, description="Experience match from 0 to 100")
    education_score: float = Field(..., ge=0, le=100, description="Education match from 0 to 100")
    matched_skills: list[str] = Field(..., description="Skills from the candidate that match the job")
    missing_skills: list[str] = Field(..., description="Job skills the candidate does not show")
    strengths: list[str] = Field(..., description="Short evidence statements for the score")
    concerns: list[str] = Field(..., description="Short evidence statements against the score")
    summary: str = Field(..., description="One-paragraph scoring summary")


class ScoreResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    candidate_id: str = Field(..., description="Caller-supplied id from the request")
    skills_score: float = Field(..., ge=0, le=100, description="Skills match from 0 to 100")
    experience_score: float = Field(..., ge=0, le=100, description="Experience match from 0 to 100")
    education_score: float = Field(..., ge=0, le=100, description="Education match from 0 to 100")
    overall_score: float = Field(..., description="Weighted score computed in code, formula_version v1")
    matched_skills: list[str] = Field(..., description="Skills from the candidate that match the job")
    missing_skills: list[str] = Field(..., description="Job skills the candidate does not show")
    strengths: list[str] = Field(..., description="Short evidence statements for the score")
    concerns: list[str] = Field(..., description="Short evidence statements against the score")
    summary: str = Field(..., description="One-paragraph scoring summary")
    formula_version: str = Field(..., description="Weight formula that produced overall_score")
    model: str = Field(..., description="Claude model id, or mock when no API key is set")


class CompareCandidate(BaseModel):
    model_config = ConfigDict(extra="forbid")

    candidate_id: str = Field(..., min_length=1, description="Caller-supplied id for this candidate")
    skills: list[str] = Field(..., description="Skills used for scoring")
    years_of_experience: float = Field(..., description="Total years of professional experience")
    work_history: list[WorkExperience] = Field(..., description="Work history without identity fields")
    education: list[Education] = Field(..., description="Education history without identity fields")
    skills_score: float = Field(..., ge=0, le=100, description="Skills sub-score from /score")
    experience_score: float = Field(..., ge=0, le=100, description="Experience sub-score from /score")
    education_score: float = Field(..., ge=0, le=100, description="Education sub-score from /score")
    summary: str = Field(..., description="Scoring summary from /score")

    @field_validator("candidate_id")
    @classmethod
    def reject_blank_id(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


class CompareRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    job_description: str = Field(..., min_length=1, description="Job the candidates are compared against")
    candidates: list[CompareCandidate] = Field(
        ...,
        min_length=MIN_COMPARE_CANDIDATES,
        max_length=MAX_COMPARE_CANDIDATES,
        description="Two to five already-scored blind candidates",
    )

    @field_validator("job_description")
    @classmethod
    def reject_blank_job(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value

    @model_validator(mode="after")
    def unique_candidate_ids(self) -> CompareRequest:
        ids = [candidate.candidate_id for candidate in self.candidates]
        if len(ids) != len(set(ids)):
            raise ValueError("candidate_id values must be unique")
        return self


class ComparisonEntry(BaseModel):
    model_config = ConfigDict(extra="forbid")

    candidate_id: str = Field(..., min_length=1, description="Id of a candidate from the request")
    rank: int = Field(..., ge=1, description="1 is the best relative fit")
    reason: str = Field(..., min_length=1, description="One-line reason for this rank")

    @field_validator("candidate_id", "reason")
    @classmethod
    def reject_blank(cls, value: str) -> str:
        if not value.strip():
            raise ValueError("must not be blank")
        return value


class CompareAssessment(BaseModel):
    model_config = ConfigDict(extra="forbid")

    rankings: list[ComparisonEntry] = Field(..., description="Relative order of the candidates sent")


class CompareResponse(BaseModel):
    model_config = ConfigDict(extra="forbid")

    rankings: list[ComparisonEntry] = Field(..., description="Relative order, best rank first")
    model: str = Field(..., description="Claude model id, or mock when no API key is set")


def json_schema_for(model: type[BaseModel]) -> dict[str, Any]:
    """JSON Schema for a model, with nested models inlined and Pydantic titles removed."""
    schema = model.model_json_schema()
    definitions = schema.pop("$defs", {})

    def resolve(node: Any) -> Any:
        if isinstance(node, dict):
            if "$ref" in node:
                ref_name = node["$ref"].rsplit("/", 1)[-1]
                extras = {key: resolve(value) for key, value in node.items() if key != "$ref"}
                resolved = resolve(deepcopy(definitions[ref_name]))
                resolved.update(extras)
                return _order_schema(resolved)
            cleaned: dict[str, Any] = {}
            for key, value in node.items():
                if key == "title":
                    continue
                if key == "properties" and isinstance(value, dict):
                    cleaned[key] = {name: resolve(child) for name, child in value.items()}
                else:
                    cleaned[key] = resolve(value)
            return _order_schema(cleaned)
        if isinstance(node, list):
            return [resolve(item) for item in node]
        return node

    return resolve(schema)


def _order_schema(node: dict[str, Any]) -> dict[str, Any]:
    ordered = {key: node[key] for key in _SCHEMA_KEY_ORDER if key in node}
    for key, value in node.items():
        if key not in ordered:
            ordered[key] = value
    return ordered


SCORE_TOOL_SCHEMA: dict[str, Any] = {
    "name": SCORE_TOOL,
    "description": (
        "Score one blind candidate against a job. Return sub-scores and evidence only. "
        "Do not return an overall score."
    ),
    "input_schema": json_schema_for(ScoreAssessment),
}

COMPARE_TOOL_SCHEMA: dict[str, Any] = {
    "name": COMPARE_TOOL,
    "description": (
        "Rank the given candidates against one job. Rank 1 is the best fit. "
        "Return each candidate id once with a one-line reason."
    ),
    "input_schema": json_schema_for(CompareAssessment),
}

app = FastAPI(
    title="Job Matcher Service",
    description="Score a blind candidate against a job, then compare the top candidates.",
    version="1.0.0",
)


def weighted_overall_score(skills_score: float, experience_score: float, education_score: float) -> float:
    """overall = 0.50*skills + 0.35*experience + 0.15*education, rounded half up to 1 decimal."""
    total = (
        SKILLS_WEIGHT * Decimal(str(skills_score))
        + EXPERIENCE_WEIGHT * Decimal(str(experience_score))
        + EDUCATION_WEIGHT * Decimal(str(education_score))
    )
    return float(total.quantize(Decimal("0.1"), rounding=ROUND_HALF_UP))


def get_anthropic_api_key() -> str | None:
    key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    return key or None


def get_claude_model() -> str:
    model = os.getenv("CLAUDE_MODEL", "").strip()
    return model or DEFAULT_CLAUDE_MODEL


def _provider_error(exc: Exception) -> HTTPException:
    if isinstance(exc, APIStatusError):
        logger.exception("Anthropic API returned an error status: %s", exc.status_code)
        return HTTPException(status_code=502, detail="Job matching provider returned an error.")
    if isinstance(exc, APIError):
        logger.exception("Anthropic API request failed")
        return HTTPException(status_code=502, detail="Failed to reach job matching provider.")
    logger.exception("Unexpected error while calling Claude")
    return HTTPException(status_code=500, detail="Unexpected error while matching.")


def call_tool(api_key: str, tool_name: str, tool_schema: dict[str, Any], user_content: str) -> dict[str, Any]:
    model = get_claude_model()
    client = Anthropic(api_key=api_key)
    logger.info("Calling Claude model %s tool %s", model, tool_name)

    try:
        response = client.messages.create(
            model=model,
            max_tokens=4096,
            tools=[tool_schema],
            tool_choice={"type": "tool", "name": tool_name},
            messages=[{"role": "user", "content": user_content}],
        )
    except (APIStatusError, APIError) as exc:
        raise _provider_error(exc) from exc
    except Exception as exc:
        raise _provider_error(exc) from exc

    tool_input: dict[str, Any] | None = None
    for block in response.content:
        if getattr(block, "type", None) == "tool_use" and getattr(block, "name", None) == tool_name:
            tool_input = dict(block.input)
            break

    if tool_input is None:
        logger.error("Claude response did not include %s tool use: %s", tool_name, response.content)
        raise HTTPException(status_code=502, detail="Matcher did not return structured data.")

    return tool_input


def _score_from_assessment(candidate_id: str, assessment: ScoreAssessment, model: str) -> ScoreResponse:
    return ScoreResponse(
        candidate_id=candidate_id,
        skills_score=assessment.skills_score,
        experience_score=assessment.experience_score,
        education_score=assessment.education_score,
        overall_score=weighted_overall_score(
            assessment.skills_score,
            assessment.experience_score,
            assessment.education_score,
        ),
        matched_skills=assessment.matched_skills,
        missing_skills=assessment.missing_skills,
        strengths=assessment.strengths,
        concerns=assessment.concerns,
        summary=assessment.summary,
        formula_version=FORMULA_VERSION,
        model=model,
    )


def mock_score(candidate_id: str) -> ScoreResponse:
    logger.info("ANTHROPIC_API_KEY is missing; returning mock score for candidate_id=%s", candidate_id)
    assessment = ScoreAssessment(
        skills_score=MOCK_SKILLS_SCORE,
        experience_score=MOCK_EXPERIENCE_SCORE,
        education_score=MOCK_EDUCATION_SCORE,
        matched_skills=["Python"],
        missing_skills=["Kubernetes"],
        strengths=["Backend API work"],
        concerns=["Limited production operations evidence"],
        summary="Mock score used because no API key is configured.",
    )
    return _score_from_assessment(candidate_id, assessment, MOCK_MODEL)


def score_with_claude(request: ScoreRequest, api_key: str) -> ScoreResponse:
    body = {
        "job_description": request.job_description,
        "candidate": request.candidate.model_dump(),
    }
    prompt = (
        "Score this candidate against the job. Call the score_candidate tool with sub-scores and evidence. "
        "Do not invent an overall score.\n\n"
        f"{json.dumps(body)}"
    )
    tool_input = call_tool(api_key, SCORE_TOOL, SCORE_TOOL_SCHEMA, prompt)
    try:
        assessment = ScoreAssessment.model_validate(tool_input)
    except ValidationError as exc:
        logger.error("Claude score payload failed validation: %s", exc)
        raise HTTPException(
            status_code=502,
            detail="Matcher returned data that did not match the expected schema.",
        ) from exc

    model = get_claude_model()
    logger.info("Scored candidate_id=%s model=%s", request.candidate_id, model)
    return _score_from_assessment(request.candidate_id, assessment, model)


def _expected_ranks(count: int) -> list[int]:
    return list(range(1, count + 1))


def validate_rankings(entries: list[ComparisonEntry], expected_ids: list[str]) -> list[ComparisonEntry]:
    ids = [entry.candidate_id for entry in entries]
    ranks = [entry.rank for entry in entries]
    if len(ids) != len(set(ids)) or set(ids) != set(expected_ids) or sorted(ranks) != _expected_ranks(len(expected_ids)):
        logger.error("Comparison ranking did not match candidates: ids=%s ranks=%s", ids, ranks)
        raise HTTPException(
            status_code=502,
            detail="Comparison ranking did not match the candidates sent.",
        )
    return sorted(entries, key=lambda entry: entry.rank)


def mock_compare(candidates: list[CompareCandidate]) -> CompareResponse:
    logger.info("ANTHROPIC_API_KEY is missing; returning mock comparison for %s candidates", len(candidates))
    rankings = [
        ComparisonEntry(candidate_id=candidate.candidate_id, rank=index, reason=MOCK_COMPARE_REASON)
        for index, candidate in enumerate(candidates, start=1)
    ]
    return CompareResponse(rankings=rankings, model=MOCK_MODEL)


def compare_with_claude(request: CompareRequest, api_key: str) -> CompareResponse:
    payload = {
        "job_description": request.job_description,
        "candidates": [candidate.model_dump() for candidate in request.candidates],
    }
    prompt = (
        "Rank these candidates for the job. Call the compare_candidates tool. "
        "Rank 1 is the best fit. Include every candidate id once and a one-line reason.\n\n"
        f"{json.dumps(payload)}"
    )
    tool_input = call_tool(api_key, COMPARE_TOOL, COMPARE_TOOL_SCHEMA, prompt)
    try:
        assessment = CompareAssessment.model_validate(tool_input)
    except ValidationError as exc:
        logger.error("Claude comparison payload failed validation: %s", exc)
        raise HTTPException(
            status_code=502,
            detail="Matcher returned data that did not match the expected schema.",
        ) from exc

    expected_ids = [candidate.candidate_id for candidate in request.candidates]
    rankings = validate_rankings(assessment.rankings, expected_ids)
    model = get_claude_model()
    logger.info("Compared %s candidates model=%s", len(rankings), model)
    return CompareResponse(rankings=rankings, model=model)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/score", response_model=ScoreResponse)
def score(request: ScoreRequest) -> ScoreResponse:
    api_key = get_anthropic_api_key()
    if api_key is None:
        return mock_score(request.candidate_id)
    return score_with_claude(request, api_key)


@app.post("/compare", response_model=CompareResponse)
def compare(request: CompareRequest) -> CompareResponse:
    api_key = get_anthropic_api_key()
    if api_key is None:
        return mock_compare(request.candidates)
    return compare_with_claude(request, api_key)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8001, reload=True)
