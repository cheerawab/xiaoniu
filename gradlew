#!/bin/sh

# Gradle start up script for UNIX
APP_HOME=$(cd "$(dirname "$0")" && pwd -P)
APP_NAME="gradlew"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
