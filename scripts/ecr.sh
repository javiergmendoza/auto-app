#!/bin/bash
# Build docker image and upload to ECR repository
# Used in TravisCI so no crazy dependencies
# set -x
set -e

function join_by { local IFS="$1"; shift; echo "$*"; }

# Default, required ENV vars
AWS_REGION=${AWS_REGION:-us-east-1}
AWS_DEFAULT_REGION=${AWS_REGION:-us-east-1}
AWS_ACCOUNT_NUMBER=${AWS_ACCOUNT_NUMBER:-920239295815}

AWS_MESOSPHERE_RUN="docker run --rm -e AWS_SESSION_TOKEN -e AWS_ACCESS_KEY_ID -e AWS_SECRET_ACCESS_KEY -e AWS_REGION -e AWS_DEFAULT_REGION mesosphere/aws-cli"

# Usage: $0 [repository] <image_tag ...>
# The repo name is required to push to ECR. the current git rev is used as a primary unique key. additional tags can be specified (eg build number)
# Example: ecr.sh generated-55c04ece4dd94be02fe6fa23fd022913 build_number
repo=${1:-generated-55c04ece4dd94be02fe6fa23fd022913}
tags=("${@:2}")

#get current git information
ts=$(date +%F-%H%M%S) # TODO make this overridable somehow? or is this necessary?
gitrev="git-$(git describe --dirty=-$ts --always)"

# Login to ECR via docker.
aws_login=$($AWS_MESOSPHERE_RUN ecr get-login --no-include-email --region $AWS_REGION)
if echo "$aws_login" | grep -q -E "^docker login -u AWS -p \S+( -e none)? https://[0-9]+.dkr.ecr.$AWS_REGION.amazonaws.com$"
then
  $aws_login
else
  echo "INVALID LOGIN!"
fi
echo "Logged in to docker"

#build the docker repo tags
repotag="$repo:${gitrev}"
remotetag="$AWS_ACCOUNT_NUMBER.dkr.ecr.us-east-1.amazonaws.com/${repotag}"

# Build and push image via docker
docker build -t ${repotag} -t ${remotetag} --build-arg GIT_REV="$gitrev" --build-arg TAGS="$(join_by , ${tags[@]})" .
echo "Pushing image: $gitrev"
docker push ${remotetag}

# Now add custom tags to existing ECR image by re-putting just the manifest via ecr API
for t in ${tags[@]}; do
  echo "Tagging image: $t"
  manifest=$($AWS_MESOSPHERE_RUN ecr batch-get-image --region $AWS_REGION --repository-name $repo --image-ids imageTag=${gitrev} --query images[].imageManifest --output text)
  $AWS_MESOSPHERE_RUN ecr put-image --region $AWS_REGION --repository-name $repo --image-tag $t --image-manifest "$manifest"
done
