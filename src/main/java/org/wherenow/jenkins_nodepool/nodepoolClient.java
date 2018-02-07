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

import java.util.Dictionary;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

/**
 *
 * @author hughsaunders
 */
public class nodepoolClient {

	private CuratorFramework conn;  
	private String requestRoot;
	private String nodeRoot;

	public nodepoolClient(String connectionString) {
		this(connectionString, "nodepool");
	}

	public nodepoolClient(String connectionString, String zkNamespace, String requestRoot, String nodeRoot) {
		this.requestRoot = requestRoot;
		this.nodeRoot = nodeRoot;
		ExponentialBackoffRetry retryPolicy = new ExponentialBackoffRetry(1000,3);
		this.conn = CuratorFrameworkFactory.builder()
			.connectString(connectionString)
			.namespace(zkNamespace)
			.retryPolicy(retryPolicy)
			.build();
	}


	public NodeRequest requestNode(Integer priority, byte[] data) throws Exception{
		String path = "{0}/{1}-".format(this.requestRoot, priority.toString());
		path = conn.create()
			.withProtection()
			.withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
			.forPath(path, data);
		
		
	}


	
		
}
