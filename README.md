# NodePool plugin

This repository contains a Jenkins plugin to perform builds on cloud instance  nodes sourced
from [NodePool](https://docs.openstack.org/infra/nodepool/).

## Structure

The implementation consists of a listener class that creates agents (slaves) when a item with a
matching label enters the Jenkins build queue.  There is also another listener that releases each
node after it is used once.
