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

import com.spotify.docker.client.DefaultDockerClient;
import com.spotify.docker.client.DockerClient;
import com.spotify.docker.client.DockerClient.LogsParam;
import com.spotify.docker.client.exceptions.DockerCertificateException;
import com.spotify.docker.client.exceptions.DockerException;
import com.spotify.docker.client.messages.ContainerConfig;
import com.spotify.docker.client.messages.ContainerCreation;
import com.spotify.docker.client.messages.ContainerInfo;
import com.spotify.docker.client.messages.HostConfig;
import com.spotify.docker.client.messages.NetworkSettings;
import com.spotify.docker.client.messages.PortBinding;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 *
 * @author hughsaunders
 */
public class NodePoolRule implements TestRule {

    private static final Logger LOG = Logger.getLogger(NodePoolRule.class.getName());

    private DockerClient docker;
    private CuratorFramework conn;

    public NodePoolRule() {
        docker = null;
        try {
            docker = DefaultDockerClient.fromEnv().build();
        } catch (DockerCertificateException e) {
            LOG.severe(e.getMessage());
        }
    }
//
//    private class NodePoolContainerRule extends DockerRule {
//
//        NodePoolContainerRule(Integer zkPort) {
//            super(ImmutableDockerConfig.builder()
//                    .name("nodepool")
//                    .image("hughsaunders/nodepoolrule")
//                    .addContainerConfigurer(builder -> {
//                        builder.env(
//                                "ZKPORT=" + zkPort.toString()
//                        );
//                    }).ports("9999") // ImmutableDockerConfig requires ports, but this port isn't used. 
//                    .build());
//        }
//    }

    public CuratorFramework getCuratorConnection() {
        return conn;
    }

    private ContainerInfo startContainer(String image, String name, List<String> env, List<String> links,  String... ports)
            throws DockerException, InterruptedException {

        //get base image
        docker.pull(image);

        //bind ports
        final Map<String, List<PortBinding>> portBindings = new HashMap<>();
        List<PortBinding> randomPort = new ArrayList<>();
        randomPort.add(PortBinding.randomPort("0.0.0.0"));
        for (String port : ports) {
            portBindings.put(port+"/tcp", randomPort);
        }
        final HostConfig hostConfig = HostConfig.builder()
                .portBindings(portBindings)
                .links(links)
                .build();

        // Create container with exposed ports
        final ContainerConfig containerConfig = ContainerConfig.builder()
                .hostConfig(hostConfig)
                .image(image).exposedPorts(ports)
                .env(env)
                .build();

        final ContainerCreation creation = docker.createContainer(containerConfig,name);
        final String id = creation.id();


        // Start containerÏ
        docker.startContainer(id);
        
        // Inspect container after starting to get runtime port mappings
        final ContainerInfo info = docker.inspectContainer(id);
        
        return info;

    }

    @Override
    public Statement apply(Statement base, Description description) {

        return new Statement() {

            @Override
            public void evaluate() throws Throwable {
                ContainerInfo zkc;
                ContainerInfo npc;

                Map<String, ContainerInfo> containers = new HashMap();
                List<String> zkEnv = new ArrayList();
                List<String> npEnv = new ArrayList();
                List<String> nodepoolLinks = new ArrayList();
                List<String> zkLinks = new ArrayList();

                // start zookeeper container
                zkc = startContainer("zookeeper:3.4", "ZK", zkEnv, zkLinks, "2181");
                containers.put("Zookeeper", zkc);

                // Get zookeeper port
                HostConfig zkHostConfig = zkc.hostConfig();
                NetworkSettings networkSettings = zkc.networkSettings();
                Map<String, List<PortBinding>> portBindings = networkSettings.ports();
                List<PortBinding> zkClientPortBindings = portBindings.get("2181/tcp");
                PortBinding zkClientPortBinding = zkClientPortBindings.get(0);
                LOG.severe(zkClientPortBinding.toString());
                Integer zkPort = Integer.parseInt(zkClientPortBinding.hostPort());

                // Add zookeeper port to nodepool container env and start it
                //npEnv.add("ZKPORT=" + zkPort.toString());
                //npEnv.forEach((s) -> {
                //    LOG.log(Level.SEVERE, "npenv: {0}", s);
                //});
               
                nodepoolLinks.add("ZK");
                npc = startContainer("hughsaunders/nodepoolrulejunit", "NP", npEnv, nodepoolLinks,  "9999");
                containers.put("Nodepool", npc);

                // connect to zookeeper
                conn = CuratorFrameworkFactory.builder()
                        .connectString(zkClientPortBinding.hostIp()
                                + ":" + zkClientPortBinding.hostPort())
                        .namespace("nodepool")
                        .retryPolicy(new ExponentialBackoffRetry(1000,3))
                        .build();
                conn.start();
                try {
                    base.evaluate();
                } finally {
                    conn.close();
                    
                    ContainerInfo c;
                    for (String name : containers.keySet()){
                        c = containers.get(name);
                        docker.stopContainer(c.id(), 0);
                        LOG.info(MessageFormat.format("---- {0} Container Output----",name));
                        LOG.info(docker.logs(c.id(), LogsParam.stdout(), LogsParam.stderr()).readFully());
                        docker.removeContainer(c.id());
                    }
          
                }

            }

        };
    }

}
