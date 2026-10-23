# HireFlow AI

A recruiter creates a job, uploads a pool of CV PDFs, and gets a ranked shortlist with reasons. HireFlow parses each CV, scores it against the job with a fixed rubric, and stores the result.

The clickable demo is a small web UI. The services behind it are a Java API plus two Python services.

## Status

The resume parser, job matcher, and core API are built. The web UI and deployment are still to come.

| Piece | Role | Status |
| --- | --- | --- |
| `resume-parser-service` | Python FastAPI. Turns a PDF into structured candidate data. | Built |
| `job-matcher-service` | Python FastAPI. Scores a parsed CV against a job, then compares the top candidates. | Built |
| `core-api` | Java Spring Boot. Jobs, uploads, orchestration, PostgreSQL. | Built |
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

## Core API

`core-api` stores jobs and candidates in PostgreSQL and runs each upload through parse, score, and compare. It calls the parser on port 8000 and the matcher on port 8001. Name, email, and phone stay in the database. The matcher receives the normalized job description and the blind profile only. `overall_score` and `formula_version` are stored as the matcher returns them.

Java 21 is required. Start Postgres, then the two Python services, then the API:

```bash
docker start hireflow-postgres || docker run --name hireflow-postgres \
  -e POSTGRES_PASSWORD=hireflow \
  -e POSTGRES_DB=hireflow \
  -p 5432:5432 -d postgres:16
```

```bash
cd resume-parser-service
source venv/bin/activate
python main.py
```

```bash
cd job-matcher-service
source venv/bin/activate
python main.py
```

```bash
cd core-api
./mvnw spring-boot:run
```

The API listens on port 8080. Interactive docs are at [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html). Health is at [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health).

With both Python services in mock mode (no `ANTHROPIC_API_KEY`), a two-PDF upload finishes with `overall_score` 67.0 on each candidate. The second identical blind profile reuses the stored score. Rank 1 is the best fit.

```bash
curl -s http://localhost:8080/api/jobs \
  -H 'content-type: application/json' \
  -d '{"title":"Backend engineer","description":"Python APIs"}'

curl -s -X POST http://localhost:8080/api/jobs/{jobId}/candidates \
  -F "files=@/path/to/one.pdf" \
  -F "files=@/path/to/two.pdf"

curl -s http://localhost:8080/api/jobs/{jobId}/rankings

curl -s -X POST http://localhost:8080/api/candidates/{candidateId}/override \
  -H 'content-type: application/json' \
  -d '{"decision":"accepted","note":"Matches the role","decided_by":"recruiter"}'
```

`POST /api/jobs/{id}/candidates` returns 202. Poll rankings until status is `done` or `failed`. An override is accepted only after `done`, and it does not change the model score.
