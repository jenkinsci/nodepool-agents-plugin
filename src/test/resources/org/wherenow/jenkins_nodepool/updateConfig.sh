#!/bin/bash

set -x

hostip=$(ip a l dev eth0 \
        |awk '/inet /{split($2,w,"/"); print w[1] }')
sed -ibk -e 's/host: localhost/host: '${ZK_PORT_2181_TCP_ADDR}'/' nodepool.yaml;
sed -ibk -e '/host:/ a\
    port: '${ZK_PORT_2181_TCP_PORT} nodepool.yaml

cat nodepool.yaml

