package com.community.bitcoinwallet.util;


import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

class WalletEntryUtilsTest {

    private static final long NANOS_IN_ONE_HOUR = 3600 * 1_000_000L;
    private static final int RETRIES_COUNT = 10_000;

    @Test
    public void shouldSwitchInstantToStartOfHour() {
        Instant startOfHour = Instant.parse("2020-10-10T12:00:00Z");
        for (int i = 0; i < RETRIES_COUNT; i++) {
            long nextNano = (long) (Math.random() * NANOS_IN_ONE_HOUR);
            Assertions.assertThat(startOfHour)
                .isEqualTo(WalletEntryUtils.atStartOfHour(startOfHour.plusNanos(nextNano)));
        }
    }

    @Test
    public void shouldSwitchInstantToEndOfHour() {
        Instant startOfHour = Instant.parse("2020-10-10T12:00:00Z");
        Instant endOfHour = startOfHour.plusSeconds(3600);
        for (int i = 0; i < RETRIES_COUNT; i++) {
            long nextNano = (long) (Math.random() * NANOS_IN_ONE_HOUR);
            Assertions.assertThat(endOfHour)
                .isEqualTo(WalletEntryUtils.atEndOfHour(startOfHour.plusNanos(nextNano)));
        }
    }

    @Test
    public void shouldConvertDoubleToBigDecimalCorrectlry(){
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(0))
            .isEqualTo(new BigDecimal("0.00"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12))
            .isEqualTo(new BigDecimal("12.00"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.1))
            .isEqualTo(new BigDecimal("12.10"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.11))
            .isEqualTo(new BigDecimal("12.11"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.111))
            .isEqualTo(new BigDecimal("12.11"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.114))
            .isEqualTo(new BigDecimal("12.11"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.115))
            .isEqualTo(new BigDecimal("12.12"));
        Assertions.assertThat(WalletEntryUtils.toBigDecimal(12.116))
            .isEqualTo(new BigDecimal("12.12"));
    }

}