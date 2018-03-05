#!/bin/bash

set -x

# compound variables to insert the correct container name into the variables
# injected by docker. Expand with ${!var}
C_ZK_PORT="${ZK_NAME}_PORT_2181_TCP_PORT"
C_ZK_ADDR="${ZK_NAME}_PORT_2181_TCP_ADDR"

ZK_PORT="${!C_ZK_PORT}"
ZK_ADDR="${!C_ZK_ADDR}"

if [[ -z $ZK_PORT ]] || [[ -z $ZK_ADDR ]]
then
    echo "Missing ZK port or ADDR: ${ZK_PORT}:${ZK_ADDR}"
    exit 1
fi

hostip=$(ip a l dev eth0 \
        |awk '/inet /{split($2,w,"/"); print w[1] }')
sed -ibk -e 's/host: localhost/host: '${ZK_ADDR}'/' nodepool.yaml;
sed -ibk -e '/host:/ a\
    port: '${ZK_PORT} nodepool.yaml

cat nodepool.yaml

