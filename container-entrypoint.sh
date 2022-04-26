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

# Stop container when SIGINT or SIGTERM is received
########### stop database helper function ############
function stop_database() {
  echo `date +"%H:%M:%S.000"`" WARN  🐳 org.testcontainers.containers.OracleADBContainer - shutting down database."

  echo `date +"%H:%M:%S.000"`" INFO  🐳 org.testcontainers.containers.OracleADBContainer - shutting down container."
}

# Retrieve value from ENV[_FILE] variable
# usage: file_env VARIABLE NAME [DEFAULT VALUE]
#    ie: file_env 'ORACLE_PASSWORD' 'example'
# (will allow for "$ORACLE_PASSWORD_FILE" to fill in the value of
#  "$ORACLE_PASSWORD" from a file, especially for container secrets feature)
file_env() {

  # Get name of variable
  local variable="${1}"
  # Get name of variable_FILE
  local file_variable="${variable}_FILE"

  # If both variable and file_variable are specified, throw error and abort
  if [ -n "${!variable:-}" ] && [ -n "${!file_variable:-}" ]; then
    echo "Both \$${variable} and \$${file_variable} are specified but are mutually exclusive."
    echo "Please specify only one of these variables."
    exit 1;
  fi;

  # Set value to default value, if any
  local value="${2:-}"

  # Read value of variable, if any
  if [ -n "${!variable:-}" ]; then
    value="${!variable}"
  # Read value of variable_FILE, if any
  elif [ -n "${!file_variable:-}" ]; then
    value="$(< "${!file_variable}")"
  fi

  export "${variable}"="${value}"
}

# Setup environment variables
function setup_env_vars() {

  declare -g DATABASE_ALREADY_EXISTS

  declare -g ORACLE_VERSION

  if [ -d "/oradata/dbconfig/${ORACLE_SID}" ]; then
    DATABASE_ALREADY_EXISTS="true";
  else

    # Variable is only supported for >=18c
    if [[ "${ORACLE_VERSION}" = "11.2"* ]]; then
      unset "ORACLE_DATABASE"
    else
      # Allow for ORACLE_DATABASE or ORACLE_DATABASE_FILE
      file_env "ORACLE_DATABASE"
    fi;

    # Allow for ORACLE_PASSWORD or ORACLE_PASSWORD_FILE
    file_env "ORACLE_PASSWORD"

    # Password is mandatory for first container start
    if [ -z "${ORACLE_PASSWORD:-}" ] && [ -z "${ORACLE_RANDOM_PASSWORD:-}" ]; then
      echo "Oracle Database SYS and SYSTEM passwords have to be specified at first database startup."
      echo "Please specify a password either via the \$ORACLE_PASSWORD variable, e.g. '-e ORACLE_PASSWORD=<password>'"
      echo "or set the \$ORACLE_RANDOM_PASSWORD environment variable to any value, e.g. '-e ORACLE_RANDOM_PASSWORD=yes'."
      exit 1;
    # ORACLE_PASSWORD and ORACLE_RANDOM_PASSWORD are mutually exclusive
    elif [ -n "${ORACLE_PASSWORD:-}" ] && [ -n "${ORACLE_RANDOM_PASSWORD:-}" ]; then
      echo "Both \$ORACLE_RANDOM_PASSWORD and \$ORACLE_PASSWORD[_FILE] are specified but are mutually exclusive."
      echo "Please specify only one of these variables."
      exit 1;
    fi;

    # Allow for APP_USER_PASSWORD or APP_USER_PASSWORD_FILE
    file_env "APP_USER_PASSWORD"

    # Check whether both variables have been specified.
    if [ -n "${APP_USER:-}" ] && [ -z "${APP_USER_PASSWORD}" ]; then
      echo "\$APP_USER has been specified without \$APP_USER_PASSWORD[_FILE]."
      echo "Both variables are required, please specify \$APP_USER and \$APP_USER_PASSWORD[_FILE]."
      exit 1;
    elif [ -n "${APP_USER_PASSWORD:-}" ] && [ -z "${APP_USER:-}" ]; then
      echo "\$APP_USER_PASSWORD[_FILE] has been specified without \$APP_USER."
      echo "Both variables are required, please specify \$APP_USER and \$APP_USER_PASSWORD[_FILE]."
      exit 1;
    fi;
  fi;
}


# Create pluggable database
function create_database {

  echo "CONTAINER: Creating pluggable database."

  RANDOM_PDBADIN_PASSWORD=$(date +%s | sha256sum | base64 | head -c 8)

  PDB_CREATE_START_TMS=$(date '+%s')


  PDB_CREATE_END_TMS=$(date '+%s')
  PDB_CREATE_DURATION=$(( PDB_CREATE_END_TMS - PDB_CREATE_START_TMS ))
  echo "CONTAINER: DONE: Creating pluggable database, duration: ${PDB_CREATE_DURATION} seconds."

  unset RANDOM_PDBADIN_PASSWORD
}

# Create schema user for the application to use
function create_app_user {

  # Check whether the user needs to be in a PDB or not
  echo "CONTAINER: Creating database application user."

  # If ORACLE_DATABASE is specified, create user also in app PDB (only applicable >=18c)
  if [ -n "${ORACLE_DATABASE:-}" ]; then
  	echo "noop"
  fi;

}

###########################
###########################
######### M A I N #########
###########################
###########################

# Set SIGINT & SIGTERM handlers
trap stop_database SIGINT SIGTERM

echo `date +"%H:%M:%S.000"`" INFO  🐳 org.testcontainers.containers.OracleADBContainer - starting up..."

# Setup all required environment variables
#setup_env_vars

# Let's start the autonomous database management...
touch /opt/oracle/dragonlite.log
dragonlite -a start -d AJDSAI2 -p DEFAULT -r eu-frankfurt-1 -sp C0mplex_Passw0rd -w Ajd -v 21c -f -u test -up C0mplex_Passw0rd -i 89.84.109.253 &

tail -f /opt/oracle/dragonlite.log &

childPID=$!
wait ${childPID}
