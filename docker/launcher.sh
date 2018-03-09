#!/bin/bash

if [ -z "$KEYPAIR_NAME" ]; then
    echo "Need KEYPAIR_NAME to be set to launch instances"
    exit 1
fi

echo "Setting keypair to ${KEYPAIR_NAME}"
sed -i s/%KEYPAIR_NAME%/${KEYPAIR_NAME}/g /etc/nodepool/nodepool.yaml
nodepool-launcher -d -l /etc/nodepool/logging.conf
