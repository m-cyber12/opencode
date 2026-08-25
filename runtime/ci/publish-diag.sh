#!/bin/sh
# Publishes diagnostic files to the public `gate-report` branch so unauthenticated
# observers (and the developer without repo-side credentials) can read real CI evidence.
#
# Usage inside Actions:  publish-diag.sh <RUN_ID> <file> [<file>...]
set -eu

RUN_ID="$1"; shift
cd "$GITHUB_WORKSPACE"

git config user.name  "gate-bot"
git config user.email "actions@github.com"
git fetch origin gate-report >/dev/null 2>&1 && {
  git checkout -B gate-report FETCH_HEAD
} || git checkout -B gate-report

mkdir -p logs/run-"$RUN_ID"
i=0
for f in "$@"; do
  [ -f "$f" ] || continue
  i=$((i+1))
  base=$(basename "$f")
  case "$base" in
    *.xml|*.html|*.txt|*.log) name="$base" ;;
    *) name="$base.log" ;;
  esac
  cp "$f" "logs/run-$RUN_ID/$name"
done

[ "$(ls -A logs/run-"$RUN_ID" 2>/dev/null)" ] || { echo "nothing to publish"; exit 0; }

git add logs
git commit -qm "diagnostics for run $RUN_ID" || exit 0
git push -q origin gate-report
echo "published diagnostics for run $RUN_ID"
