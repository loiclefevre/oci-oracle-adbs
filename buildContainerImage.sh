#!/bin/bash
# Since: April, 2022
# Author: loiclefevre
# Name: buildContainerImage.sh
# Description: Build a Container image
#
# Copyright 2022 Loïc Lefèvre
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Exit on errors
# Great explanation on https://vaneyckt.io/posts/safer_bash_scripts_with_set_euxo_pipefail/
set -Eeuo pipefail

VERSION="19.0.0"
IMAGE_NAME="loiclefevre/oracle-adb"

function usage() {
    cat << EOF
Usage: buildContainerImage.sh [-v version] [-o] [container build option]
Builds a container image for Oracle Autonomous Database Shared infrastructure.
Parameters:
   -v: version of Oracle Autonomous Database to build
       Choose one of: 19.0.0, 21.0.0
   -o: passes on container build option
Apache License, Version 2.0
Copyright (c) 2022 Loïc Lefèvre
EOF

}

while getopts "v:o:" optname; do
  case "${optname}" in
    "h")
      usage
      exit 0;
      ;;
    "v")
      VERSION="${OPTARG}"
      ;;
    "o")
      eval "BUILD_OPTS=(${OPTARG})"
      ;;
    "?")
      usage;
      exit 1;
      ;;
    *)
    # Should not occur
      echo "Unknown error while processing options inside buildContainerImage.sh"
      ;;
  esac;
done;

IMAGE_NAME="${IMAGE_NAME}:${VERSION}"

echo "BUILDER: building image $IMAGE_NAME"

BUILD_START_TMS=$(date '+%s')

rm -f dragonlite
upx --best -k -o dragonlite target/dragonlite-linux-x86_64
#cp target/dragonlite-linux-x86_64 ./dragonlite

buildah bud -f Dockerfile."${VERSION//./}" -t "${IMAGE_NAME}" --build-arg BUILD_MODE="test"

BUILD_END_TMS=$(date '+%s')
BUILD_DURATION=$(( BUILD_END_TMS - BUILD_START_TMS ))

echo "Build of container image ${IMAGE_NAME} completed in ${BUILD_DURATION} seconds."
