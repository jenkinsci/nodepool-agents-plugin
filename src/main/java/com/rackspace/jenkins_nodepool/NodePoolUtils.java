package com.rackspace.jenkins_nodepool;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NodePoolUtils {

    /**
     * A regex pattern for the hold until string value.  Examples of the value include:
     * - 40m for 40 minutes
     * - 1h for one hour
     * - 2d for two days
     * - 4w for four weeks
     * - 3M for three months
     */
    private static final Pattern HOLD_UNTIL_STR_PATTERN = Pattern.compile("([1-9][0-9]?)([mhdwM])");

    /**
     * Private constructor to prevent instantiation.
     */
    private NodePoolUtils() {
    }

    /**
     * Converts the hold until string value to an absolute epoch time in milliseconds.
     *
     * @param initialTimeEpochMs the time instant starting point
     * @param holdUntilValue     the string value for the hold until relative to the initial time
     * @return an absolute epoch time in milliseconds.
     * @throws HoldUntilValueException if the hold until value is malformed or incorrect.
     */
    public static Long covertHoldUtilStringToEpochMs(final long initialTimeEpochMs, String holdUntilValue) throws HoldUntilValueException {
        final Matcher matcher = HOLD_UNTIL_STR_PATTERN.matcher(holdUntilValue);
        if (matcher.matches()) {
            final Instant timeInstant = Instant.ofEpochMilli(initialTimeEpochMs);
            final ZonedDateTime timeInstantUTC = timeInstant.atZone(ZoneOffset.UTC);

            // Captured groups are indexed from left to right, starting at one.  Group zero denotes the entire pattern
            final long number = Long.parseLong(matcher.group(1));
            final String unit = matcher.group(2);

            // Quick sanity check on the number value
            if (number <= 0L) {
                throw new HoldUntilValueException("Value is less than one");
            }
            if (number > 99) {
                throw new HoldUntilValueException("Value is larger than 99");
            }

            switch (unit) {
                case "m": // minutes
                    return timeInstantUTC.plus(Duration.ofMinutes(number)).toInstant().toEpochMilli();
                case "h": // hours
                    return timeInstantUTC.plus(Duration.ofHours(number)).toInstant().toEpochMilli();
                case "d": // days
                    return timeInstantUTC.plus(Duration.ofDays(number)).toInstant().toEpochMilli();
                case "w": // weeks
                    return ChronoUnit.WEEKS.addTo(timeInstantUTC, number).toInstant().toEpochMilli();
                case "M": // months
                    return ChronoUnit.MONTHS.addTo(timeInstantUTC, number).toInstant().toEpochMilli();
                default:
                    throw new HoldUntilValueException(String.format("Unsupported Hold Until unit value: %s", unit));
            }
        } else {
            throw new HoldUntilValueException(String.format("Invalid hold until string value: %s - Expecting values similar to: 40m, 1h, 2d, 3w, 4M", holdUntilValue));
        }
    }

    /**
     * Converts the milliseconds since epoch to a
     *
     * @param epochMs the milliseconds since epoch
     * @param zone    the zone offset - typically UTC
     * @return a formatted date/time string in the specified timezone.
     */
    public static String getFormattedDateTime(final Long epochMs, final ZoneOffset zone) {
        final Instant instant = Instant.ofEpochMilli(epochMs);
        final OffsetDateTime utcInstant = instant.atOffset(zone);
        return DateTimeFormatter.ISO_DATE_TIME.format(utcInstant);
    }
}
