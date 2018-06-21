#!/usr/bin/env bash

port=$1
scriptdir="$(cd "$(dirname "$0")"; pwd)"
exec="$scriptdir/build/libs/memcache-server.jar"
cmd="java -jar"

if [[ "$port" = "" ]] ; then
    $cmd "$exec"
else
    $cmd "$exec" --app.port=$port
fi
