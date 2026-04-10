#!/usr/bin/env sh
set -eu

INPUT_PATH=${1:-ecs/task-definition.json}
OUTPUT_PATH=${2:-ecs/task-definition.rendered.json}

: "${AWS_ACCOUNT_ID:?AWS_ACCOUNT_ID is required}"
: "${AWS_REGION:?AWS_REGION is required}"
: "${DEPLOY_ENV:?DEPLOY_ENV is required}"
: "${DOCKER_IMAGE_URI:?DOCKER_IMAGE_URI is required}"

sed \
  -e "s|\${AWS_ACCOUNT_ID}|${AWS_ACCOUNT_ID}|g" \
  -e "s|\${AWS_REGION}|${AWS_REGION}|g" \
  -e "s|\${DEPLOY_ENV}|${DEPLOY_ENV}|g" \
  -e "s|\${DOCKER_IMAGE_URI}|${DOCKER_IMAGE_URI}|g" \
  "$INPUT_PATH" > "$OUTPUT_PATH"

echo "Rendered task definition at $OUTPUT_PATH"
