package com.community.bitcoinwallet.service;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {BitcoinWalletApplication.class},
    properties = "spring.profiles.active=h2")
@Transactional
public class WalletServiceTest {

    private static final int COUNT_ENTRIES = 6;
    private static final int QUARTER_OF_HOUR_SECONDS = 15 * 60;

    @Autowired
    private WalletService service;

    @Test
    public void addEntryShouldThrowExceptionsIfIllegalEntyPassed() {
        Assertions.assertThatThrownBy(() -> service.addEntry(null))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.addEntry(new WalletEntry(null, BigDecimal.ZERO)))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.addEntry(new WalletEntry(Instant.now(), null)))
            .isInstanceOf(IllegalArgumentException.class);

        Assertions.assertThatThrownBy(() -> service.addEntry(new WalletEntry(Instant.now(),
            BigDecimal.ONE.negate())))
            .isInstanceOf(IllegalArgumentException.class);

        service.addEntry(new WalletEntry(Instant.now(), BigDecimal.ONE));
    }

    @Test
    public void getBalanceShouldThrowExceptionsIfIllegalEntyPassed() {
        Instant now = Instant.parse("2020-09-01T11:00:00.000Z");
        Assertions.assertThatThrownBy(() -> service.getBalance(null, now))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalance(now, null))
            .isInstanceOf(IllegalArgumentException.class);

        Assertions.assertThatThrownBy(() -> service.getBalance(now, now))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalance(now, now.minusNanos(1)))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalance(now, now.plusNanos(1)))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalance(now, now.plusSeconds(3599)))
            .isInstanceOf(IllegalArgumentException.class);

        service.getBalance(now, now.plusSeconds(3600));
    }

    @Test
    public void shouldComputeBalanceForAGivenTimeFrame() {
        Instant instant = addEntries(false);
        Instant nextHour = instant.plusSeconds(3600);
        Instant nextHour2 = nextHour.plusSeconds(3600);
        Assertions.assertThat(service.getBalance(instant, nextHour))
            .isEqualTo(
                Collections.singletonList(walletEntry(nextHour, "100.40")));

        Instant nextHour3 = nextHour2.plusSeconds(3600);
        Assertions.assertThat(service.getBalance(instant, nextHour3))
            .isEqualTo(
                Arrays.asList(walletEntry(nextHour, "100.40"),
                    walletEntry(nextHour2, "50.20")));

        Assertions.assertThat(service.getBalance(nextHour3, nextHour3.plusSeconds(3600)))
            .isEqualTo(Collections.emptyList());
    }

    @Test
    public void shouldComputeBalanceForAGivenTimeFrameIfAddedInReversedTime() {
        Instant instant = addEntries(true);
        Instant prevHour = instant.minusSeconds(3600);

        Assertions.assertThat(service.getBalance(prevHour, instant))
            .isEqualTo(
                Collections.singletonList(walletEntry(instant, "100.40")));

        Instant prevTwoHours = prevHour.minusSeconds(3600);
        Assertions.assertThat(service.getBalance(prevTwoHours, instant))
            .isEqualTo(
                Arrays.asList(walletEntry(prevHour, "25.10"),
                    walletEntry(instant, "100.40")));

        Assertions.assertThat(service.getBalance(prevTwoHours.minusSeconds(3600), prevTwoHours))
            .isEqualTo(Collections.emptyList());
    }

    @Test
    public void shouldSkipHoursWithoutBalance() {
        BigDecimal amount = DateAndAmountUtils.toBigDecimal("25.10");
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:00:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:30:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T14:15:00.000Z"), amount));

        Assertions.assertThat(service.getBalance(Instant.parse("2020-09-01T10:00:00.000Z"),
            Instant.parse("2020-09-01T16:00:00.000Z")))
            .isEqualTo(
                Arrays.asList(walletEntry(Instant.parse("2020-09-01T12:00:00.000Z"), "50.20"),
                    walletEntry(Instant.parse("2020-09-01T14:00:00.000Z"), "25.10"),
                    walletEntry(Instant.parse("2020-09-01T15:00:00.000Z"), "25.10")));
    }

    private WalletEntry walletEntry(Instant instant, String amount) {
        return new WalletEntry(instant, DateAndAmountUtils.toBigDecimal(amount));
    }

    private Instant addEntries(boolean reversedTime) {
        Instant now = Instant.parse("2020-09-01T11:00:00.000Z");
        Instant temp = now;
        BigDecimal amount = DateAndAmountUtils.toBigDecimal("25.10");
        for (int i = 0; i < COUNT_ENTRIES; i++) {
            service.addEntry(new WalletEntry(temp, amount));
            temp = reversedTime ? temp.minusSeconds(QUARTER_OF_HOUR_SECONDS) :
                temp.plusSeconds(QUARTER_OF_HOUR_SECONDS);
        }
        return now;
    }
}