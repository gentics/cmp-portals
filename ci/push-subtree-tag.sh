#!/bin/bash

set -o nounset
set -o errexit

REPO_URL=${1}
PACKAGE_DIRECTORY=${2}
TAG_NAME=${4}
SHA=$(git subtree split -P "$PACKAGE_DIRECTORY")
git push "$REPO_URL" "$SHA:refs/tags/$TAG_NAME"
