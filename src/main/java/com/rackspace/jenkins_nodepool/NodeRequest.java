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

import static com.rackspace.jenkins_nodepool.NodePoolClient.idForPath;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.zookeeper.CreateMode;

/**
 * Represents a nodepool node request. Data format is JSON dump of following
 * dict structure: --- node_types: - label1 - label2 requestor: string id (eg
 * hostname) state: string requested|pending|fulfilled|failed state_time: float
 * seconds since epoch
 *
 * @author hughsaunders
 */
public class NodeRequest extends ZooKeeperObject {

    //TODO: check requests-lock znodes, they seem to be stacking up.
    // what creates them and what should clean them up?
    private static final Logger LOGGER = Logger.getLogger(NodeRequest.class.getName());

    public NodeRequest(String connectionString, String label, String jenkinsLabel) throws Exception {
        this(connectionString, Arrays.asList(new String[]{label}), jenkinsLabel);
    }

    @SuppressFBWarnings
    public NodeRequest(String connectionString, List<String> labels, String jenkinsLabel) throws Exception {
        super(connectionString);
        data.put("node_types", new ArrayList(labels));
        data.put("requestor", config.getRequestor());
        data.put("state", RequestState.requested);
        data.put("state_time", new Double(System.currentTimeMillis() / 1000));
        data.put("jenkins_label", jenkinsLabel);
        // sets path and zkid
        createZNode();
    }

    private void createZNode() throws Exception {
        final String createPath = MessageFormat.format("/{0}/{1}-",
                config.getRequestRoot(), config.getPriority());
        LOGGER.finest(MessageFormat.format("Creating request node: {0}",
                createPath));
        String requestPath = getConnection().create()
                .creatingParentsIfNeeded()
                .withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
                .forPath(createPath, getJson().getBytes());

        LOGGER.log(Level.FINEST, "Requeste created at path: {0}", requestPath);

        setZKID(idForPath(requestPath));
        setPath(requestPath);
    }

    public RequestState getState() {
        return (RequestState) data.get("state");
    }

    public List<NodePoolNode> getAllocatedNodes() throws Exception {
        // Example fulfilled request
        // {"nodes": ["0000000000"], "node_types": ["debian"], "state": "fulfilled", "declined_by": [], "state_time": 1520849225.4513698, "reuse": false, "requestor": "NodePool:min-ready"}
        if (data.get("state") != RequestState.fulfilled) {
            throw new IllegalStateException("Attempt to get allocated nodes from a node request before it has been fulfilled");
        }
        List<NodePoolNode> nodeObjects = new ArrayList();
        for (Object id : (List) data.get("nodes")) {
            nodeObjects.add(new NodePoolNode(connectionString, (String) id));
        }

        return nodeObjects;
    }

    @Override
    public void updateFromMap(Map newData) {
        super.updateFromMap(newData);
        // convert state time from string
        final Double stateTime = (Double) newData.get("state_time");
        data.put("state_time", stateTime);

        // convert 'state' back into its corresponding enum value
        final String stateString = (String) newData.get("state");
        data.put("state", RequestState.valueOf(stateString));
    }

    String getNodePoolLabel() {
        final List<String> labels = (List<String>) data.get("node_types");
        return labels.get(0);
    }

    String getJenkinsLabel() {
        return (String) data.get("jenkins_label");
    }
}
