"""Resume parser FastAPI microservice."""

from __future__ import annotations

import logging
import os
from typing import Any

import pymupdf as fitz
from anthropic import Anthropic, APIError, APIStatusError
from dotenv import load_dotenv
from fastapi import FastAPI, File, HTTPException, UploadFile
from pydantic import BaseModel, Field, ValidationError

load_dotenv()

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
)
logger = logging.getLogger("resume-parser-service")

CLAUDE_MODEL = "claude-3-5-sonnet-20241022"
PARSE_RESUME_TOOL = "parse_resume"
MAX_PDF_BYTES = 10 * 1024 * 1024


class WorkExperience(BaseModel):
    company: str = Field(..., description="Employer or organization name")
    role: str = Field(..., description="Job title or role")
    duration: str = Field(..., description="Employment period, e.g. 'Jan 2020 - Present'")
    description: str = Field(..., description="Summary of responsibilities and achievements")


class Education(BaseModel):
    institution: str = Field(..., description="School, university, or training organization")
    degree: str = Field(..., description="Degree or credential, e.g. 'B.S.' or 'Bootcamp Certificate'")
    field_of_study: str = Field(..., description="Major or field of study, or empty string if not found")
    duration: str = Field(..., description="Study period, e.g. '2016 - 2020'")


class ResumeParsedData(BaseModel):
    candidate_name: str = Field(..., description="Full name of the candidate")
    email: str = Field(..., description="Email address, or empty string if not found")
    phone: str = Field(..., description="Phone number, or empty string if not found")
    skills: list[str] = Field(..., description="List of skills")
    years_of_experience: float = Field(..., description="Total years of professional experience")
    work_history: list[WorkExperience] = Field(..., description="Chronological work experience entries")
    education: list[Education] = Field(..., description="Education history entries")


class ParseResumeResponse(BaseModel):
    raw_text: str = Field(..., description="Text extracted from the PDF before parsing")
    parsed_data: ResumeParsedData = Field(..., description="Structured fields extracted from the resume")


PARSE_RESUME_TOOL_SCHEMA: dict[str, Any] = {
    "name": PARSE_RESUME_TOOL,
    "description": (
        "Extract structured candidate information from raw resume text. "
        "Always call this tool with the parsed fields. Use empty strings "
        "for missing contact or education subfields and an empty list when "
        "skills, work history, or education cannot be determined."
    ),
    "input_schema": {
        "type": "object",
        "properties": {
            "candidate_name": {"type": "string", "description": "Full name of the candidate"},
            "email": {"type": "string", "description": "Email address, or empty string if not found"},
            "phone": {"type": "string", "description": "Phone number, or empty string if not found"},
            "skills": {
                "type": "array",
                "items": {"type": "string"},
                "description": "List of skills",
            },
            "years_of_experience": {
                "type": "number",
                "description": "Total years of professional experience",
            },
            "work_history": {
                "type": "array",
                "description": "Chronological work experience entries",
                "items": {
                    "type": "object",
                    "properties": {
                        "company": {"type": "string"},
                        "role": {"type": "string"},
                        "duration": {"type": "string"},
                        "description": {"type": "string"},
                    },
                    "required": ["company", "role", "duration", "description"],
                    "additionalProperties": False,
                },
            },
            "education": {
                "type": "array",
                "description": "Education history entries",
                "items": {
                    "type": "object",
                    "properties": {
                        "institution": {"type": "string"},
                        "degree": {"type": "string"},
                        "field_of_study": {"type": "string"},
                        "duration": {"type": "string"},
                    },
                    "required": ["institution", "degree", "field_of_study", "duration"],
                    "additionalProperties": False,
                },
            },
        },
        "required": [
            "candidate_name",
            "email",
            "phone",
            "skills",
            "years_of_experience",
            "work_history",
            "education",
        ],
        "additionalProperties": False,
    },
}

MOCK_PARSED_RESUME = ResumeParsedData(
    candidate_name="Jane Doe",
    email="jane.doe@example.com",
    phone="+1-555-0100",
    skills=["Python", "FastAPI", "PostgreSQL", "Docker"],
    years_of_experience=5.0,
    work_history=[
        WorkExperience(
            company="Acme Corp",
            role="Software Engineer",
            duration="Jan 2021 - Present",
            description="Built and maintained backend APIs and data pipelines.",
        ),
        WorkExperience(
            company="Startup Inc",
            role="Junior Developer",
            duration="Jun 2019 - Dec 2020",
            description="Developed internal tools and contributed to the product API.",
        ),
    ],
    education=[
        Education(
            institution="State University",
            degree="B.S.",
            field_of_study="Computer Science",
            duration="2015 - 2019",
        ),
    ],
)

app = FastAPI(
    title="Resume Parser Service",
    description="Extract structured candidate data from PDF resumes.",
    version="1.0.0",
)


