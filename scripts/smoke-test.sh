#!/usr/bin/env bash
# Creates a job, uploads two fixture PDFs, checks the mock score, and records an override.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${BASE_URL:-http://localhost:8080}"
ONE="$ROOT/scripts/fixtures/one.pdf"
TWO="$ROOT/scripts/fixtures/two.pdf"

if ! command -v curl >/dev/null || ! command -v python3 >/dev/null; then
  echo "curl and python3 are required" >&2
  exit 1
fi

for pdf in "$ONE" "$TWO"; do
  if [[ ! -f "$pdf" ]]; then
    echo "Missing fixture: $pdf" >&2
    exit 1
  fi
done

echo "Waiting for $BASE/actuator/health"
ready=0
for _ in $(seq 1 60); do
  if curl -fsS "$BASE/actuator/health" >/dev/null 2>&1; then
    ready=1
    break
  fi
  sleep 2
done
if [[ "$ready" -ne 1 ]]; then
  echo "core-api did not become healthy at $BASE" >&2
  exit 1
fi

echo "Resetting scoring formula to v1"
curl -fsS -X PUT "$BASE/api/formula" \
  -H 'content-type: application/json' \
  -d '{"skills_weight":50,"experience_weight":35,"education_weight":15}' >/dev/null

job_json="$(curl -fsS -X POST "$BASE/api/jobs" \
  -H 'content-type: application/json' \
  -d '{"title":"Backend engineer","description":"Python APIs"}')"
job_id="$(printf '%s' "$job_json" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')"
echo "Created job $job_id"

upload_json="$(curl -fsS -X POST "$BASE/api/jobs/$job_id/candidates" \
  -F "files=@${ONE};filename=one.pdf" \
  -F "files=@${TWO};filename=two.pdf")"
echo "Uploaded $(printf '%s' "$upload_json" | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["candidate_ids"]))') candidates"

rankings_file="$(mktemp)"
trap 'rm -f "$rankings_file"' EXIT

finished=0
for _ in $(seq 1 60); do
  curl -fsS "$BASE/api/jobs/$job_id/rankings" >"$rankings_file"
  if python3 - "$rankings_file" <<'PY'
import json
import sys

rows = json.load(open(sys.argv[1]))
if len(rows) != 2:
    sys.exit(1)
if any(row["status"] in ("pending", "parsing", "scoring") for row in rows):
    sys.exit(1)
sys.exit(0)
PY
  then
    finished=1
    break
  fi
  sleep 1
done

if [[ "$finished" -ne 1 ]]; then
  echo "Rankings did not finish:" >&2
  cat "$rankings_file" >&2
  exit 1
fi

python3 - "$rankings_file" <<'PY'
import json
import sys

rows = json.load(open(sys.argv[1]))
errors = []
if len(rows) != 2:
    errors.append(f"expected 2 candidates, got {len(rows)}")

for row in rows:
    name = row.get("original_filename")
    status = row.get("status")
    score = row.get("score") or {}
    print(
        f"{name}  status={status}  overall={score.get('overall_score')}  "
        f"model={score.get('model')}"
    )
    if status != "done":
        errors.append(f"{name} status is {status}: {row.get('error_message')}")
        continue
    if score.get("overall_score") != 67.0:
        errors.append(f"{name} overall_score is {score.get('overall_score')}")
    if score.get("skills_score") != 80:
        errors.append(f"{name} skills_score is {score.get('skills_score')}")
    if score.get("experience_score") != 60:
        errors.append(f"{name} experience_score is {score.get('experience_score')}")
    if score.get("education_score") != 40:
        errors.append(f"{name} education_score is {score.get('education_score')}")
    if score.get("formula_version") != "v1":
        errors.append(f"{name} formula_version is {score.get('formula_version')}")
    if score.get("model") != "mock":
        errors.append(f"{name} model is {score.get('model')}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
PY

curl -fsS "$BASE/api/jobs/$job_id/rankings" >"$rankings_file"

candidate_id="$(python3 - "$rankings_file" <<'PY'
import json
import sys

print(json.load(open(sys.argv[1]))[0]["id"])
PY
)"
echo "Recording override for $candidate_id"

override_file="$(mktemp)"
trap 'rm -f "$rankings_file" "$override_file"' EXIT

http_code="$(curl -sS -o "$override_file" -w '%{http_code}' \
  -X POST "$BASE/api/candidates/$candidate_id/override" \
  -H 'content-type: application/json' \
  -d '{"decision":"accepted","note":"Matches the role","decided_by":"recruiter"}')"

python3 - "$override_file" "$http_code" "$candidate_id" <<'PY'
import json
import sys

body_path, code, candidate_id = sys.argv[1:]
raw = open(body_path).read()
if code != "201":
    print(f"override status is {code}: {raw}", file=sys.stderr)
    sys.exit(1)
body = json.loads(raw)
errors = []
if body.get("candidate_id") != candidate_id:
    errors.append(f"override candidate_id is {body.get('candidate_id')}")
if body.get("decision") != "accepted":
    errors.append(f"override decision is {body.get('decision')}")
if body.get("note") != "Matches the role":
    errors.append(f"override note is {body.get('note')}")
if body.get("decided_by") != "recruiter":
    errors.append(f"override decided_by is {body.get('decided_by')}")
if not body.get("id"):
    errors.append("override id is missing")
if not body.get("created_at"):
    errors.append("override created_at is missing")
if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
PY

curl -fsS "$BASE/api/jobs/$job_id/rankings" >"$rankings_file"

python3 - "$rankings_file" "$candidate_id" <<'PY'
import json
import sys

rows = json.load(open(sys.argv[1]))
candidate_id = sys.argv[2]
errors = []
target = None

if len(rows) != 2:
    errors.append(f"expected 2 candidates, got {len(rows)}")

for row in rows:
    name = row.get("original_filename")
    score = row.get("score") or {}
    override = row.get("override")
    if row.get("id") == candidate_id:
        target = row
    elif override is not None:
        errors.append(f"{name} has an unexpected override")
    if score.get("overall_score") != 67.0:
        errors.append(f"{name} overall_score is {score.get('overall_score')}")

if target is None:
    errors.append(f"rankings have no candidate {candidate_id}")
else:
    override = target.get("override") or {}
    name = target.get("original_filename")
    print(
        f"{name}  override={override.get('decision')}  "
        f"overall={((target.get('score') or {}).get('overall_score'))}"
    )
    if override.get("decision") != "accepted":
        errors.append(f"{name} override decision is {override.get('decision')}")
    if override.get("note") != "Matches the role":
        errors.append(f"{name} override note is {override.get('note')}")
    if override.get("decided_by") != "recruiter":
        errors.append(f"{name} override decided_by is {override.get('decided_by')}")

if errors:
    print("\n".join(errors), file=sys.stderr)
    sys.exit(1)
print("Smoke test passed.")
PY
