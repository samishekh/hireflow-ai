# HireFlow AI

A recruiter creates a job, uploads CV PDFs, and gets a ranked shortlist with reasons. Each CV is parsed, scored against the job with a fixed rubric, and stored.

## Services

| Piece | Role | Status |
| --- | --- | --- |
| `resume-parser-service` | Python FastAPI. PDF to structured candidate data. Port 8000. | Built |
| `job-matcher-service` | Python FastAPI. Scores a parsed CV against a job. Port 8001. | Built |
| `core-api` | Java Spring Boot. Jobs, uploads, orchestration, PostgreSQL. Port 8080. | Built |
| `web-ui` | React + Vite. Create a job, upload CVs, watch the pipeline, read rankings, edit the global formula, record an override. Port 5173. | Built |

## Run

From the repo root:

```bash
docker compose up --build
```

Compose starts Postgres, the parser, the matcher, `core-api`, and the web UI. Stop with `docker compose down`.

| | |
| --- | --- |
| Parser | [http://localhost:8000/docs](http://localhost:8000/docs) |
| Matcher | [http://localhost:8001/docs](http://localhost:8001/docs) |
| Core API | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) |
| Health | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| Web UI | [http://localhost:5173](http://localhost:5173) |

The UI is served by nginx in Compose and calls `core-api` on the same origin. A role is a full posting: overview, responsibilities, requirements, and nice-to-haves. The pipeline page polls rankings and slides each CV across Pending, Parsing, Scoring, and Done. Rankings keep the weighted score next to any recruiter decision.

To work on the UI without rebuilding the image, with `core-api` already on port 8080:

```bash
cd web-ui
npm install
npm run dev
```

The dev server proxies `/api` to `http://localhost:8080`.

### Claude or mock

Both Python services call Claude when `ANTHROPIC_API_KEY` is set. With no key they stay in mock mode: the parser returns a fixed resume, and `/score` returns sub-scores 80, 60, and 40 (`overall_score` 67.0).

Put the same file in `resume-parser-service/.env` and `job-matcher-service/.env`. Both are gitignored, and Compose treats a missing file as mock mode. `.dockerignore` keeps `.env` out of images. Recreate the containers after you change either file.

```
ANTHROPIC_API_KEY=your-key-here
CLAUDE_MODEL=claude-sonnet-5-5
```

`CLAUDE_MODEL` is optional and defaults to `claude-sonnet-5-5`. Leave the key empty to stay in mock mode.

### Smoke test

With the stack up and both services in mock mode:

```bash
./scripts/smoke-test.sh
```

The script sets the global formula back to v1, creates a job, uploads `scripts/fixtures/one.pdf` and `scripts/fixtures/two.pdf`, and expects both candidates to finish at `overall_score` 67.0 with model `mock`. It then records an accepted override and checks that the decision is stored while the score stays the same. A real API key fails those fixed scores.

## CI

GitHub Actions runs on every pull request and on every push to `master`. Pytest covers the parser and the matcher, including the contract tests against `contracts/`. `core-api` runs JUnit, Testcontainers, and WireMock. The web UI runs its tests and the TypeScript build.

`evaluation/` is the matcher's pinned shortlist: one job and ten blind profiles, with recorded sub-scores. The matcher tests set `HIREFLOW_RECORDED_SCORES` and score them through `POST /score` with formula `v1`. No API key is used. The job fails if the top 3 is not `ava-chen`, `noah-ibrahim`, and `lena-okoye` at 91.0, 86.8, and 82.5. `samir-dalal` is fourth at 81.8, close enough that a weight change can move the top 3.

A push to `master` publishes the four images to GHCR as `ghcr.io/<owner>/hireflow-ai/<service>:latest` and a tag for the commit. The workflow uses `GITHUB_TOKEN`. No Claude key is stored in Actions.

## AWS

Local runs stay on Compose. The same app ran on EKS in `us-east-1` from `hireflow-cluster.yaml` and the manifests in `k8s/`. That stack was deleted the same day. Nothing from it is still running.

The cluster is `hireflow`, Kubernetes 1.37, with the NAT gateway disabled and one managed node group, `ng-1`: a single public `t3.medium` and a 20 GB disk. The node's public IP is how image pulls and `api.anthropic.com` leave the VPC.

Postgres is private RDS, PostgreSQL 16, class `db.t4g.micro`, single AZ, 20 GB. The database name is `hireflow` and the user is `postgres`. Public access is off, and port 5432 is open only from the node security group. Deletion protection is off and backup retention is 0, so the instance can be deleted without a final snapshot. `k8s/configmap.yaml` keeps `SPRING_DATASOURCE_URL` as `jdbc:postgresql://REPLACE_ME.rds.amazonaws.com:5432/hireflow`. The host is replaced with the RDS endpoint before apply, and that endpoint is not committed. The parser and matcher stay on cluster DNS: `http://resume-parser-service:8000` and `http://job-matcher-service:8001`.

Images are `ghcr.io/samishekh/hireflow-ai/<service>:latest` with `imagePullPolicy: Always`. Each Deployment expects a docker-registry Secret named `ghcr` in the `hireflow` namespace. `k8s/secret.example.yaml` is the shape of the app Secret (`POSTGRES_PASSWORD`, `ANTHROPIC_API_KEY`, `CLAUDE_MODEL`). It is created by hand and is not committed. This run set `CLAUDE_MODEL` to `claude-haiku-5-5`. An empty `ANTHROPIC_API_KEY` keeps both Python services in mock mode. An empty `CLAUDE_MODEL` leaves each service on `claude-sonnet-5-5`.

The AWS Load Balancer Controller, installed with IRSA, serves `k8s/ingress-http.yaml`. The load balancer is internet-facing, target type `ip`, HTTP on port 80 only, with health check `/` and an idle timeout of 180 seconds so a parse or score request is not cut off. There is no hostname and no certificate. The site is `http://<alb-dns-name>/`. The UI nginx proxies `/api` and `/actuator` to `core-api`.

The manifests are applied directly: the namespace, the ConfigMap, one Deployment and ClusterIP Service per app, then the ingress. Probes match the Compose healthchecks. `web-ui` waits until `core-api` health reports UP.

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/resume-parser-service.yaml
kubectl apply -f k8s/job-matcher-service.yaml
kubectl apply -f k8s/core-api.yaml
kubectl apply -f k8s/web-ui.yaml
kubectl apply -f k8s/ingress-http.yaml
```

Tear down in this order. Delete the ingress and wait until the load balancer is gone, delete the RDS instance with no final snapshot, then delete the cluster:

```bash
eksctl delete cluster --name hireflow --region us-east-1
```

## Pipeline

`core-api` runs each upload through parse and score. Name, email, and phone stay in PostgreSQL. The matcher receives the normalized job description and a blind profile only. Claude's sub-scores are stored as returned. `overall_score` is recomputed in `core-api` from the global formula, so the matcher's own overall is not stored. A second identical blind profile reuses the sub-scores and applies the current formula.

1. **Parse.** Extract PDF text and return name, contact, skills, years of experience, work history, and education. PDF only, under 10MB. `POST /parse-resume`.
2. **Score.** Blind profile: skills, work history, education, and years of experience. Claude returns skills, experience, and education sub-scores plus evidence. `POST /score`. The stored overall is `skills_weight * skills + experience_weight * experience + education_weight * education`, with weights as percents, rounded half up to one decimal. The default is formula `v1`: 50, 35, and 15. Scoring ends at status `done`.
3. **Formula.** `GET /api/formula` and `PUT /api/formula` with `skills_weight`, `experience_weight`, and `education_weight`. The three weights are from 0 to 100, at most two decimal places, and must add up to 100. A change rewrites every stored overall and formula label. It does not call the matcher. New uploads use only the new weights. `50/35/15` stays labeled `v1`; any other mix is labeled like `10/10/80`.
4. **Override.** Accepted only after status `done`. It does not change the model score.
5. **Delete.** `DELETE /api/candidates/{id}` removes that one candidate, plus the stored score and every recruiter decision. `DELETE /api/jobs/{id}` removes the role and every candidate under it, including their scores and decisions. Both return 204. A missing id returns 404.

`POST /api/jobs/{id}/candidates` returns 202. Poll rankings until status is `done` or `failed`.

```bash
curl -s http://localhost:8080/api/jobs \
  -H 'content-type: application/json' \
  -d '{"title":"Backend engineer","description":"Python APIs"}'

curl -s -X POST http://localhost:8080/api/jobs/{jobId}/candidates \
  -F "files=@/path/to/one.pdf" \
  -F "files=@/path/to/two.pdf"

curl -s http://localhost:8080/api/jobs/{jobId}/rankings

curl -s -X PUT http://localhost:8080/api/formula \
  -H 'content-type: application/json' \
  -d '{"skills_weight":60,"experience_weight":25,"education_weight":15}'

curl -s -X POST http://localhost:8080/api/candidates/{candidateId}/override \
  -H 'content-type: application/json' \
  -d '{"decision":"accepted","note":"Matches the role","decided_by":"recruiter"}'

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE \
  http://localhost:8080/api/candidates/{candidateId}

curl -s -o /dev/null -w '%{http_code}\n' -X DELETE \
  http://localhost:8080/api/jobs/{jobId}
```

Request bodies for `/parse-resume` and `/score` are in the service docs linked above.

## Run without Compose

Java 21. Start Postgres, then each Python service, then the API. The API expects the parser on port 8000, the matcher on port 8001, and Postgres on port 5432 (`hireflow` / `hireflow`).

```bash
docker start hireflow-postgres || docker run --name hireflow-postgres \
  -e POSTGRES_PASSWORD=hireflow \
  -e POSTGRES_DB=hireflow \
  -p 5432:5432 -d postgres:16
```

In `resume-parser-service`, then again in `job-matcher-service`:

```bash
python -m venv venv
source venv/bin/activate
pip install -r requirements.txt
python main.py
```

```bash
cd core-api
./mvnw spring-boot:run
```
