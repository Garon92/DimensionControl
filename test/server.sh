#!/bin/bash
# Test server control. Usage: server.sh start <dir> | stop <dir>
# start feeds the server's stdin from <dir>/cmds.txt, writes its output to <dir>/server.out and waits for "Done".
JAVA8=${JAVA8:-java} # must be Java 8
D=$2
case "$1" in
start)
  : > "$D/cmds.txt"
  (cd "$D" && exec nohup sh -c "tail -n +1 -f cmds.txt | $JAVA8 -Xmx4G -Dfml.queryResult=confirm -Dtest.dir=$D -jar forge-1.7.10-10.13.4.1614-1.7.10-universal.jar nogui > server.out 2>&1") > /dev/null 2>&1 < /dev/null &
  sleep 3
  for i in $(seq 1 300); do
    grep -qE 'Done \(|Encountered an unexpected' "$D/server.out" 2>/dev/null && break
    sleep 0.5
  done
  grep -E 'Done \(|Encountered an unexpected' "$D/server.out"
  ;;
stop)
  echo stop >> "$D/cmds.txt"
  for i in $(seq 1 60); do pgrep -f "Dtest.dir=$D " > /dev/null || break; sleep 0.5; done
  pkill -f "Dtest.dir=$D " 2>/dev/null
  pkill -f "tail -n \+1 -f cmds.txt" 2>/dev/null
  sleep 1
  ;;
esac
