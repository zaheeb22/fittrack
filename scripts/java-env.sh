# Other scripts load this file to find Java. JAVA_HOME is checked first.
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  FITTRACK_JAVA="$JAVA_HOME/bin/java"
else
  FITTRACK_JAVA=''
  if [ -x /usr/libexec/java_home ]; then
    FITTRACK_JDK=$(/usr/libexec/java_home -v 25 2>/dev/null) || FITTRACK_JDK=''
    if [ -n "$FITTRACK_JDK" ]; then FITTRACK_JAVA="$FITTRACK_JDK/bin/java"; fi
  fi
  if [ -z "$FITTRACK_JAVA" ]; then
    for FITTRACK_CANDIDATE in "$HOME"/.p2/pool/plugins/org.eclipse.justj.openjdk.hotspot.jre.full.*/jre/bin/java; do
      if [ -x "$FITTRACK_CANDIDATE" ]; then FITTRACK_JAVA="$FITTRACK_CANDIDATE"; fi
    done
  fi
  if [ -z "$FITTRACK_JAVA" ] && command -v java >/dev/null 2>&1; then
    FITTRACK_JAVA=$(command -v java)
  fi
fi
if [ -z "$FITTRACK_JAVA" ]; then echo 'Configure JAVA_HOME with JDK 25.' >&2; exit 1; fi
export JAVA_HOME="${FITTRACK_JAVA%/bin/java}"
