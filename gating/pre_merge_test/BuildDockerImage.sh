#!/bin/bash -xeu

DOCKER_REPO="${DOCKER_REPO:-hughsaunders/jenkinsnodepool}"

# can't refer to files outside the context (working dir) so temporarily 
# copy in pom, so that deps can be read and included in the image.
cp ../../pom.xml pom.xml
docker build . -t ${DOCKER_REPO} 
docker push ${DOCKER_REPO}
rm pom.xml

# remove cached images from current long running slaves
# requires clouds.yml with Jenkins account and pub key in https://github.com/rcbops/rpc-gating/blob/master/keys/rcb.keys
ansible-playbook -i openstack.py dockercacheremove.yml


