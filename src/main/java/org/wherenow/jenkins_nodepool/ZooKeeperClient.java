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

import java.text.MessageFormat;
import org.apache.curator.RetryPolicy;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

/**
 *
 * @author hughsaunders
 */
public class ZooKeeperClient{

	private CuratorFramework conn;  
	private String requestRoot;
	private String nodeRoot;
	private String connectionString;
	private String zkNamespace;
	private RetryPolicy retryPolicy;	
        
        
        public static CuratorFramework createConnection(String connectionString){
            ZooKeeperClient zkc = new ZooKeeperClient(connectionString);
            zkc.connect();
            return zkc.getConnection();
        }
	

	public ZooKeeperClient(String connectionString) {
		this(connectionString, 
			"nodepool",
			new ExponentialBackoffRetry(1000,3));
	}

	public ZooKeeperClient(String connectionString, String zkNamespace, 
                RetryPolicy retryPolicy) {
		this.connectionString = connectionString;
		this.zkNamespace = zkNamespace;
		this.retryPolicy = retryPolicy;
	}

	public void connect(){
		conn = CuratorFrameworkFactory.builder()
		.connectString(connectionString)
		.namespace(zkNamespace)
		.retryPolicy(retryPolicy)
		.build();
		conn.start();
	}
	
	public void disconnect(){
		conn.close();	
	}
        
        public CuratorFramework getConnection(){
            if (conn == null){
                throw new IllegalStateException("Attempt to get connection before calling connect");
            }
            return conn;
        }



	
		
}
