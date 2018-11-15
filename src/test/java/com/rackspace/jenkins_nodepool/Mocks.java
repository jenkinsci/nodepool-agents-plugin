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

import com.google.gson.Gson;
import com.rackspace.jenkins_nodepool.models.NodeModel;
import hudson.model.Label;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.labels.LabelAtom;

import java.io.IOException;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStepExecution.PlaceholderTask;

import static java.lang.String.format;
import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.*;

/**
 * This class contains all the fixtures and mocks for unit testing.
 */
public class Mocks {

    /**
     * Logger for this class.
     */
    private static final Logger LOG = getLogger(Mocks.class.getName());

    Charset charset;
    CuratorFramework conn;
    String connectionString;
    String credentialsID;
    String host;
    String jsonString;
    String key;
    Label label;
    String labelPrefix;
    String nodeRoot;
    NodePool np;
    String npID;
    String npLabel;
    String npcName;
    NodePoolNode npn;
    NodePoolSlave nps;
    NodePoolComputer npc;
    Integer port;
    String priority;
    Queue.Item queueItem;
    String requestRoot;
    String requestor;
    PlaceholderTask task;
    String value;
    String zkNamespace;
    TestingServer zkTestServer;
    NodeRequest nr;
    List<NodePoolNode> allocatedNodes;
    Integer requestTimeout;
    Integer installTimeout;
    Integer maxAttempts;
    String holdUntilRoot;
    String jdkInstallationScript;
    String jdkHome;
    NodePoolJob npj;
    FlowNode fn;
    FlowExecution fe;
    FlowExecutionOwner feo;
    WorkflowRun run;
    Long qID = 42L;
    WorkflowJob job;
    List<Attempt> attemptListFailure;
    Attempt attemptFailure;
    List<Attempt> attemptListSuccess;
    Attempt attemptSuccess;


