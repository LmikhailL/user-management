#!/usr/bin/env bash
# Stop hook: after a turn that changed files, format + build the project, and on success
# open a PR with the changes. No-ops if the working tree is clean or GITHUB_PAT is unavailable.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

REPO_SLUG="LmikhailL/user-management"
BASE_BRANCH="main"

if [ -z "$(git status --porcelain)" ]; then
  exit 0
fi

if [ ! -f .env ]; then
  echo "stop-build-and-pr: .env not found, skipping build+PR automation." >&2
  exit 0
fi
set -a
# shellcheck disable=SC1091
source .env
set +a

if [ -z "${GITHUB_PAT:-}" ]; then
  echo "stop-build-and-pr: GITHUB_PAT not set in .env, skipping build+PR automation." >&2
  exit 0
fi

./mvnw spotlessApply
./mvnw clean install

BRANCH="auto/$(date +%Y%m%d%H%M%S)"
git checkout -b "$BRANCH"
git add -A
git commit -m "Automated: spotlessApply + mvn clean install"
git push "https://${GITHUB_PAT}@github.com/${REPO_SLUG}.git" "${BRANCH}:${BRANCH}"

PR_BODY=$(printf 'Automated PR after a successful `./mvnw spotlessApply` + `./mvnw clean install`.')
JSON_PAYLOAD=$(printf '{"title":"Automated update %s","head":"%s","base":"%s","body":"%s"}' \
  "$BRANCH" "$BRANCH" "$BASE_BRANCH" "$PR_BODY")

curl -sf -X POST \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${REPO_SLUG}/pulls" \
  -d "$JSON_PAYLOAD" > /dev/null

echo "stop-build-and-pr: opened PR from ${BRANCH} into ${BASE_BRANCH}."
