package com.community.bitcoinwallet.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;

public final class DateAndAmountUtils {
    private DateAndAmountUtils() {
    }

    public static Instant atStartOfHour(Instant instant) {
        OffsetDateTime offsetDateTime = instant.atOffset(ZoneOffset.UTC);
        return instant.minusNanos(offsetDateTime.getNano())
            .minusSeconds(offsetDateTime.getSecond())
            .minusSeconds(offsetDateTime.getMinute() * 60);
    }

    public static Instant atEndOfHour(Instant instant) {
        return atStartOfHour(instant).plusSeconds(3600);
    }

    public static ZonedDateTime toUTCZonedDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC);
    }

    public static BigDecimal toBigDecimal(double amount) {
        return BigDecimal.valueOf(amount).setScale(8, RoundingMode.HALF_UP);
    }


}
