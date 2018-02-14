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

import org.apache.zookeeper.CreateMode;

/**
 * 
 * Single use node life cycle:
 * request
 * accept
 *  lock
 * use
 * return
 *  unlock
 * 
 * lock (kazoo.recipe.lock)
 *  noderoot/nodeid/lock
 *
 * @author hughsaunders
 */
public class NodePoolClient {
        
    	public NodeRequest requestNode(Integer priority, byte[] data) throws Exception{
		String path = "{0}/{1}-".format(this.requestRoot, priority.toString());
		path = conn.create()
			.withProtection()
			.withMode(CreateMode.EPHEMERAL_SEQUENTIAL)
			.forPath(path, data);
		//TODO:create proper constructor for node request and pass it some useful information
		return new NodeRequest("testlabel");
	}
        
}