    public Mocks() {
        requestTimeout = 30;
        installTimeout = 60;
        maxAttempts = 3;
        requestor = "unittests";
        priority = "001";
        labelPrefix = "nodepool-";
        value = "a map value";
        key = "map key";
        npID = "000000001";
        jsonString = MessageFormat.format("'{'\"{0}\":\"{1}\"'}'", key, value);
        zkNamespace = "unittest";
        nodeRoot = "nodes";
        requestRoot = "requests";
        charset = Charset.forName("UTF-8");
        npLabel = "debian";
        host = "host";
        port = 22;
        credentialsID = "somecreds";
        label = new LabelAtom(MessageFormat.format("{0}{1}", labelPrefix, npLabel));
        npcName = MessageFormat.format("{0}-{1}", label.getDisplayName(), npID);
        np = mock(NodePool.class, withSettings().serializable());
        task = mock(PlaceholderTask.class);
        //queueItem = mock(Queue.Item.class);
        npn = mock(NodePoolNode.class);
        nps = mock(NodePoolSlave.class);
        nr = mock(NodeRequest.class);
        npc = mock(NodePoolComputer.class);
        jdkInstallationScript = "apt-get update && apt-get install openjdk-8-jre-headless -y";
        jdkHome = "/usr/lib/jvm/java-8-openjdk-amd64";
        npj = mock(NodePoolJob.class);
        fn = mock(FlowNode.class);
        fe = mock(FlowExecution.class);
        feo = mock(FlowExecutionOwner.class);
        run = mock(WorkflowRun.class);
        job = mock(WorkflowJob.class);
        attemptListSuccess = new ArrayList<>();
        attemptSuccess = mock(Attempt.class);
        attemptListFailure = new ArrayList<>();
        attemptFailure = mock(Attempt.class);

        attemptListSuccess.add(attemptSuccess);
        attemptListFailure.add(attemptFailure);
        allocatedNodes = new ArrayList<>();
        allocatedNodes.add(npn);

        startTestServer();
        when(npn.getName()).thenReturn(npcName);
        when(npn.getHostKey()).thenReturn("ahostkey");
        // final, can't be mocked: when(nps.toComputer()).thenReturn(npc);

        // commented so spy nodepool can be returned in NodePoolTest
        //when(npn.getNodePool()).thenReturn(np);
        when(nps.getNodePoolNode()).thenReturn(npn);
        when(nps.getNumExecutors()).thenReturn(1);
        when(nps.getLabelString()).thenReturn(label.getDisplayName());
        when(nps.getNodeName()).thenReturn(npcName);
        when(nps.getNodePoolJob()).thenReturn(npj);
        //when(queueItem.getAssignedLabel()).thenReturn(label);
        //when(queueItem.getId()).thenReturn(42L);

        when(np.getConn()).thenReturn(conn);
        when(np.getRequestor()).thenReturn(requestor);
        when(np.getCharset()).thenReturn(charset);
        when(np.getLabelPrefix()).thenReturn(labelPrefix);
        when(np.getRequestRoot()).thenReturn(requestRoot);
        when(np.getNodeRoot()).thenReturn(nodeRoot);
        when(np.nodePoolLabelFromJenkinsLabel(label.getDisplayName())).thenReturn(npLabel);
        when(task.getAssignedLabel()).thenReturn(label);
        when(task.getName()).thenReturn("Task 1");
        try {
            when(task.getNode()).thenReturn(fn);
            when(feo.getExecutable()).thenReturn(run);
        } catch (IOException | InterruptedException ex) {
            fail(format("%s setting up Mocks node and flow execution owner executable. Message: %s",
                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
        when(fn.getExecution()).thenReturn(fe);
        when(fe.getOwner()).thenReturn(feo);
        when(run.getBuildStatusSummary()).thenReturn(new Run.Summary(false, "message"));
        when(run.getParent()).thenReturn(job);
        when(run.getNumber()).thenReturn(22);
        when(run.isBuilding()).thenReturn(true);
        when(job.getDisplayName()).thenReturn("a job");
        when(npj.getTask()).thenReturn(task);
        when(npj.getAttempts()).thenReturn(attemptListSuccess);
        when(npj.getRun()).thenReturn(run);
        when(attemptSuccess.isSuccess()).thenReturn(true);
        when(attemptSuccess.getDurationSeconds()).thenReturn(1L);
        when(attemptFailure.isSuccess()).thenReturn(false);
        when(attemptFailure.getDurationSeconds()).thenReturn(1L);
        try {
            when(nr.getAllocatedNodes()).thenReturn(allocatedNodes);
            when(nr.getState()).thenReturn(NodePoolState.FULFILLED);
        } catch (Exception ex) {
            fail(format("%s setting up Mocks allocated nodes and state. Message: %s",
                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
    }

    void cleanup() {
        try {
            zkTestServer.stop();
        } catch (IOException ex) {
            fail(format("%s stopping zookeeper server in Mocks. Message: %s",
                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
    }

    NodeModel getNodeData(String path) throws Exception {
        byte[] rdata = conn.getData().forPath(path);
        String rString = new String(rdata, charset);
        return new Gson().fromJson(rString, NodeModel.class);
    }

    void writeNodeData(String path, NodeModel data) throws Exception {
        byte[] bdata = new Gson().toJson(data).getBytes(charset);
        if (conn.checkExists().forPath(path) != null) {
            conn.setData().forPath(path, bdata);
        } else {
            conn.create().creatingParentsIfNeeded().forPath(path, bdata);
        }
    }

    private void startTestServer() {
        try {
            zkTestServer = new TestingServer();
            zkTestServer.start();
            connectionString = zkTestServer.getConnectString();
            conn = CuratorFrameworkFactory.builder()
                    .connectString(connectionString)
                    .namespace(zkNamespace)
                    .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                    .build();
            conn.start();
        } catch (Exception ex) {
            fail(format("%s starting zookeeper server in Mocks. Message: %s",
                    ex.getClass().getSimpleName(), ex.getLocalizedMessage()));
        }
    }

}
