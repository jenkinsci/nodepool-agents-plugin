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
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;

/**
 * Base class for zookeeper proxy objects.
 *
 * @author Rackspace
 */
public abstract class ZooKeeperObject {
    private static final Logger LOGGER = Logger.getLogger(ZooKeeperObject.class.getName());
    static final Gson GSON = new Gson();
    static final Charset CHARSET = Charset.forName("UTF-8");

    final Map data;
    final String connectionString;
    String path;
    String zKID;

    public ZooKeeperObject(String connectionString) {
        data = new HashMap();
        this.connectionString = connectionString;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getZKID() {
        return zKID;
    }

    public void setZKID(String zKID) {
        this.zKID = zKID;
    }

    public void updateFromMap(Map data) {
        this.data.putAll(data);
    }

    CuratorFramework getConnection() {
        return ZooKeeperClient.getConnection(connectionString);
    }

    public final void updateFromZK() throws Exception {
        byte[] bytes = getConnection().getData().forPath(this.path);
        String jsonString = new String(bytes, CHARSET);
        final Map zkData = GSON.fromJson(jsonString, HashMap.class);
        updateFromMap(zkData);
    }

    public void writeToZK() throws Exception {
        getConnection().setData().forPath(this.path, GSON.toJson(data).getBytes(CHARSET));
    }

}
