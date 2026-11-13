#!/bin/sh
# One-time setup after cloning: enable the pre-push hook, then report what is missing.
set -e
cd "$(dirname "$0")/.."
chmod +x .githooks/* scripts/*.sh gradlew
git config core.hooksPath .githooks
echo "git hooks enabled (.githooks/pre-push runs ./gradlew check)"
exec scripts/check-env.sh
