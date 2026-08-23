#!/bin/sh
# Maven launcher wrapper: converts unix paths to Windows format for the
# Windows-based Java runtime (this bash has no cygpath).
to_win() {
  p="$1"
  p=$(echo "$p" | sed -E 's#^/([a-zA-Z])/#\1:\\#')
  p=$(echo "$p" | sed 's#/#\\#g')
  echo "$p"
}

MAVEN_HOME_UNIX="C:/Program Files/JetBrains/IntelliJ IDEA 2024.2.1/plugins/maven/lib/maven3"

# expand boot jar glob
for f in "$MAVEN_HOME_UNIX"/boot/plexus-classworlds-*.jar; do
  CLASSWORLDS_JAR_UNIX="$f"
done

MAVEN_HOME_WIN=$(to_win "$MAVEN_HOME_UNIX")
CLASSWORLDS_JAR_WIN=$(to_win "$CLASSWORLDS_JAR_UNIX")
M2CONF_WIN=$(to_win "$MAVEN_HOME_UNIX/bin/m2.conf")
JANSI_WIN=$(to_win "$MAVEN_HOME_UNIX/lib/jansi-native")
PROJECT_WIN=$(to_win "$(pwd)")

if [ -n "$JAVA_HOME" ]; then
  JAVACMD="$JAVA_HOME/bin/java"
else
  JAVACMD="java"
fi

exec "$JAVACMD" $MAVEN_OPTS -classpath "$CLASSWORLDS_JAR_WIN" \
  "-Dclassworlds.conf=$M2CONF_WIN" \
  "-Dmaven.home=$MAVEN_HOME_WIN" \
  "-Dlibrary.jansi.path=$JANSI_WIN" \
  "-Dmaven.multiModuleProjectDirectory=$PROJECT_WIN" \
  org.codehaus.plexus.classworlds.launcher.Launcher "$@"
