package com.rackspace.jenkins_nodepool;

import org.junit.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NodePoolStateTest {
    /**
     * Logger for this class.
     */
    private static final Logger LOG = getLogger(NodePoolStateTest.class.getName());

    private static final Map<String, NodePoolState> testConditions = new HashMap<>();

    public NodePoolStateTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
        // String -> Expected NodePoolState enum value
        testConditions.put("in-use", NodePoolState.IN_USE);
        testConditions.put("in_use", NodePoolState.IN_USE);
        testConditions.put("inuse", NodePoolState.IN_USE);
        testConditions.put("IN-USE", NodePoolState.IN_USE);
        testConditions.put("IN_USE", NodePoolState.IN_USE);
        testConditions.put("INUSE", NodePoolState.IN_USE);
        testConditions.put("In-Use", NodePoolState.IN_USE);
        testConditions.put("In_Use", NodePoolState.IN_USE);
        testConditions.put("InUse", NodePoolState.IN_USE);
        testConditions.put("inUse", NodePoolState.IN_USE);
        testConditions.put("In-use", NodePoolState.IN_USE);
        testConditions.put("in-Use", NodePoolState.IN_USE);
        testConditions.put("in_Use", NodePoolState.IN_USE);

        testConditions.put("aborted", NodePoolState.ABORTED);
        testConditions.put("failed", NodePoolState.FAILED);
        testConditions.put("building", NodePoolState.BUILDING);
        testConditions.put("deleting", NodePoolState.DELETING);
        testConditions.put("fulfilled", NodePoolState.FULFILLED);
        testConditions.put("hold", NodePoolState.HOLD);
        testConditions.put("init", NodePoolState.INIT);
        testConditions.put("pending", NodePoolState.PENDING);
        testConditions.put("ready", NodePoolState.READY);
        testConditions.put("requested", NodePoolState.REQUESTED);
        testConditions.put("testing", NodePoolState.TESTING);
        testConditions.put("uploading", NodePoolState.UPLOADING);
        testConditions.put("used", NodePoolState.USED);
    }

    @AfterClass
    public static void tearDownClass() throws IOException {
    }

    @Before
    public void before() {
    }

    @After
    public void after() {
    }

    @Test
    public void testNodePoolStateFromStringGood() {
        for (String key : testConditions.keySet()) {
            assertEquals("NodePoolState fromString() - " + key, testConditions.get(key), NodePoolState.fromString(key));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNodePoolStateFromStringBad1() {
        final String badString = "blah";
       NodePoolState.fromString(badString);
       fail("Should have received an IllegalArgumentException from invalid string: " + badString);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNodePoolStateFromStringBad2() {
        final String badString = "Hugh Saunders is a steely-eyed missile man";
        NodePoolState.fromString(badString);
        fail("Should have received an IllegalArgumentException from invalid string: " + badString);
    }
}
