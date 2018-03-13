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

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.AttachContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.DockerCmdExecFactory;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.Link;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.async.ResultCallbackTemplate;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.LogContainerResultCallback;
import com.github.dockerjava.netty.NettyDockerCmdExecFactory;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.slf4j.LoggerFactory;

/**
 *
 * @author hughsaunders
 */
public class NodePoolRule implements TestRule {

    private class logCallBack extends ResultCallbackTemplate<LogContainerResultCallback, Frame> {

        private String container;

        logCallBack() {
            this("");
        }

        logCallBack(String container) {
            this.container = container;
        }

        @Override
        public void onNext(Frame item) {
            LOG.info(MessageFormat.format(
                    "{0}: {1}",
                    container,
                    new String(item.getPayload()).trim())
            );
        }
    }

    private static final Logger LOG = Logger.getLogger(NodePoolRule.class.getName());

    private DockerClientConfig config;
    private DockerClient docker;
    private CuratorFramework conn;
    private Map<String, CreateContainerResponse> containers;

    public NodePoolRule() {
        this("unix:///var/run/docker.sock");
    }

    public NodePoolRule(String dockerConnection) {

        config = DefaultDockerClientConfig.createDefaultConfigBuilder()
                .withDockerHost(dockerConnection)
                .withDockerTlsVerify(Boolean.FALSE)
                .build();

        DockerCmdExecFactory dcef = new NettyDockerCmdExecFactory();
        docker = DockerClientBuilder.getInstance(config)
                .withDockerCmdExecFactory(dcef)
                .build();

        containers = new HashMap();
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

    public void runOnContainer(CreateContainerResponse c, String cmd) throws InterruptedException {

        ExecCreateCmdResponse exec = docker.execCreateCmd(c.getId())
                .withAttachStderr(Boolean.TRUE)
                .withAttachStdout(Boolean.TRUE)
                .withCmd("date")
                .exec();
        docker.execStartCmd(exec.getId())
                .exec(new ExecStartResultCallback(System.out, System.err))
                .awaitCompletion();
    }

    public void nprun(String cmd) throws InterruptedException {
        runOnContainer(getContainerMatchingPrefix("NP"), cmd);
    }

    public CreateContainerResponse getContainerMatchingPrefix(String prefix) {
        for (String c : containers.keySet()) {
            if (c.startsWith(prefix)) {
                return containers.get(c);
            }
        }
        throw new IllegalStateException(MessageFormat.format("Failed to find container matching prefix {0}", prefix));
    }

    public CuratorFramework getCuratorConnection() {
        return conn;
    }

    public DockerClient getDockerClient() {
        return docker;
    }

    public Map<String, CreateContainerResponse> getContainers() {
        return containers;
    }

    private CreateContainerResponse startContainer(String image,
            String name, List<String> env, List<String> links,
            String... ports) {

        Ports portBindings = new Ports();
        List<ExposedPort> exposedPorts = new ArrayList();
        ExposedPort exposedPort;
        for (String port : ports) {
            exposedPort = ExposedPort.tcp(Integer.parseInt(port));
            exposedPorts.add(exposedPort);
            portBindings.bind(exposedPort, new Ports.Binding(null, null));
        }

        // List<String> --> Link[]
        Link[] linksa = new Link[links.size()];
        IntStream.range(0, links.size()).forEach(idx -> {
            String linkString = links.get(idx);
            Link link = new Link(linkString, linkString);
            linksa[idx] = link;
        });

        CreateContainerResponse container = docker.createContainerCmd(image)
                .withExposedPorts(exposedPorts)
                .withPortBindings(portBindings)
                .withPublishAllPorts(true)
                .withName(name)
                .withLinks(linksa)
                .withEnv(env)
                .exec();

        docker.startContainerCmd(container.getId()).exec();

        docker.logContainerCmd(container.getId())
                .withStdOut(Boolean.TRUE)
                .withStdErr(Boolean.TRUE)
                .withTailAll()
                .exec(new logCallBack(name));
        return container;

    }

    @Override
    public Statement apply(Statement base, Description description) {

        return new Statement() {

            private String containerName(String base) {
                Random random = new Random();
                Integer id = random.nextInt();
                return MessageFormat.format("{0}{1,number,####}", base, id);
            }

            @Override
            public void evaluate() throws Throwable {
                LOG.info("NodePoolRule evaluate top");
                CreateContainerResponse zkc;
                CreateContainerResponse npc;
                String npName = containerName("NP");
                String zkName = containerName("ZK");

                // add random int to names to allow parallel running. 
                List<String> zkEnv = new ArrayList();
                List<String> npEnv = new ArrayList();
                List<String> nodepoolLinks = new ArrayList();
                List<String> zkLinks = new ArrayList();

                // start zookeeper container
                zkc = startContainer("zookeeper:3.4", zkName,
                        zkEnv, zkLinks, "2181");
                containers.put("Zookeeper", zkc);

////                 Get zookeeper port
//                HostConfig zkHostConfig = zkc.hostConfig();
//                NetworkSettings networkSettings = zkc.networkSettings();
//                Map<String, List<PortBinding>> portBindings = networkSettings.ports();
//                List<PortBinding> zkClientPortBindings = portBindings.get("2181/tcp");
//                PortBinding zkClientPortBinding = zkClientPortBindings.get(0);
//                LOG.severe(zkClientPortBinding.toString());
//                Integer zkPort = Integer.parseInt(zkClientPortBinding.hostPort());
                // Add zookeeper port to nodepool container env and start it
                //npEnv.add("ZKPORT=" + zkPort.toString());
                //npEnv.forEach((s) -> {
                //    LOG.log(Level.SEVERE, "npenv: {0}", s);
                //});
                npEnv.add(MessageFormat.format("ZK_NAME={0}", zkName));
                nodepoolLinks.add(zkName);
                npc = startContainer("hughsaunders/nodepoolrulejunit",
                        npName, npEnv, nodepoolLinks, "9999");
                containers.put("Nodepool", npc);

                // connect to zookeeper
                InspectContainerResponse zkInfo = docker.inspectContainerCmd(zkc.getId()).exec();

                Map<ExposedPort, Ports.Binding[]> zkBindings = zkInfo.getNetworkSettings().getPorts().getBindings();
                String zkClientHostPort = null;
                for (ExposedPort ep : zkBindings.keySet()) {
                    LOG.info(MessageFormat.format("checking zk exposed port {0}", ep.getPort()));
                    if (ep.getPort() != 2181) {
                        continue;
                    }
                    LOG.info("Found zookeeper port binding");
                    Ports.Binding[] bindings = zkBindings.get(ep);
                    for (Ports.Binding binding : bindings) {
                        zkClientHostPort = binding.getHostPortSpec();
                        break;
                    }

                }
                if (zkClientHostPort == null) {
                    throw new IllegalStateException("Failed to find the zookeeper port on the docker host");
                }
                conn = CuratorFrameworkFactory.builder()
                        .connectString("127.0.0.1"
                                + ":" + zkClientHostPort)
                        .namespace("nodepool")
                        .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                        .build();
                conn.start();
                try {
                    base.evaluate();
                } finally {
                    conn.close();

                    CreateContainerResponse c;
                    for (String name : containers.keySet()) {

                        c = containers.get(name);
//                        LOG.info(MessageFormat.format("---- {0} Container Output----", name));
//                        docker.logContainerCmd(c.getId())
//                                .withStdOut(Boolean.TRUE)
//                                .withStdErr(Boolean.TRUE)
//                                .withTailAll()
//                                .exec(new logCallBack(name))
//                                .awaitCompletion();
                        docker.stopContainerCmd(c.getId()).exec();
                        docker.removeContainerCmd(c.getId()).exec();
                    }

                }

            }

        };
    }

}
