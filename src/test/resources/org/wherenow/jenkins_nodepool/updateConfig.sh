#!/bin/bash

set -x

hostip=$(ip a l dev eth0 \
        |awk '/inet /{split($2,w,"/"); print w[1] }')
sed -ibk -e 's/host: localhost/host: '${hostip}'/' nodepool.yaml;
sed -ibk -e '/host:/ a\
    port: '${ZKPORT} nodepool.yaml

cat nodepool.yaml

