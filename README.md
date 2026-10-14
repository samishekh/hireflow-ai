# HireFlow AI

A recruiter creates a job, uploads a pool of CV PDFs, and gets a ranked shortlist with reasons. HireFlow parses each CV, scores it against the job with a fixed rubric, and stores the result.

The clickable demo is a small web UI. The services behind it are a Java API plus two Python services.

## Status

Only the resume parser is built. The matcher, core API, web UI, and deployment are still to come.

| Piece | Role | Status |
| --- | --- | --- |
| `resume-parser-service` | Python FastAPI. Turns a PDF into structured candidate data. | Built |
| `job-matcher-service` | Python FastAPI. Scores a parsed CV against a job, then compares the top candidates. | Not started |
| `core-api` | Java Spring Boot. Jobs, uploads, orchestration, PostgreSQL. | Not started |
| `web-ui` | React + Vite. Create a job, upload CVs, watch status, read rankings, record an override. | Not started |

## Resume parser

The parser reads a PDF, extracts the text, and asks Claude to return structured fields: name, contact, skills, years of experience, work history, and education. If `ANTHROPIC_API_KEY` is missing, it returns a fixed mock resume so the service still runs.

From `resume-parser-service`:

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```

The API listens on port 8000. Interactive docs are at [http://localhost:8000/docs](http://localhost:8000/docs).

Put the key in `resume-parser-service/.env` if you want a real parse. That file is gitignored.

```bash
ANTHROPIC_API_KEY=your-key-here
```

```bash
curl -s http://localhost:8000/health

curl -s -F "file=@/path/to/resume.pdf" http://localhost:8000/parse-resume
```

Only PDF uploads are accepted, and the file must be under 10MB.
