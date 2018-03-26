/*
 * The MIT License
 *
 * Copyright 2018 hughsaunders.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.rackspace.jenkins_nodepool;

import hudson.model.Computer;
import hudson.model.Descriptor;
import hudson.model.Slave;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.ManuallyProvidedKeyVerificationStrategy;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * Representation of a Jenkins slave sourced from NodePool.
 *
 * @author hughsaunders
 */
public class NodePoolSlave extends Slave {

    /**
     * Class logger
     */
    private static final Logger LOG = Logger.getLogger(NodePoolSlave.class.getName());

    /**
     * The node from the associated NodePool cluster.
     */
    private transient final NodePoolNode nodePoolNode;

    /**
     * Increment this when modifying this class.
     */
    static final long serialVersionUID = 1L;

    /**
     * Create a new slave
     *
     * @param nodePoolNode  the node from NodePool
     * @param credentialsId  the Jenkins credential identifier
     * @throws Descriptor.FormException  on configuration exception
     * @throws IOException on configuration exception
     */
    public NodePoolSlave(NodePoolNode nodePoolNode, String credentialsId) throws Descriptor.FormException, IOException {

        super(
                nodePoolNode.getName(), // name
                "Nodepool Node", // description
                "/var/lib/jenkins", // TODO this should be the path to the root of the workspace on the slave
                "1", // num executors
                Mode.EXCLUSIVE,
                nodePoolNode.getJenkinsLabel(),
                new SSHLauncher(
                        nodePoolNode.getHost(),
                        nodePoolNode.getPort(),
                        credentialsId,
                        "", //jvmoptions
                        null, // javapath
                        null, //jdkInstaller
                        "", //prefixStartSlaveCmd
                        "", //suffixStartSlaveCmd
                        300, //launchTimeoutSeconds
                        30, //maxNumRetries
                        10, //retryWaitTime
                        new ManuallyProvidedKeyVerificationStrategy(nodePoolNode.getHostKey())
                        //new NonVerifyingKeyVerificationStrategy()
                        //TODO: go back to verifying host key strategy
                ),
                new RetentionStrategy.Always(), //retentionStrategy
                new ArrayList() //nodeProperties
        );
        this.nodePoolNode = nodePoolNode;
    }

    public NodePoolNode getNodePoolNode() {
        return nodePoolNode;
    }

    // Called for deserialisation
    private void readObject(java.io.ObjectInputStream in)
            throws IOException, ClassNotFoundException {
        in.defaultReadObject(); // call default deserializer
        LOG.log(Level.WARNING, "Removing NodePool Slave {0} on startup as its a nodepool node that will have been destroyed", this.toString());
        Jenkins.getInstance().removeNode(this);
    }

    // TODO seems odd storing the computer object on the node since it's related to the slave object here.
    @Override
    public Computer createComputer() {
        NodePoolComputer npc = new NodePoolComputer(this, nodePoolNode);
        return npc;
    }

}
