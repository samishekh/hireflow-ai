# HireFlow AI

A recruiter creates a job, uploads a pool of CV PDFs, and gets a ranked shortlist with reasons. HireFlow parses each CV, scores it against the job with a fixed rubric, and stores the result.

The clickable demo is a small web UI. The services behind it are a Java API plus two Python services.

## Status

The resume parser and job matcher are built. The core API, web UI, and deployment are still to come.

| Piece | Role | Status |
| --- | --- | --- |
| `resume-parser-service` | Python FastAPI. Turns a PDF into structured candidate data. | Built |
| `job-matcher-service` | Python FastAPI. Scores a parsed CV against a job, then compares the top candidates. | Built |
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
CLAUDE_MODEL=claude-sonnet-5-5
```

`CLAUDE_MODEL` is optional. When it is unset, the parser uses `claude-sonnet-5-5`.

```bash
curl -s http://localhost:8000/health

curl -s -F "file=@/path/to/resume.pdf" http://localhost:8000/parse-resume
```

Only PDF uploads are accepted, and the file must be under 10MB.

## Job matcher

The matcher scores one parsed candidate against a job, then ranks up to five already-scored candidates against each other. The score request is blind: skills, work history, education, and years of experience only. Claude returns skills, experience, and education sub-scores plus evidence. The service computes `overall_score` as `0.50 * skills + 0.35 * experience + 0.15 * education`, rounded to one decimal, and labels that formula `v1`.

If `ANTHROPIC_API_KEY` is missing, `/score` returns fixed sub-scores of 80, 60, and 40 (`overall_score` 67.0) and `/compare` keeps the input order.

From `job-matcher-service`:

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```

The API listens on port 8001. Interactive docs are at [http://localhost:8001/docs](http://localhost:8001/docs).

Put the key in `job-matcher-service/.env` if you want a real score. That file is gitignored. `CLAUDE_MODEL` is optional and defaults to `claude-sonnet-5-5`.

```bash
curl -s http://localhost:8001/health

curl -s http://localhost:8001/score \
  -H 'content-type: application/json' \
  -d '{"candidate_id":"cand-1","job_description":"Backend engineer","candidate":{"skills":["Python"],"years_of_experience":5,"work_history":[],"education":[]}}'

curl -s http://localhost:8001/compare \
  -H 'content-type: application/json' \
  -d '{"job_description":"Backend engineer","candidates":[{"candidate_id":"cand-1","skills":["Python"],"years_of_experience":5,"work_history":[],"education":[],"skills_score":80,"experience_score":60,"education_score":40,"summary":"API work"},{"candidate_id":"cand-2","skills":["Go"],"years_of_experience":3,"work_history":[],"education":[],"skills_score":70,"experience_score":55,"education_score":40,"summary":"Services work"}]}'
```

`POST /compare` accepts 2 to 5 candidates. Rank 1 is the best fit.
