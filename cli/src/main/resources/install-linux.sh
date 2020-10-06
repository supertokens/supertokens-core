#!/bin/bash

ST_INSTALL_LOC=$ST_INSTALL_LOC

if grep docker /proc/1/cgroup -qa; then
  trap 'kill -TERM $PID' TERM INT
  "${ST_INSTALL_LOC}"jre/bin/java -classpath "${ST_INSTALL_LOC}cli/*" io.supertokens.cli.Main false "${ST_INSTALL_LOC}" $@ &
  PID=$!
  wait $PID
else
  "${ST_INSTALL_LOC}"jre/bin/java -classpath "${ST_INSTALL_LOC}cli/*" io.supertokens.cli.Main false "${ST_INSTALL_LOC}" $@
fi
