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

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.Label;
import hudson.slaves.NodeProvisioner;
import java.util.Collection;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 *
 * @author hughsaunders
 */
public class NodePoolCloud extends hudson.slaves.Cloud {

    private String connectionString;

    @DataBoundConstructor
    public NodePoolCloud(String name,
	    String connectionString) {
        super(name);
	this.connectionString = connectionString;
    }
    
    public String getConnectionString(){
	    return connectionString;
    }
    public String getName(){
	    return name;
    }

    @Override
    public Collection<NodeProvisioner.PlannedNode> provision(Label label, int i) {
        // returns a list of planned nodes
        // generate Future<Node> object
        // box the future node in NodeProvisioner.PlannedNode
        // return list of PlannedNode
        
    }

    @Override
    public boolean canProvision(Label label) {
        // TODO: Work our how to query the labels nodepool supports
        return true;
        
    }
    
    @Extension
    public static final class DescriptorImpl extends Descriptor<hudson.slaves.Cloud> {
	public DescriptorImpl(){
		load();
	}
	@Override	
	public String getDisplayName(){
		return "Nodepool Cloud";
	}
	
    }
    
}
