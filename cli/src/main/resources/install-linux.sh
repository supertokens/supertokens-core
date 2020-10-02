#!/bin/bash

trap 'kill -TERM $PID' TERM INT

ST_INSTALL_LOC=$ST_INSTALL_LOC

"${ST_INSTALL_LOC}"jre/bin/java -classpath "${ST_INSTALL_LOC}cli/*" io.supertokens.cli.Main false "${ST_INSTALL_LOC}" $@ &
PID=$!
wait $PID
if [ $? -eq 0 ] && [ "$#" -ne 0 ] && [ "$1" == update ]; then
  "${ST_INSTALL_LOC}"/.update/supertokensExe update-complete --originalInstallDir="${ST_INSTALL_LOC}" &
  PID=$!
  wait $PID
fi
