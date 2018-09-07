package com.rackspace.jenkins_nodepool;

import org.junit.*;

import java.io.IOException;
import java.time.ZoneOffset;
import java.util.logging.Logger;

import static java.util.logging.Logger.getLogger;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NodePoolUtilsTest {
    // 2018-09-10T20:52:30.317Z in milliseconds since epoch
    private static final long someTimeValue = 1536612750317L;

    /**
     * Logger for this class.
     */
    private static final Logger LOG = getLogger(NodePoolUtilsTest.class.getName());

    public NodePoolUtilsTest() {
    }

    @BeforeClass
    public static void setUpClass() throws Exception {
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
    public void testCovertHoldUtilStringToEpochMs15m() {
        final String holdUntilValue = "15m";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 15 minutes",
                    someTimeValue + 1000L * 60L * 15L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s", holdUntilValue));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs1m() {
        final String holdUntilValue = "40m";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 40 minutes",
                    someTimeValue + 1000L * 60L * 40L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s", holdUntilValue));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs1h() {
        final String holdUntilValue = "1h";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 1 hour",
                    someTimeValue + 1000L * 60L * 60L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s", holdUntilValue));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs3h() {
        final String holdUntilValue = "3h";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 3 hours",
                    someTimeValue + 1000L * 60L * 60L * 3L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs99h() {
        final String holdUntilValue = "99h";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 99 hours",
                    someTimeValue + 1000L * 60L * 60L * 99L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }
    @Test
    public void testCovertHoldUtilStringToEpochMs1d() {
        final String holdUntilValue = "1d";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 1 day",
                    someTimeValue + 1000L * 60L * 60L * 24L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs5d() {
        final String holdUntilValue = "5d";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 5 days",
                    someTimeValue + 1000L * 60L * 60L * 24L * 5L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs1w() {
        final String holdUntilValue = "1w";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 1 week",
                    someTimeValue + 1000L * 60L * 60L * 24L * 7L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs1M() {
        final String holdUntilValue = "1M";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            assertEquals("Hold Until Time - 1 month",
                    someTimeValue + 1000L * 60L * 60L * 24L * 30L,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test
    public void testCovertHoldUtilStringToEpochMs3M() {
        // Sometime on September 10, 2018
        final long someTimeValue = 1536612750317L;
        final String holdUntilValue = "3M";
        try {
            final long holdUntilEpochMs = NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
            // Sept 10 (30 days) -> Oct 10 (31 days) -> November 10 (30 days) -> December 10
            final long oneDay = 1000L * 60L * 60L * 24L;
            assertEquals("Hold Until Time - 3 months",
                    someTimeValue + oneDay * 30L * 3 + oneDay,
                    holdUntilEpochMs);
        } catch (HoldUntilValueException e) {
            fail(String.format("HoldUntilValueException with input: %s. Message: %s", holdUntilValue, e.getLocalizedMessage()));
        }
    }

    @Test(expected = HoldUntilValueException.class)
    public void testCovertHoldUtilStringToEpochMsInvalidUnitZ() throws HoldUntilValueException {
        final long someTimeValue = 1536612750317L;
        final String holdUntilValue = "1z";
        // Should throw an exception
        NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
        fail(String.format("Expected HoldUntilValueException due to invalid unit: %s", holdUntilValue));
    }

    @Test(expected = HoldUntilValueException.class)
    public void testCovertHoldUtilStringToEpochMsInvalidUnitDW() throws HoldUntilValueException {
        final long someTimeValue = 1536612750317L;
        final String holdUntilValue = "1dw";
        // Should throw an exception
        NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
        fail(String.format("Expected HoldUntilValueException due to invalid unit: %s", holdUntilValue));
    }

    @Test(expected = HoldUntilValueException.class)
    public void testCovertHoldUtilStringToEpochMsInvalidNumericValueMinusOne() throws HoldUntilValueException {
        final long someTimeValue = 1536612750317L;
        final String holdUntilValue = "-1h";
        // Should throw an exception
        NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
        fail(String.format("Expected HoldUntilValueException due to invalid unit: %s", holdUntilValue));
    }

    @Test(expected = HoldUntilValueException.class)
    public void testCovertHoldUtilStringToEpochMsInvalidNumericValue100() throws HoldUntilValueException {
        final long someTimeValue = 1536612750317L;
        final String holdUntilValue = "100h";
        // Should throw an exception
        NodePoolUtils.covertHoldUtilStringToEpochMs(someTimeValue, holdUntilValue);
        fail(String.format("Expected HoldUntilValueException due to invalid unit: %s", holdUntilValue));
    }

    @Test
    public void testGetFormattedTime() {
        // Sometime on September 10, 2018
        final long someTimeValue = 1536612750317L;
        final String expected = "2018-09-10T20:52:30.317Z";
        final String dateTimeString = NodePoolUtils.getFormattedDateTime(someTimeValue, ZoneOffset.UTC);
        assertEquals("Formatted Time", expected, dateTimeString);
    }
}
