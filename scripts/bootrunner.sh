#!/bin/bash
set -e

APP_NAME="auto-app"
JAR=${APP_NAME}.jar
CONF=${APP_NAME}.conf

if [ ! -f ${JAR} ] ; then
  echo "${JAR} not found"
  exit 2
fi

if [ ! -s ${CONF} ] ; then
  echo "${CONF} not found"
  exit 3
fi

# based on spring conf file: https://docs.spring.io/spring-boot/docs/current/reference/html/deployment-install.html#deployment-script-customization-conf-file
# Use custom script for proper signal handling
source ${CONF}

# Services use sopstool to encrypt sensistive data.  By default sopstool
# decrypts all encrypted files in .sops.yaml.
# If the service roles does not have access to other env kms key then use
# `-f` and ENVIRONMENT variable to only decrypt a specific file
exec java ${JAVA_OPTS} -jar ${JAR}
