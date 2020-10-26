package com.community.bitcoinwallet.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public class WalletEntryUtils {
    private WalletEntryUtils() {
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

    public static BigDecimal toBigDecimal(double amount){
        return BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP);
    }


}
