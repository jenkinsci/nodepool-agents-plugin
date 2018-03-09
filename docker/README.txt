This directory contains a Docker configuration to run a NodePool integration
environment.  In its current configuration, it is most useful for development
testing of the Jenkins plugin.

Steps to do plugin development work:

1. cd docker
2. Set KEYPAIR_NAME environment variable to the name of your keypair used with
   the IAD region of Rackspace public cloud.
2. docker-compose build 
3. docker-compose up

At this point, you should have 2 containers running:
1. A ZooKeeper container, which NodePool depends on.
2. A NodePool launcher container, which is reponsible for managing a pool of
   cloud server nodes.

The nodepool.yaml configuration used will start a single cloud server instance
of Debian Jessie.

There is also a "sh" script in this directory for convenience access to the
containers' shells.

How to fire jobs on NodePool via the plugin:

1. First complete the above steps.
2. Run "maven hpi:run" from the project root directory
3. Jenkins will start on http://localhost:8080.  Access this URL now.
4. Click "Manage Jenkins" => "Configure System"
5. Create a "Nodepool Cloud" configuration entry near the bottom of the page.
    * "Name" is an arbitrary label to describe the cloud
    * connection string should be "127.0.0.1:2181"
6. At this point, create a Jenkins job and restrict the label where it can
   be run to a TBD label meaning.  Jenkins will query the nodepool "Cloud"
   plugin to see if it can provision a node for the provided label.

7. TODO!!!  Plugin in active development!

