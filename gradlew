#!/bin/sh
# Gradle wrapper launcher (minimal POSIX version).
APP_HOME=$(cd "$(dirname "$0")" && pwd -P) || exit
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
    if [ ! -x "$JAVACMD" ]; then
        echo "ERROR: JAVA_HOME is set to an invalid directory: $JAVA_HOME" >&2
        exit 1
    fi
else
    JAVACMD=java
    command -v java >/dev/null 2>&1 || { echo "ERROR: java not found. Install JDK 17 and set JAVA_HOME." >&2; exit 1; }
fi

exec "$JAVACMD" -Xmx64m -Xms64m $JAVA_OPTS $GRADLE_OPTS \
    "-Dorg.gradle.appname=gradlew" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
