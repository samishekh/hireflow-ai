"""Score paths with a stubbed Claude client. No API key required."""

import json

import pytest
from anthropic import APIError, APIStatusError
from fastapi.testclient import TestClient
import httpx2

import main

JOB = "Backend engineer who ships APIs."

CANDIDATE = {
    "skills": ["Python", "FastAPI"],
    "years_of_experience": 5.0,
    "work_history": [
        {
            "company": "Acme",
            "role": "Engineer",
            "duration": "2021 - Present",
            "description": "Built APIs.",
        }
    ],
    "education": [
        {
            "institution": "State University",
            "degree": "B.S.",
            "field_of_study": "Computer Science",
            "duration": "2015 - 2019",
        }
    ],
}

ASSESSMENT = {
    "skills_score": 90.0,
    "experience_score": 70.0,
    "education_score": 40.0,
    "matched_skills": ["Python"],
    "missing_skills": ["Kubernetes"],
    "strengths": ["API work"],
    "concerns": ["No operations depth"],
    "summary": "Strong API background.",
}


class _Block:
    def __init__(self, type: str, name: str | None = None, payload: dict | None = None):
        self.type = type
        self.name = name
        self.input = payload


class _Response:
    def __init__(self, content: list | None = None, parsed_output: object = None):
        self.content = content or []
        self.parsed_output = parsed_output


class _Anthropic:
    outcome: object = None
    constructed: int = 0
    calls: list[dict] = []

    def __init__(self, api_key: str):
        type(self).constructed += 1
        self.api_key = api_key
        self.messages = self

    def parse(self, **kwargs):
        type(self).calls.append(kwargs)
        outcome = type(self).outcome
        if isinstance(outcome, BaseException):
            raise outcome
        return outcome


def _request() -> httpx2.Request:
    return httpx2.Request("POST", "https://api.anthropic.com/v1/messages")


def _parsed_response(payload: dict) -> _Response:
    return _Response(parsed_output=payload)


@pytest.fixture
def client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.delenv("HIREFLOW_RECORDED_SCORES", raising=False)
    monkeypatch.setattr(main, "Anthropic", _Anthropic)
    _Anthropic.constructed = 0
    _Anthropic.calls = []
    _Anthropic.outcome = _parsed_response(ASSESSMENT)
    return TestClient(main.app)


def _score_body(**overrides) -> dict:
    body = {
        "candidate_id": "cand-1",
        "job_description": JOB,
        "candidate": CANDIDATE,
    }
    body.update(overrides)
    return body


def test_health(client: TestClient):
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json() == {"status": "ok"}


def test_weighted_overall_score_uses_v1_weights():
    assert main.weighted_overall_score(80, 60, 40) == 67.0
    assert main.weighted_overall_score(100, 100, 100) == 100.0
    assert main.weighted_overall_score(0, 0, 0) == 0.0
    assert main.weighted_overall_score(90, 70, 40) == 75.5


def test_structured_payload_becomes_score(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    monkeypatch.delenv("CLAUDE_MODEL", raising=False)

    response = client.post("/score", json=_score_body())

    assert response.status_code == 200
    body = response.json()
    assert body["candidate_id"] == "cand-1"
    assert body["skills_score"] == 90.0
    assert body["experience_score"] == 70.0
    assert body["education_score"] == 40.0
    assert body["overall_score"] == 75.5
    assert body["formula_version"] == "v1"
    assert body["model"] == main.DEFAULT_CLAUDE_MODEL
    assert body["summary"] == ASSESSMENT["summary"]
    assert _Anthropic.constructed == 1
    call = _Anthropic.calls[0]
    assert call["model"] == main.DEFAULT_CLAUDE_MODEL
    assert call["output_format"] is main.ScoreAssessment
    content = call["messages"][0]["content"]
    assert '"candidate_name"' not in content
    assert '"email"' not in content
    assert '"phone"' not in content
    sent = json.loads(content.split("\n\n", 1)[1])
    assert set(sent["candidate"]) == {"skills", "years_of_experience", "work_history", "education"}


def test_overall_score_from_claude_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = _parsed_response({**ASSESSMENT, "overall_score": 99})

    response = client.post("/score", json=_score_body())

    assert response.status_code == 502
    assert response.json()["detail"] == "Matcher returned data that did not match the expected schema."


def test_identity_fields_on_score_request_return_422(client: TestClient):
    candidate = {**CANDIDATE, "candidate_name": "Ada Lovelace", "email": "ada@example.com"}
    response = client.post("/score", json=_score_body(candidate=candidate))

    assert response.status_code == 422


def test_blank_job_description_returns_422(client: TestClient):
    response = client.post("/score", json=_score_body(job_description="   "))

    assert response.status_code == 422


def test_missing_structured_output_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = _Response([_Block("text")])

    response = client.post("/score", json=_score_body())

    assert response.status_code == 502
    assert response.json()["detail"] == "Matcher did not return structured data."


def test_api_status_error_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = APIStatusError(
        "overloaded",
        response=httpx2.Response(529, request=_request()),
        body=None,
    )

    response = client.post("/score", json=_score_body())

    assert response.status_code == 502
    assert response.json()["detail"] == "Job matching provider returned an error."


def test_api_error_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = APIError("connection failed", _request(), body=None)

    response = client.post("/score", json=_score_body())

    assert response.status_code == 502
    assert response.json()["detail"] == "Failed to reach job matching provider."


def test_missing_api_key_returns_mock_score(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    response = client.post("/score", json=_score_body())

    assert response.status_code == 200
    body = response.json()
    assert body["overall_score"] == 67.0
    assert body["skills_score"] == 80.0
    assert body["experience_score"] == 60.0
    assert body["education_score"] == 40.0
    assert body["formula_version"] == "v1"
    assert body["model"] == "mock"
    assert body["candidate_id"] == "cand-1"
    assert _Anthropic.constructed == 0
