#!/bin/bash

set -o nounset
set -o errexit

VERSION=${1}
BUILDPATH=${2}
REPO_USERNAME=${REPO_USERNAME:-}
REPO_PASSWORD=${REPO_PASSWORD:-}

mkdir -p target

docker run --rm -v $(pwd)/${BUILDPATH}:/apps -w /apps \
    --pids-limit 4096 \
    -e REPO_USERNAME="${REPO_USERNAME}" -e REPO_PASSWORD="${REPO_PASSWORD}" \
    -e VERSION="${VERSION}" \
    -e GOMAXPROCS=2 \
    --entrypoint sh \
    alpine/helm:3.11.1 -c '
        helm repo add --username "$REPO_USERNAME" --password "$REPO_PASSWORD" gentics https://repo.gentics.com/repository/helm/
        helm repo add --username "$REPO_USERNAME" --password "$REPO_PASSWORD" gentics-snapshots https://repo.gentics.com/repository/helm-snapshots/
        helm repo update
        helm dependency build helm-chart
        helm package helm-chart --version $VERSION --app-version $VERSION -d target
    '