def get_anthropic_api_key() -> str | None:
    key = os.getenv("ANTHROPIC_API_KEY", "").strip()
    return key or None


def extract_text_from_pdf(pdf_bytes: bytes) -> str:
    try:
        document = fitz.open(stream=pdf_bytes, filetype="pdf")
    except Exception as exc:
        logger.exception("Failed to open PDF with PyMuPDF")
        raise HTTPException(status_code=400, detail="Unable to read PDF file.") from exc

    try:
        pages: list[str] = []
        for page in document:
            pages.append(page.get_text() or "")
        text = "\n".join(pages).strip()
    except Exception as exc:
        logger.exception("Failed to extract text from PDF")
        raise HTTPException(status_code=422, detail="Failed to extract text from PDF.") from exc
    finally:
        document.close()

    if not text:
        logger.warning("PDF contained no extractable text")
        raise HTTPException(status_code=422, detail="PDF contained no extractable text.")

    return text


def mock_parse_resume() -> ResumeParsedData:
    logger.info("ANTHROPIC_API_KEY is missing; returning mock parsed resume")
    return MOCK_PARSED_RESUME.model_copy(deep=True)


def parse_resume_with_claude(raw_text: str, api_key: str) -> ResumeParsedData:
    client = Anthropic(api_key=api_key)
    logger.info("Calling Claude model %s to parse resume", CLAUDE_MODEL)

    try:
        response = client.messages.create(
            model=CLAUDE_MODEL,
            max_tokens=4096,
            tools=[PARSE_RESUME_TOOL_SCHEMA],
            tool_choice={"type": "tool", "name": PARSE_RESUME_TOOL},
            messages=[
                {
                    "role": "user",
                    "content": (
                        "Parse the following resume text into structured candidate data. "
                        "Call the parse_resume tool with the extracted fields.\n\n"
                        f"{raw_text}"
                    ),
                }
            ],
        )
    except APIStatusError as exc:
        logger.exception("Anthropic API returned an error status: %s", exc.status_code)
        raise HTTPException(
            status_code=502,
            detail="Resume parsing provider returned an error.",
        ) from exc
    except APIError as exc:
        logger.exception("Anthropic API request failed")
        raise HTTPException(
            status_code=502,
            detail="Failed to reach resume parsing provider.",
        ) from exc
    except Exception as exc:
        logger.exception("Unexpected error while calling Claude")
        raise HTTPException(status_code=500, detail="Unexpected error while parsing resume.") from exc

    tool_input: dict[str, Any] | None = None
    for block in response.content:
        if getattr(block, "type", None) == "tool_use" and getattr(block, "name", None) == PARSE_RESUME_TOOL:
            tool_input = dict(block.input)
            break

    if tool_input is None:
        logger.error("Claude response did not include parse_resume tool use: %s", response.content)
        raise HTTPException(status_code=502, detail="Parser did not return structured resume data.")

    try:
        parsed = ResumeParsedData.model_validate(tool_input)
    except ValidationError as exc:
        logger.error("Claude tool payload failed validation: %s", exc)
        raise HTTPException(
            status_code=502,
            detail="Parser returned data that did not match the expected schema.",
        ) from exc

    logger.info("Successfully parsed resume for candidate_name=%s", parsed.candidate_name)
    return parsed


def is_pdf_upload(file: UploadFile) -> bool:
    filename = (file.filename or "").lower()
    content_type = (file.content_type or "").lower()
    return filename.endswith(".pdf") or content_type in {"application/pdf", "application/x-pdf"}


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/parse-resume", response_model=ParseResumeResponse)
async def parse_resume(file: UploadFile = File(...)) -> ParseResumeResponse:
    if not is_pdf_upload(file):
        raise HTTPException(status_code=400, detail="Only PDF files are supported.")

    try:
        pdf_bytes = await file.read()
    except Exception as exc:
        logger.exception("Failed to read uploaded file")
        raise HTTPException(status_code=400, detail="Failed to read uploaded file.") from exc
    finally:
        await file.close()

    if not pdf_bytes:
        raise HTTPException(status_code=400, detail="Uploaded file is empty.")
    if len(pdf_bytes) > MAX_PDF_BYTES:
        raise HTTPException(status_code=413, detail="PDF exceeds the 10MB size limit.")

    logger.info(
        "Received resume upload filename=%s size=%s bytes",
        file.filename,
        len(pdf_bytes),
    )

    raw_text = extract_text_from_pdf(pdf_bytes)
    logger.info("Extracted %s characters of resume text", len(raw_text))

    api_key = get_anthropic_api_key()
    if api_key is None:
        parsed_data = mock_parse_resume()
    else:
        parsed_data = parse_resume_with_claude(raw_text, api_key)

    return ParseResumeResponse(raw_text=raw_text, parsed_data=parsed_data)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
