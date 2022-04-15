#!/bin/bash
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
set -Eeuo pipefail

# Stop container when SIGINT or SIGTERM is received
########### stop database helper function ############
function stop_database() {
  echo "CONTAINER: shutdown request received."
  echo "CONTAINER: shutting down database!"

  echo "CONTAINER: stopping container."
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

  if [ -d "${ORACLE_BASE}/oradata/dbconfig/${ORACLE_SID}" ]; then
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
  fi;

}

###########################
###########################
######### M A I N #########
###########################
###########################

# Set SIGINT & SIGTERM handlers
trap stop_database SIGINT SIGTERM

echo "CONTAINER: starting up..."

# Setup all required environment variables
setup_env_vars

# If database does not yet exist, create directory structure
if [ -z "${DATABASE_ALREADY_EXISTS:-}" ]; then
  echo "CONTAINER: first database startup, initializing..."

# Otherwise check that symlinks are in place
else
  echo "CONTAINER: database already initialized."
fi;

# Startup database
echo "CONTAINER: starting up Oracle Database..."
echo ""

# Check whether database did come up successfully
if healthcheck.sh; then

  # First database startup / initialization
  if [ -z "${DATABASE_ALREADY_EXISTS:-}" ]; then

    # Set Oracle password if it's the first DB startup
    echo "CONTAINER: Resetting SYS and SYSTEM passwords."

    # If password is specified
    if [ -n "${ORACLE_PASSWORD:-}" ]; then
      #resetPassword "${ORACLE_PASSWORD}"

    # Generate random password
    elif [ -n "${ORACLE_RANDOM_PASSWORD:-}" ]; then
      RANDOM_PASSWORD=$(date +%s | sha256sum | base64 | head -c 8)
      #resetPassword "${RANDOM_PASSWORD}"
      echo "############################################"
      echo "ORACLE PASSWORD FOR SYS AND SYSTEM: ${RANDOM_PASSWORD}"
      echo "############################################"

    # Should not happen unless script logic changes
    else
      echo "SCRIPT ERROR: Unspecified password!"
      echo "Please report a bug at https://github.com/gvenzl/oci-oracle-xe/issues with your environment details."
      exit 1;
    fi;

    # Check whether user PDB should be created
    # setup_env_vars has already validated >=18c requirement
    if [ -n "${ORACLE_DATABASE:-}" ]; then
      create_database
    fi;

    # Check whether app user should be created
    # setup_env_vars has already validated environment variables
    if [ -n "${APP_USER:-}" ]; then
      create_app_user
    fi;

  echo ""
  echo "#########################"
  echo "DATABASE IS READY TO USE!"
  echo "#########################"
  echo ""
else
  echo "############################################"
  echo "DATABASE STARTUP FAILED!"
  echo "CHECK LOG OUTPUT ABOVE FOR MORE INFORMATION!"
  echo "############################################"
  exit 1;
fi;

childPID=$!
wait ${childPID}
