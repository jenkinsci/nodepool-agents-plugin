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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.rackspace.jenkins_nodepool.models.NodeRequestModel;
import org.apache.zookeeper.CreateMode;
import org.junit.*;

import java.util.List;
import java.util.Map;

import static java.lang.String.format;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author hughsaunders
 */
public class ZooKeeperObjectTest {

    private Mocks m;
    private ZooKeeperObject<NodeRequestModel> zko;

    public ZooKeeperObjectTest() {
    }

    @BeforeClass
    public static void setUpClass() {
    }

    @AfterClass
    public static void tearDownClass() {
    }

    @Before
    public void setUp() {
        m = new Mocks();
        // Create an instance of the ZK object wrapper - path is relative to the ZK connection namespace (typically: /nodepool)
        // Create a dummy ZK object for a model - model isn't important
        zko = new ZooKeeperObject<>(format("/%s/%s-", m.np.getRequestRoot(), 100), m.npID, m.np.getConn(), NodeRequestModel.class);
    }

    @After
    public void tearDown() {
        m.cleanup();
    }

    @Test
    public void testRequestModel() {
        // Create a request model
        final NodeRequestModel model = new NodeRequestModel();
        model.setState(NodePoolState.INIT);
        model.setRequestor("my jenkins");
        model.setState_time(new Double(System.currentTimeMillis() / 1000.0d));
        model.setReuse(false);
        model.setBuild_id("44");

        try {
            // Save it
            final String newPath = zko.save(model, CreateMode.EPHEMERAL_SEQUENTIAL);
            // Load it back
            final NodeRequestModel loadedModel = zko.load();
            // Check the values
            assertEquals("BuildID should be: " + model.getBuild_id(), model.getBuild_id(), loadedModel.getBuild_id());
            assertEquals("State should be: " + model.getState(), model.getState(), loadedModel.getState());
            assertEquals("Requestor should be: " + model.getRequestor(), model.getRequestor(), loadedModel.getRequestor());
            assertEquals("Path should be: " + zko.getPath(), zko.getPath(), newPath);
            assertEquals("ZK ID Should be: " + zko.getZKID(), zko.getZKID(), zko.idFromPath(newPath));
        } catch (ZookeeperException | NodePoolException e) {
            fail("Saving Request Model failed. Message: " + e.getMessage());
        }
    }

    @Test
    public void testIdsFromPath() {
        // Valid Examples: path -> ID
        final Map<String, String> mapValidPathIDs = ImmutableMap.of(
                "/some/node/path/nodepool-0000000001", "0000000001",
                "/some/other/path/nodepool-master-bionic-pubcloud-iad-0082170008", "0082170008",
                "/some/other/path/-1747766601", "1747766601"
        );

        for (final String path : mapValidPathIDs.keySet()) {
            try {
                assertEquals("Testing Valid Path: " + path, mapValidPathIDs.get(path), zko.idFromPath(path));
            } catch (NodePoolException e) {
                fail(e.getMessage());
            }
        }
    }

    @Test
    public void testInvalidIdsFromPath() {
        // Invalid examples: invalid path where we can't extract the id
        final List<String> mapInValidPathIDs = ImmutableList.of(
                "/some/node/path/nodepool0000000001",
                "",
                "/",
                "//",
                "/-",
                "some/path/without/leading/slash",
                "/--",
                "/some/node-0000000d1", // no characters allowed in numeric id portion
                "/some/node-d00000051", // no characters allowed in numeric id portion
                "/some/node-000000051c", // no characters allowed in numeric id portion
                "/some/node-0000!0051", // no characters allowed in numeric id portion
                "/some/node-0000~0051", // no characters allowed in numeric id portion
                "/some/node-0000&0051", // no characters allowed in numeric id portion
                "/some/node-0000*0051", // no characters allowed in numeric id portion
                "/some/node-0$7000051", // no characters allowed in numeric id portion
                "/some/node-0%7000051", // no characters allowed in numeric id portion
                "/some/node-0_7000051", // no characters allowed in numeric id portion
                "/some/node-0+7000051", // no characters allowed in numeric id portion
                "/some/node-00700005=", // no characters allowed in numeric id portion
                "/some/node-007000@50", // no characters allowed in numeric id portion
                "/some/node-0#7000050" // no characters allowed in numeric id portion
        );

        // Make sure all the invalid paths throw an exception
        for (final String path : mapInValidPathIDs) {
            try {
                final String id = zko.idFromPath(path);
                fail(format("Expected to throw Exception with invalid path: %s but extracted id: %s", path, id));
            } catch (NodePoolException e) {
                // Ignore as this was expected with bad input
            }
        }
    }
}
