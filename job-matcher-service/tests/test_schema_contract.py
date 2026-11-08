"""The checked-in matcher schemas stay aligned with the Pydantic models."""

import json
from pathlib import Path

import main

CONTRACTS = Path(__file__).resolve().parents[2] / "contracts" / "matcher"


def _load(name: str) -> dict:
    return json.loads((CONTRACTS / name).read_text())


def test_score_request_schema_matches_contract():
    assert _load("score-request.schema.json") == main.json_schema_for(main.ScoreRequest)


def test_score_response_schema_matches_contract():
    assert _load("score-response.schema.json") == main.json_schema_for(main.ScoreResponse)


def test_score_tool_schema_matches_contract():
    assert main.SCORE_TOOL_SCHEMA["input_schema"] == _load("score-candidate-tool.schema.json")
