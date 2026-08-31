#!/usr/bin/env bash
# PostToolUse hook (matcher: Bash, if: "Bash(git commit *)"): runs immediately after a `git
# commit` on a branch named for the story's ticket (e.g. `US-001`), never on `main`. Verifies the
# build (spotless:apply + mvn clean install), pushes that same ticket branch, opens a PR into
# `main` the first time with a compact, headless-Claude-generated description (later pushes to the
# same PR keep that description as-is), then runs an automated code review of the PR's whole diff
# via a separate headless `claude -p` call (no tool access) and posts it as a PR comment. Never
# creates its own commit or its own throwaway branch — this hook only reacts to a commit already
# made by the calling git-commit command.
# No-ops if there's nothing new to ship, if the current
# branch is the base branch itself, if the working tree has uncommitted changes, or if GITHUB_PAT
# is unavailable.
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

EXISTING_PR_JSON="$(curl -sf \
  -H "Authorization: Bearer ${GITHUB_PAT}" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/${REPO_SLUG}/pulls?head=${REPO_OWNER}:${CURRENT_BRANCH}&state=open")"
PR_NUMBER="$(echo "$EXISTING_PR_JSON" | jq -r '.[0].number // empty')"
EXISTING_PR_URL="$(echo "$EXISTING_PR_JSON" | jq -r '.[0].html_url // empty')"

if [ -n "$EXISTING_PR_URL" ]; then
  echo "stop-build-and-pr: pushed ${CURRENT_BRANCH}; existing PR already open at ${EXISTING_PR_URL}."
else
  # The *first* commit unique to this branch (not the latest) — a ticket branch accumulates
  # follow-up commits over time (fixes, tooling), and the latest one drifts away from what the PR
  # is actually about. The first commit is normally the story-commit skill's own feature commit.
  PR_TITLE="$(git log "origin/${BASE_BRANCH}..HEAD" --reverse --pretty=%s | head -1)"

  # Compact description, generated once at PR-creation time only (not regenerated on later
  # pushes to the same PR): commit subjects + a diff --stat summary is enough context for a
  # skimmable body without paying for the full diff the way the review step does.
  PR_COMMITS="$(git log "origin/${BASE_BRANCH}..HEAD" --reverse --pretty=format:'- %s')"
  PR_DIFFSTAT="$(git diff --stat "origin/${BASE_BRANCH}...HEAD" | tail -1)"
  # Quoted heredoc ('EOF', not EOF) for the static instructions: no $()/backtick expansion inside
  # it. The dynamic parts (commit subjects, diff stat) are concatenated in afterward via printf
  # rather than interpolated inside the heredoc — commit messages and diff content can contain
  # arbitrary shell metacharacters, and an unquoted heredoc would try to expand them.
  # IMPORTANT: keep this heredoc's own text free of apostrophes/contractions ("do not", not
  # "don't") — bash's $(...) scanner does a naive quote-balance pass that gets confused by a lone
  # single quote inside a heredoc body even with a quoted delimiter, breaking the whole script.
  PR_BODY_PROMPT_STATIC="$(cat <<'EOF'
Write a compact GitHub pull request description in GitHub-flavored markdown for the PR whose
commits are listed below. Structure: a "## Summary" section with 1-2 sentences on what the PR
does and why, then a "## Changes" section as a short bulleted list of the key changes (group
related commits together, do not just restate every commit message verbatim). Only add a
"## Testing" section if the commits clearly indicate tests were added — one line, not a list.
Keep the whole thing compact and skimmable: well under 150 words total, no filler, no restating
the diff stat. Do not use any tools. Respond with the markdown only, nothing else.

Commits (oldest first):
EOF
)"
  PR_BODY_PROMPT="$(printf '%s\n%s\n\nDiff stat: %s' "$PR_BODY_PROMPT_STATIC" "$PR_COMMITS" "$PR_DIFFSTAT")"
  PR_BODY="$(printf '%s' "$PR_BODY_PROMPT" | claude -p --model sonnet --tools "" --output-format text || true)"
  if [ -z "$PR_BODY" ]; then
    # Headless review call failed or returned nothing — fall back to the plain commit list
    # rather than opening a PR with an empty body.
    PR_BODY="$PR_COMMITS"
  fi

  JSON_PAYLOAD="$(jq -n --arg title "$PR_TITLE" --arg head "$CURRENT_BRANCH" --arg base "$BASE_BRANCH" --arg body "$PR_BODY" \
    '{title: $title, head: $head, base: $base, body: $body}')"

  PR_RESPONSE="$(curl -sf -X POST \
    -H "Authorization: Bearer ${GITHUB_PAT}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${REPO_SLUG}/pulls" \
    -d "$JSON_PAYLOAD")"
  PR_NUMBER="$(echo "$PR_RESPONSE" | jq -r '.number')"

  echo "stop-build-and-pr: opened PR from ${CURRENT_BRANCH} into ${BASE_BRANCH} (\"${PR_TITLE}\")."
fi

# Automated code review: feed the PR's whole diff (three-dot, same as GitHub's own PR diff view)
# directly into a headless Claude Code invocation as plain text — --tools "" disables all tool
# access, so there is nothing for it to request permission for, and no risk of it acting on the
# repo. Runs on every push that reaches this point (not just when the PR is first opened), so the
# review reflects whatever is on the branch right now; each run posts a new comment rather than
# editing a previous one.
REVIEW_DIFF="$(git diff "origin/${BASE_BRANCH}...HEAD")"
if [ -n "$REVIEW_DIFF" ]; then
  # Same reasoning as PR_BODY_PROMPT above, doubly so here: this is arbitrary diff content, not
  # just commit subjects — guaranteed to contain shell metacharacters ($, backticks, quotes) that
  # an unquoted heredoc would try to expand. Quoted heredoc for the static instructions, diff
  # concatenated in afterward as an inert value. Keep this heredoc's own text apostrophe-free too
  # (see the PR_BODY_PROMPT_STATIC comment above for why).
  REVIEW_PROMPT_STATIC="$(cat <<'EOF'
You are reviewing a git diff for a pull request. Report only real, concrete correctness bugs
and clear simplification/efficiency issues you are confident about — do not pad the review with
style nitpicks or speculative concerns. For each finding, name the file and describe the concrete
problem and its consequence in 1-2 sentences. If you find nothing worth flagging, say so plainly
in one sentence. Keep the whole review under 400 words, formatted as GitHub-flavored markdown
suitable for a PR comment. Do not use any tools — just read the diff below and respond with the
review text only, nothing else.

--- DIFF ---
EOF
)"
  REVIEW_PROMPT="$(printf '%s\n%s\n--- END DIFF ---' "$REVIEW_PROMPT_STATIC" "$REVIEW_DIFF")"

  REVIEW_TEXT="$(printf '%s' "$REVIEW_PROMPT" | claude -p --model sonnet --tools "" --output-format text || true)"

  if [ -n "$REVIEW_TEXT" ]; then
    COMMENT_PAYLOAD="$(jq -n --arg body "$REVIEW_TEXT" '{body: ("### 🤖 Automated code review\n\n" + $body)}')"
    curl -sf -X POST \
      -H "Authorization: Bearer ${GITHUB_PAT}" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/${REPO_SLUG}/issues/${PR_NUMBER}/comments" \
      -d "$COMMENT_PAYLOAD" > /dev/null
    echo "stop-build-and-pr: posted an automated code review comment on PR #${PR_NUMBER}."
  else
    echo "stop-build-and-pr: code review produced no output, skipping comment." >&2
  fi
fi
