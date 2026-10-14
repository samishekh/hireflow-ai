"""The checked-in parser schemas stay aligned with the Pydantic models."""

import json
from pathlib import Path

import pytest
from pydantic import ValidationError

import main

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "parser"


def _load(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text())


def test_resume_parsed_data_schema_matches_contract():
    assert _load("resume-parsed-data.schema.json") == main.json_schema_for(main.ResumeParsedData)


def test_parse_resume_response_schema_matches_contract():
    assert _load("parse-resume-response.schema.json") == main.json_schema_for(main.ParseResumeResponse)


def test_tool_schema_matches_parsed_data_contract():
    assert main.PARSE_RESUME_TOOL_SCHEMA["input_schema"] == _load("resume-parsed-data.schema.json")


def test_response_schema_embeds_parsed_data_schema():
    response = _load("parse-resume-response.schema.json")
    parsed = _load("resume-parsed-data.schema.json")
    embedded = dict(response["properties"]["parsed_data"])
    assert embedded.pop("description") == "Structured fields extracted from the resume"
    assert embedded == parsed


def test_models_reject_unknown_fields():
    with pytest.raises(ValidationError):
        main.ResumeParsedData.model_validate(
            {
                "candidate_name": "Ada Lovelace",
                "email": "",
                "phone": "",
                "skills": [],
                "years_of_experience": 1,
                "work_history": [],
                "education": [],
                "nickname": "Ada",
            }
        )
