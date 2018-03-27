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

import hudson.model.Queue.Task;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.curator.test.TestingServer;
import static org.mockito.Mockito.*;

/**
 *
 * @author hughsaunders
 */
public class Mocks {

    TestingServer zkTestServer;
    String connectionString;
    CuratorFramework conn;
    NodePool np;
    Task task;
    Charset charset;
    String zkNamespace;


    public Mocks() {
        zkNamespace = "unittest";
        charset = Charset.forName("UTF-8");
        np = mock(NodePool.class);
        task = mock(Task.class);
        startTestServer();
        when(np.getConn()).thenReturn(conn);
        when(np.getRequestor()).thenReturn("unittest");
        when(np.getCharset()).thenReturn(charset);
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
            Logger.getLogger(Mocks.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public void cleanup() {
        try {
            zkTestServer.stop();
        } catch (IOException ex) {
            Logger.getLogger(Mocks.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

}
