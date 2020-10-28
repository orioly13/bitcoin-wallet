package com.community.bitcoinwallet.util;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

class DateAndAmountUtilsTest {

    private static final long NANOS_IN_ONE_HOUR = 3600 * 1_000_000L;
    private static final int RETRIES_COUNT = 10_000;

    @Test
    public void shouldSwitchInstantToStartOfHour() {
        Instant startOfHour = Instant.parse("2020-10-10T12:00:00Z");
        for (int i = 0; i < RETRIES_COUNT; i++) {
            long nextNano = (long) (Math.random() * NANOS_IN_ONE_HOUR);
            Assertions.assertThat(startOfHour)
                .isEqualTo(DateAndAmountUtils.atStartOfHour(startOfHour.plusNanos(nextNano)));
        }
    }

    @Test
    public void shouldSwitchInstantToEndOfHour() {
        Instant startOfHour = Instant.parse("2020-10-10T12:00:00Z");
        Instant endOfHour = startOfHour.plusSeconds(3600);
        for (int i = 0; i < RETRIES_COUNT; i++) {
            long nextNano = (long) (Math.random() * NANOS_IN_ONE_HOUR);
            Assertions.assertThat(endOfHour)
                .isEqualTo(DateAndAmountUtils.atEndOfHour(startOfHour.plusNanos(nextNano)));
        }
    }

    @Test
    public void shouldConvertInstantToUTCZonedDateTime() {
        Assertions.assertThat(
            DateAndAmountUtils.toUTCZonedDate(Instant.parse("2020-10-10T12:00:00Z")))
            .isEqualTo(ZonedDateTime.ofInstant(Instant.parse("2020-10-10T12:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    public void shouldConvertDoubleToBigDecimalCorrectlry() {
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(0))
            .isEqualTo(new BigDecimal("0.00000000"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12))
            .isEqualTo(new BigDecimal("12.00000000"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.1))
            .isEqualTo(new BigDecimal("12.10000000"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.11))
            .isEqualTo(new BigDecimal("12.11000000"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.111_111_11))
            .isEqualTo(new BigDecimal("12.11111111"));

        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.111_111_111))
            .isEqualTo(new BigDecimal("12.11111111"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.111_111_114))
            .isEqualTo(new BigDecimal("12.11111111"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.111_111_115))
            .isEqualTo(new BigDecimal("12.11111112"));
        Assertions.assertThat(DateAndAmountUtils.toBigDecimal(12.111_111_116))
            .isEqualTo(new BigDecimal("12.11111112"));
    }

}