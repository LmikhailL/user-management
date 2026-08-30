#!/usr/bin/env bash
# Stop hook: runs AFTER a commit has already been made via the story-commit skill, which commits
# on a branch named for the story's ticket (e.g. `US-001`), never on `main`. Verifies the build
# (spotlessApply + mvn clean install), pushes that same ticket branch, and opens a PR into `main`
# the first time — later runs just push the branch update rather than opening a duplicate PR.
# Never creates its own commit or its own throwaway branch; both are the story-commit skill's
# job. No-ops if there's nothing new to ship, if the current branch is the base branch itself, if
# the working tree has uncommitted changes, or if GITHUB_PAT is unavailable.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$REPO_ROOT"

REPO_SLUG="LmikhailL/user-management"
REPO_OWNER="${REPO_SLUG%%/*}"
BASE_BRANCH="main"

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

if [ -n "$(git status --porcelain)" ]; then
  echo "stop-build-and-pr: working tree has uncommitted/untracked changes — run the story-commit skill to commit first, skipping." >&2
  exit 0
fi

CURRENT_BRANCH="$(git branch --show-current)"
if [ -z "$CURRENT_BRANCH" ] || [ "$CURRENT_BRANCH" = "$BASE_BRANCH" ]; then
  # Detached HEAD, or still on the base branch — story-commit always branches to <TICKET-ID>
  # before committing, so there's nothing of ours to ship from here.
  exit 0
fi

# Plain `git fetch origin` fails against this private repo (no credential helper configured for
# fetch, only push had the PAT embedded) — fetch through the same PAT-embedded URL and update the
# origin/<base> tracking ref explicitly via refspec, same as it would from a normal `origin` fetch.
git fetch "https://${GITHUB_PAT}@github.com/${REPO_SLUG}.git" \
  "+refs/heads/${BASE_BRANCH}:refs/remotes/origin/${BASE_BRANCH}" --quiet

AHEAD_COUNT="$(git rev-list "origin/${BASE_BRANCH}..HEAD" --count)"
if [ "$AHEAD_COUNT" -eq 0 ]; then
  exit 0
fi

./mvnw spotless:apply
if [ -n "$(git status --porcelain)" ]; then
  echo "stop-build-and-pr: spotless:apply found formatting that wasn't committed clean — fix it and re-commit via story-commit before this can open a PR." >&2
  git checkout -- .
  exit 1
fi

./mvnw clean install

git push "https://${GITHUB_PAT}@github.com/${REPO_SLUG}.git" "${CURRENT_BRANCH}:${CURRENT_BRANCH}"

EXISTING_PR_URL="$(curl -sf \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${REPO_SLUG}/pulls?head=${REPO_OWNER}:${CURRENT_BRANCH}&state=open" \
  | jq -r '.[0].html_url // empty')"

if [ -n "$EXISTING_PR_URL" ]; then
  echo "stop-build-and-pr: pushed ${CURRENT_BRANCH}; existing PR already open at ${EXISTING_PR_URL}."
  exit 0
fi

# The *first* commit unique to this branch (not the latest) — a ticket branch accumulates
# follow-up commits over time (fixes, tooling), and the latest one drifts away from what the PR
# is actually about. The first commit is normally the story-commit skill's own feature commit.
PR_TITLE="$(git log "origin/${BASE_BRANCH}..HEAD" --reverse --pretty=%s | head -1)"
PR_BODY="$(git log "origin/${BASE_BRANCH}..HEAD" --pretty=format:'- %s')"
JSON_PAYLOAD="$(jq -n --arg title "$PR_TITLE" --arg head "$CURRENT_BRANCH" --arg base "$BASE_BRANCH" --arg body "$PR_BODY" \
  '{title: $title, head: $head, base: $base, body: $body}')"

curl -sf -X POST \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${REPO_SLUG}/pulls" \
  -d "$JSON_PAYLOAD" > /dev/null

echo "stop-build-and-pr: opened PR from ${CURRENT_BRANCH} into ${BASE_BRANCH} (\"${PR_TITLE}\")."
