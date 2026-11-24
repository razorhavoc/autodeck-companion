#!/bin/sh
# Gradle start up script for POSIX (requires gradle/wrapper/gradle-wrapper.jar,
# generated automatically by Android Studio sync or `gradle wrapper --gradle-version 8.11.1`).

APP_HOME=$( cd "${APP_HOME:-./}" && pwd -P )
CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

if [ ! -f "$CLASSPATH" ]; then
  echo "ERROR: $CLASSPATH not found."
  echo "Open this project in Android Studio to sync Gradle 8.11.1, or run: gradle wrapper --gradle-version 8.11.1"
  exit 1
fi

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" -Xmx64m -Xms64m -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
