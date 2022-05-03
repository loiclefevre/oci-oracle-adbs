#!/bin/sh
#
# Since: April, 2022
# Author: loic.lefevre
# Name: container-entrypoint.sh
# Description: The entrypoint script for the container
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
set -euo pipefail

function stop_database() {
  echo `date +"%H:%M:%S.000"`" WARN  🐳 Container - shutting down database."

  if [[ "${REUSE}" = "false" ]]; then
     # terminate database
     echo `date +"%H:%M:%S.000"`" INFO  🐳 Container - terminating database."
     dragonlite -a terminate -d ${DATABASE_NAME} -p ${PROFILE_NAME} -sp ${SYSTEM_PASSWORD} -w ${WORKLOAD_TYPE} -v 19c
  fi;

  echo `date +"%H:%M:%S.000"`" INFO  🐳 Container - shutting down container."
}

# Set SIGINT & SIGTERM handlers
trap stop_database SIGINT SIGTERM

echo `date +"%H:%M:%S.000"`" INFO  🐳 Container - starting up..."

# Let's start the autonomous database management...
touch /opt/oracle/dragonlite.log
dragonlite -r ${REUSE} -d ${DATABASE_NAME} -p ${PROFILE_NAME} -sp ${SYSTEM_PASSWORD} -w ${WORKLOAD_TYPE} -v 19c -u ${USER} -up ${USER_PASSWORD} -i ${IP_ADDRESS} &

tail -f /opt/oracle/dragonlite.log &

childPID=$!
wait ${childPID}
