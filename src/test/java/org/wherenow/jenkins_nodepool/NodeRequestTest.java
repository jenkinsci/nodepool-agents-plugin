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

import com.google.gson.Gson;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.logging.Logger;
import org.apache.curator.framework.CuratorFramework;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.jvnet.tiger_types.Types;

/**
 *
 * @author hughsaunders
 */
public class NodeRequestTest {

    private static final Logger LOG = Logger.getLogger(NodeRequestTest.class.getName());
    static Gson gson;
    private String label = "testlabel";
    private CuratorFramework conn;
    private ZooKeeperClient zkc;

    @ClassRule
    public static NodePoolRule npr = new NodePoolRule();

    @BeforeClass
    public static void setUpClass() {
        gson = new Gson();

    }

    @Before
    public void setUp() throws Exception {
        conn = npr.getCuratorConnection();

        
        //Temp code for debugging the tiger-types conflict
        
        // Example using HTMLEmail from Apache Commons Email 
        Class theClass = Types.class;

        // Find the path of the compiled class 
        String classPath = theClass.getResource(theClass.getSimpleName() + ".class").toString();
        LOG.severe("Class: " + classPath);

        // Find the path of the lib which includes the class 
        String libPath = classPath.substring(0, classPath.lastIndexOf("!"));
        LOG.severe("Lib:   " + libPath);

        // Find the path of the file inside the lib jar 
        String filePath = libPath + "!/META-INF/MANIFEST.MF";
        LOG.severe("File:  " + filePath);

        // We look at the manifest file, getting two attributes out of it 
        Manifest manifest = new Manifest(new URL(filePath).openStream());
        Attributes attr = manifest.getMainAttributes();
        LOG.severe("Manifest-Version: " + attr.getValue("Manifest-Version"));
        LOG.severe("Implementation-Version: " + attr.getValue("Implementation-Version"));
    }

    @Test
    public void TestSerialisation() {
        NodeRequest nr = new NodeRequest(conn, label);
        String json = nr.toString();

        LOG.fine("TestSerialisation json string: " + json);

        // ensure the json is valid by deserialising it
        Map data = gson.fromJson(json, HashMap.class);

        // Check a couple of key value pairs are as expected
        assertEquals((String) data.get("state"), "requested");
        assertEquals(((List) data.get("node_types")).get(0), label);
    }

    @Test
    public void TestDeserialisation() {
        String[] keys = {"node_types", "requestor", "state", "state_time"};
        NodeRequest nr = new NodeRequest(conn, label);
        String json = nr.toString();
        NodeRequest nr2 = NodeRequest.fromJson(conn, json);
        LOG.info("nr: " + nr);
        LOG.info("nr2: " + nr2);
        for (String key : keys) {
            LOG.info("key compare: " + key);
            assertEquals(nr.get(key), nr2.get(key));
        }
        assertEquals(nr, nr2);
        assertTrue(nr.equals(nr2));
    }

}
