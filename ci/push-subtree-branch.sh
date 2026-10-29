#!/bin/bash

set -o nounset
set -o errexit

REPO_URL=${1}
PACKAGE_DIRECTORY=${2}
BRANCH_NAME=${3}
SHA=$(git subtree split -P "$PACKAGE_DIRECTORY")
git push "$REPO_URL" "$SHA:refs/heads/$BRANCH_NAME" --force
