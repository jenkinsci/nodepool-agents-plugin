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
package org.wherenow.jenkins_nodepool;

import hudson.model.Descriptor;
import hudson.model.Node.Mode;
import hudson.model.Slave;
import hudson.plugins.sshslaves.SSHLauncher;
import hudson.plugins.sshslaves.verifiers.ManuallyProvidedKeyVerificationStrategy;
import hudson.slaves.RetentionStrategy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 *
 * @author hughsaunders
 */
public class NodePoolNode extends Slave {

    private static final Logger LOGGER = Logger.getLogger(NodePoolNode.class.getName());

    public NodePoolNode(String name, String host, int port,
            String hostKey, String credentialsId, String label) throws Descriptor.FormException, IOException {

        super(
                name, // name
                "Nodepool Node", // description
                "/var/lib/jenkins", // TODO this should be the path to the root of the workspace on the slave
                "2", // num executors
                Mode.EXCLUSIVE,
                label,
                new SSHLauncher(
                        host,
                        port,
                        credentialsId,
                        "", //jvmoptions
                        null, // javapath
                        null, //jdkInstaller
                        "", //prefixStartSlaveCmd
                        "", //suffixStartSlaveCmd
                        300, //launchTimeoutSeconds
                        30, //maxNumRetries
                        10, //retryWaitTime
                        new ManuallyProvidedKeyVerificationStrategy(hostKey)
                ),
                new RetentionStrategy.Demand(1, 1), //retention strategy TODO: use a more suitlable strategy
                new ArrayList() //nodeProperties
        );
    }
}