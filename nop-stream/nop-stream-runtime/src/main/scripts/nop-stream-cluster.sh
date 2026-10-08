#!/usr/bin/env bash
# Copyright (c) 2017-2024 Nop Platform. All rights reserved.
# nop-stream production cluster launcher (WI1, plan 2328).
#
# Subcommands:
#   classpath              print the assembled runtime classpath (compile scope)
#   taskmanager  <args>    launch TaskManagerMain   (key=value args, see its usage())
#   coordinator  <args>    launch JobCoordinatorMain (key=value args, see its usage())
#
# Classpath assembly:
#   1. If NOP_STREAM_CLASSPATH is set it is used verbatim (escape hatch for
#      pre-assembled deployments).
#   2. Otherwise `mvn dependency:build-classpath` resolves the compile-scope
#      classpath of nop-stream-runtime from the local repository and caches the
#      result under <project-root>/_tmp/nop-stream-runtime-classpath.txt.
#   3. NOP_STREAM_EXTRA_CLASSPATH is ALWAYS appended when set — use it for your
#      JDBC driver jar: the compile classpath ships no JDBC driver.
#
# Examples (single machine, H2 AUTO_SERVER shared file):
#   H2_JAR=$(ls ~/.m2/repository/com/h2database/h2/*/h2-*.jar | head -1)
#   export NOP_STREAM_EXTRA_CLASSPATH="$H2_JAR"
#   DB="jdbc:h2:file:/tmp/nop-stream-cluster;AUTO_SERVER=TRUE;MODE=MySQL"
#   ./nop-stream-cluster.sh taskmanager  nodeId=tm-0 jdbcUrl="$DB" topicNamespace=run-1
#   ./nop-stream-cluster.sh coordinator jobId=job-1 jdbcUrl="$DB" topicNamespace=run-1 \
#       checkpointBaseDir=/tmp/nop-stream-checkpoints expectedNodeIds=tm-0
# Production MySQL launch adds: jdbcDriver=com.mysql.cj.jdbc.Driver ... see
# ClusterLaunchConfig.KEY_JDBC_DRIVER (driver jar via NOP_STREAM_EXTRA_CLASSPATH).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# Locate the Maven project root: the topmost directory holding a pom.xml when
# walking up from this script (module poms nest, the root is the last hit).
find_project_root() {
  local dir="$SCRIPT_DIR"
  local root=""
  while [ "$dir" != "/" ]; do
    if [ -f "$dir/pom.xml" ]; then
      root="$dir"
    fi
    dir="$(dirname "$dir")"
  done
  if [ -z "$root" ]; then
    echo "[nop-stream-cluster] ERROR: no pom.xml found above $SCRIPT_DIR" >&2
    return 1
  fi
  echo "$root"
}

PROJECT_ROOT="$(find_project_root)"
RUNTIME_MODULE="$PROJECT_ROOT/nop-stream/nop-stream-runtime"
CP_CACHE="$PROJECT_ROOT/_tmp/nop-stream-runtime-classpath.txt"

build_classpath() {
  if [ -n "${NOP_STREAM_CLASSPATH:-}" ]; then
    echo "$NOP_STREAM_CLASSPATH"
    return 0
  fi
  if [ ! -f "$CP_CACHE" ]; then
    command -v mvn >/dev/null 2>&1 || {
      echo "[nop-stream-cluster] ERROR: 'mvn' not found on PATH and NOP_STREAM_CLASSPATH is unset." >&2
      echo "  Either install Maven, pre-build the classpath file at $CP_CACHE," >&2
      echo "  or export NOP_STREAM_CLASSPATH directly." >&2
      return 1
    }
    mkdir -p "$(dirname "$CP_CACHE")"
    echo "[nop-stream-cluster] assembling compile classpath via mvn dependency:build-classpath ..." >&2
    # includeScope=runtime keeps the published dependency face (compile+runtime):
# the default (all scopes) would drag test-jar/junit/h2/message-brokers onto a
# production classpath and make global initialization load their defaults beans.
    (cd "$RUNTIME_MODULE" && mvn -q dependency:build-classpath -DincludeScope=runtime -Dmdep.outputFile="$CP_CACHE") || {
      echo "[nop-stream-cluster] ERROR: classpath assembly failed; run 'mvn install -DskipTests' first." >&2
      return 1
    }
  fi
  local cp
  cp="$(cat "$CP_CACHE")"
  # The runtime module's own classes/jar are not part of build-classpath output.
  local runtime_jar
  runtime_jar="$(ls "$RUNTIME_MODULE"/target/nop-stream-runtime-*.jar 2>/dev/null | grep -v -e test -e sources | head -1 || true)"
  if [ -z "$runtime_jar" ]; then
    runtime_jar="$RUNTIME_MODULE/target/classes"
  fi
  echo "$runtime_jar:$cp${NOP_STREAM_EXTRA_CLASSPATH:+:$NOP_STREAM_EXTRA_CLASSPATH}"
}

CP="$(build_classpath)"

case "${1:-}" in
  classpath)
    echo "$CP"
    ;;
  taskmanager)
    shift
    exec java -cp "$CP" io.nop.stream.runtime.launch.TaskManagerMain "$@"
    ;;
  coordinator)
    shift
    exec java -cp "$CP" io.nop.stream.runtime.launch.JobCoordinatorMain "$@"
    ;;
  *)
    echo "Usage: nop-stream-cluster.sh {classpath|taskmanager <key=val...>|coordinator <key=val...>}" >&2
    echo "" >&2
    echo "Environment: NOP_STREAM_CLASSPATH (override assembly), NOP_STREAM_EXTRA_CLASSPATH (driver jars)." >&2
    exit 2
    ;;
esac
