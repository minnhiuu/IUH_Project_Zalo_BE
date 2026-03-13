package com.bondhub.common.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class DateUtil {

    private DateUtil() {
        throw new IllegalStateException("Utility class");
    }

    /**
     * Converts ZonedDateTime to LocalDateTime using Vietnam timezone (Asia/Ho_Chi_Minh, UTC+7).
     * This ensures consistent behavior with the application's default timezone setting.
     * 
     * Why Asia/Ho_Chi_Minh?
     * - Database (MongoDB) stores LocalDateTime without timezone info
     * - Spring Data MongoDB audit saves in JVM's default timezone (Asia/Ho_Chi_Minh)
     * - By standardizing on Vietnam timezone, all stored dates are in local time
     * 
     * @param zonedDateTime The ZonedDateTime to convert (can be in any timezone)
     * @return LocalDateTime in Vietnam timezone, or null if input is null
     */
    public static LocalDateTime toLocalDateTime(ZonedDateTime zonedDateTime) {
        if (zonedDateTime == null) {
            return null;
        }
        // Convert to Vietnam timezone to ensure consistent storage
        return zonedDateTime.withZoneSameInstant(ZoneId.of("Asia/Ho_Chi_Minh")).toLocalDateTime();
    }

    /**
     * Converts LocalDateTime (assumed to be in Vietnam timezone) to Instant.
     * Use this when you need to convert stored LocalDateTime to Instant for calculations.
     * 
     * @param localDateTime The LocalDateTime in Vietnam timezone
     * @return Instant representing the same moment in time
     */
    public static Instant toInstant(LocalDateTime localDateTime) {
        if (localDateTime == null) {
            return null;
        }
        return localDateTime.atZone(ZoneId.of("Asia/Ho_Chi_Minh")).toInstant();
    }

    /**
     * Converts Instant to LocalDateTime in Vietnam timezone.
     * 
     * @param instant The instant to convert
     * @return LocalDateTime in Vietnam timezone
     */
    public static LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
