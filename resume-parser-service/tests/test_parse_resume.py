"""Parse path with a stubbed Claude client. No API key or PDF file required."""

import pytest
from anthropic import APIError, APIStatusError
from fastapi.testclient import TestClient
import httpx2

import main

RAW_TEXT = "Ada Lovelace resume text"

VALID_PARSED = {
    "candidate_name": "Ada Lovelace",
    "email": "ada@example.com",
    "phone": "",
    "skills": ["Mathematics"],
    "years_of_experience": 2.0,
    "work_history": [
        {
            "company": "Analytical Engines",
            "role": "Mathematician",
            "duration": "1842 - 1852",
            "description": "Wrote notes on the analytical engine.",
        }
    ],
    "education": [
        {
            "institution": "Home",
            "degree": "Tutored",
            "field_of_study": "Mathematics",
            "duration": "1816 - 1832",
        }
    ],
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

    def __init__(self, api_key: str):
        type(self).constructed += 1
        self.api_key = api_key
        self.messages = self
        self.calls: list[dict] = []

    def parse(self, **kwargs):
        self.calls.append(kwargs)
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
    monkeypatch.setattr(main, "extract_text_from_pdf", lambda pdf_bytes: RAW_TEXT)
    monkeypatch.setattr(main, "Anthropic", _Anthropic)
    _Anthropic.constructed = 0
    _Anthropic.outcome = _parsed_response(VALID_PARSED)
    return TestClient(main.app)


def _upload(client: TestClient):
    return client.post(
        "/parse-resume",
        files={"file": ("resume.pdf", b"%PDF-1.4 not a real document", "application/pdf")},
    )


def test_structured_payload_becomes_parsed_data(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")

    response = _upload(client)

    assert response.status_code == 200
    body = response.json()
    assert body["raw_text"] == RAW_TEXT
    assert body["parsed_data"] == VALID_PARSED
    assert _Anthropic.constructed == 1


def test_missing_structured_output_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = _Response([_Block("text")])

    response = _upload(client)

    assert response.status_code == 502
    assert response.json()["detail"] == "Parser did not return structured resume data."


def test_invalid_payload_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = _parsed_response({**VALID_PARSED, "nickname": "Ada"})

    response = _upload(client)

    assert response.status_code == 502
    assert response.json()["detail"] == "Parser returned data that did not match the expected schema."


def test_api_status_error_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = APIStatusError(
        "overloaded",
        response=httpx2.Response(529, request=_request()),
        body=None,
    )

    response = _upload(client)

    assert response.status_code == 502
    assert response.json()["detail"] == "Resume parsing provider returned an error."


def test_api_error_returns_502(client: TestClient, monkeypatch: pytest.MonkeyPatch):
    monkeypatch.setenv("ANTHROPIC_API_KEY", "test-key")
    _Anthropic.outcome = APIError("connection failed", _request(), body=None)

    response = _upload(client)

    assert response.status_code == 502
    assert response.json()["detail"] == "Failed to reach resume parsing provider."


def test_non_pdf_extension_is_rejected(client: TestClient):
    response = client.post(
        "/parse-resume",
        files={"file": ("resume.txt", b"%PDF-1.4 not a real document", "application/pdf")},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "Only PDF files are supported."


def test_pdf_extension_without_header_is_rejected(client: TestClient):
    response = client.post(
        "/parse-resume",
        files={"file": ("resume.pdf", b"not a pdf", "application/pdf")},
    )

    assert response.status_code == 400
    assert response.json()["detail"] == "Only PDF files are supported."


def test_missing_api_key_returns_mock_and_does_not_call_claude(
    client: TestClient, monkeypatch: pytest.MonkeyPatch
):
    monkeypatch.delenv("ANTHROPIC_API_KEY", raising=False)

    response = _upload(client)

    assert response.status_code == 200
    body = response.json()
    assert body["raw_text"] == RAW_TEXT
    assert body["parsed_data"] == main.MOCK_PARSED_RESUME.model_dump()
    assert _Anthropic.constructed == 0
