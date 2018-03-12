#!/bin/bash

if [ -z "$NODEPOOL_CLOUD_NAME" ]; then
    echo "Need NODEPOOL_CLOUD_NAME to be set name of cloud in clouds.yaml"
    exit 1
fi

if [ -z "$NODEPOOL_REGION_NAME" ]; then
    echo "Need NODEPOOL_REGION_NAME to be set to cloud region"
    exit 1
fi

if [ -z "$NODEPOOL_KEYPAIR_NAME" ]; then
    echo "Need KEYPAIR_NAME to be set to launch instances"
    exit 1
fi

echo "Setting cloud to ${NODEPOOL_CLOUD_NAME}"
sed -i s/%NODEPOOL_CLOUD_NAME%/${NODEPOOL_CLOUD_NAME}/g /etc/nodepool/nodepool.yaml

echo "Setting region to ${NODEPOOL_REGION_NAME}"
sed -i s/%NODEPOOL_REGION_NAME%/${NODEPOOL_REGION_NAME}/g /etc/nodepool/nodepool.yaml

echo "Setting keypair to ${NODEPOOL_KEYPAIR_NAME}"
sed -i s/%NODEPOOL_KEYPAIR_NAME%/${NODEPOOL_KEYPAIR_NAME}/g /etc/nodepool/nodepool.yaml

nodepool-launcher -d -l /etc/nodepool/logging.conf
