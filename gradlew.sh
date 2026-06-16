#!/bin/sh
# Gradle wrapper - Unix/Linux version
# Download from: https://github.com/gradle/gradle/raw/master-8.9/gradle/wrapper/gradle-wrapper.jar

exec java -Xmx64m -Xms64m -classpath "$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
