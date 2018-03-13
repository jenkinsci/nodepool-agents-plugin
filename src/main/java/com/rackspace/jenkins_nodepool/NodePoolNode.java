package com.rackspace.jenkins_nodepool;

import com.google.gson.Gson;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.curator.framework.CuratorFramework;

/**
 * Representation of a node from NodePool (not necessarily a Jenkins slave)
 */
public class NodePoolNode {

    private final KazooLock lock;

    private final Map data;
    private final String connectionString;
    private final String path;
    private static final Gson gson = new Gson();
    private static final Charset charset = Charset.forName("UTF-8");
    private final String id;


    public NodePoolNode(String connectionString, String id) throws Exception {
        data = new HashMap();
        this.connectionString = connectionString;
        this.path = MessageFormat.format("/nodes/{0}", id);
        this.id = id;
        this.lock = new KazooLock(connectionString, getLockPath());
        this.updateFromZK();
    }

    public void updateFromMap(Map data) {
        this.data.putAll(data);
    }

    CuratorFramework getConnection() {
        return ZooKeeperClient.getConnection(connectionString);
    }


    public void updateFromZK() throws Exception {
        byte[] bytes = getConnection().getData().forPath(this.path);
        String jsonString = new String(bytes, charset);
        final Map zkData = gson.fromJson(jsonString, HashMap.class);
        updateFromMap(zkData);
    }

    public void writeToZK() throws Exception {
        //TODO: Implement this.
        getConnection().setData().forPath(this.path, gson.toJson(data).getBytes(charset));
    }

    public String getNPType() {
        return (String) data.get("type");
    }

    final String getLockPath() {
        return MessageFormat.format("/nodes/{0}/lock", id);
    }

    public String getJenkinsLabel() {
        return MessageFormat.format("nodepool-{0}", getNPType());
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return MessageFormat.format("{0}-{1}", getJenkinsLabel(), getId());
    }

    private KazooLock getLock() {
        return lock;
    }

    public String getHost() {
        return (String) data.get("interface_ip");
    }

    public Integer getPort() {
        return ((Double) data.get("connection_port")).intValue();
    }

    public String getHostKey() {
        List<String> hostKeys = (List) data.get("host_keys");
        return hostKeys.get(0);
    }

    public List<String> getHostKeys() {
        return (List) data.get("host_keys");
    }

    public Map getData() {
        return data;
    }

    @Override
    public String toString() {
        return getName();
    }

    public void setInUse() throws Exception {
        updateFromZK();
        data.put("state", "in-use");
        writeToZK();
        lock.acquire();
    }

    public void release() throws Exception {
        // get up to date info, so we are less likely to lose data
        // when writing back.
        updateFromZK();
        data.put("state", "used");
        writeToZK();
        lock.release();
    }

}
