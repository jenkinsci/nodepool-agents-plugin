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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 *
 * @author hughsaunders
 */
public class ZooKeeperClient {

    private CuratorFramework conn;
    private String requestRoot;
    private String nodeRoot;
    private String connectionString;
    private String zkNamespace;
    private RetryPolicy retryPolicy;

    // Store connections in a map, so everytime a connection with the
    // same connectionString is requested, the same connection handle
    // will be returned.
    private static Map<String, ZooKeeperClient> instances = new HashMap();

    public static ZooKeeperClient getClient(String connectionString) {
        if (ZooKeeperClient.instances.containsKey(connectionString)) {
            return ZooKeeperClient.instances.get(connectionString);
        } else {
            ZooKeeperClient zkc = new ZooKeeperClient(connectionString);
            ZooKeeperClient.instances.put(connectionString, zkc);
            return zkc;
        }
    }

    public static List<String> getConnectionStrings() {
        return new ArrayList(ZooKeeperClient.instances.keySet());
    }

    public static CuratorFramework getConnection(String connectionString) {
        return ZooKeeperClient.getClient(connectionString).conn;
    }

    public ZooKeeperClient(String connectionString) {
        this(connectionString,
                "nodepool",
                new ExponentialBackoffRetry(1000, 3));
    }

    public ZooKeeperClient(String connectionString, String zkNamespace,
            RetryPolicy retryPolicy) {
        this.connectionString = connectionString;
        this.zkNamespace = zkNamespace;
        this.retryPolicy = retryPolicy;

        conn = CuratorFrameworkFactory.builder()
                .connectString(connectionString)
                .namespace(zkNamespace)
                .retryPolicy(retryPolicy)
                .build();
        conn.start();
    }

    public CuratorFramework getConnection() {
        return ZooKeeperClient.getConnection(connectionString);
    }

    public void disconnect() {
        conn.close();
    }

    public String getRequestRoot() {
        return requestRoot;
    }

    public String getNodeRoot() {
        return nodeRoot;
    }

    public String getConnectionString() {
        return connectionString;
    }

    public String getZkNamespace() {
        return zkNamespace;
    }

    public RetryPolicy getRetryPolicy() {
        return retryPolicy;
    }

}
