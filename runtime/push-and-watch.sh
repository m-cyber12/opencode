#!/usr/bin/env bash
# One-command Phase A launcher: push → watch CI → print G1..G8 gate table.
# Usage:
#   export GH_TOKEN=github_pat_xxx        # Contents+Actions RW on target repo
#   export REPO=owner/opencode-android    # created if missing
#   runtime/push-and-watch.sh
set -euo pipefail

REPO="${REPO:?set REPO=owner/name}"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

echo "== ensuring remote"
if ! git remote get-url origin >/dev/null 2>&1; then
  gh repo create "$REPO" --private --source . --remote origin --push >/dev/null
else
  git push -u origin "$BRANCH"
fi

echo "== waiting for workflow run (branch=$BRANCH)"
sleep 5
RUN_ID="$(gh run list --repo "$REPO" --workflow runtime-gates.yml --branch "$BRANCH" --limit 1 --json databaseId -q '.[0].databaseId')"
echo "run id: $RUN_ID  url: https://github.com/$REPO/actions/runs/$RUN_ID"

gh run watch "$RUN_ID" --repo "$REPO" --exit-status || true

echo "== fetching logs"
mkdir -p out/ci-logs
gh run view "$RUN_ID" --repo "$REPO" --log > out/ci-logs/full.log 2>/dev/null || \
  gh api "repos/$REPO/actions/runs/$RUN_ID/logs" > out/ci-logs/logs.zip

echo; echo "== GATE RESULTS =="
grep -hE 'GATE G[0-9]+ name=' out/ci-logs/full.log | sed 's/^[^\t]*\t//' | sort -u || true
