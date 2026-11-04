"""Pinned top 3 for the checked-in evaluation set. No API key and no Claude call."""

import json
from pathlib import Path

import pytest
from fastapi.testclient import TestClient

import main

ROOT = Path(__file__).resolve().parents[2] / "evaluation"
SET_PATH = ROOT / "set.json"
EXPECTED_PATH = ROOT / "expected-top3.json"


@pytest.fixture
def recorded_client(monkeypatch: pytest.MonkeyPatch) -> TestClient:
    monkeypatch.setenv("HIREFLOW_RECORDED_SCORES", str(SET_PATH))
    monkeypatch.setenv("ANTHROPIC_API_KEY", "present-but-must-not-be-used")

    def fail_if_called(*_args, **_kwargs):
        raise AssertionError("evaluation scoring called Claude")

    monkeypatch.setattr(main, "Anthropic", fail_if_called)
    return TestClient(main.app)


def test_evaluation_set_keeps_top_three(recorded_client: TestClient):
    evaluation = json.loads(SET_PATH.read_text(encoding="utf-8"))
    expected = json.loads(EXPECTED_PATH.read_text(encoding="utf-8"))
    rows = evaluation["candidates"]
    assert len(rows) == 10

    scored = []
    for row in rows:
        response = recorded_client.post(
            "/score",
            json={
                "candidate_id": row["candidate_id"],
                "job_description": evaluation["job_description"],
                "candidate": row["profile"],
            },
        )
        assert response.status_code == 200, response.text
        body = response.json()
        assessment = row["assessment"]
        assert body["model"] == "recorded"
        assert body["formula_version"] == "v1"
        assert body["overall_score"] == main.weighted_overall_score(
            assessment["skills_score"],
            assessment["experience_score"],
            assessment["education_score"],
        )
        scored.append(body)

    scored.sort(key=lambda item: (-item["overall_score"], item["candidate_id"]))
    top3 = scored[:3]
    assert [item["candidate_id"] for item in top3] == expected["candidate_ids"]
    assert [item["overall_score"] for item in top3] == expected["overall_scores"]
    assert scored[3]["candidate_id"] == "samir-dalal"
    assert scored[3]["overall_score"] == 81.8


def test_recorded_score_missing_candidate_returns_404(recorded_client: TestClient):
    evaluation = json.loads(SET_PATH.read_text(encoding="utf-8"))
    profile = evaluation["candidates"][0]["profile"]
    response = recorded_client.post(
        "/score",
        json={
            "candidate_id": "not-in-the-set",
            "job_description": evaluation["job_description"],
            "candidate": profile,
        },
    )
    assert response.status_code == 404
