#!/bin/sh
set -eu
cd "$(dirname "$0")"
lab_port="${PORT:-8080}"
lab_url="http://127.0.0.1:$lab_port"
(
  attempt=0
  while [ "$attempt" -lt 180 ]; do
    if curl --silent --fail "$lab_url/api/state" >/dev/null 2>&1; then
      open "$lab_url"
      exit 0
    fi
    sleep 1
    attempt=$((attempt + 1))
  done
) &
if [ -f target/mission-scheduler-2.0.0.jar ]; then
  exec java -jar target/mission-scheduler-2.0.0.jar
fi
exec ./mission-scheduler
