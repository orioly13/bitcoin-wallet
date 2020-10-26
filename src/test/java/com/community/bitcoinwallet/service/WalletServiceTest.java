package com.community.bitcoinwallet.service;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.InMemoryWalletRepository;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

public class WalletServiceTest {


    private static final int COUNT_ENTRIES = 6;
    private static final int QUARTER_OF_HOUR_SECONDS = 15 * 60;

    private WalletService service;

    @BeforeEach
    public void setUpRepository() {
        service = new WalletService(new InMemoryWalletRepository());
    }

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
        Instant now = Instant.parse("2020-10-01T11:00:00.000Z");
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
        Assertions.assertThat(service.getBalance(instant, nextHour))
            .isEqualTo(
                Collections.singletonList(walletEntry(instant, "100.40")));

        Instant nextTwoHours = nextHour.plusSeconds(3600);
        Assertions.assertThat(service.getBalance(instant, nextTwoHours))
            .isEqualTo(
                Arrays.asList(walletEntry(instant, "100.40"),
                    walletEntry(nextHour, "50.20")));

        Assertions.assertThat(service.getBalance(nextTwoHours, nextTwoHours.plusSeconds(3600)))
            .isEqualTo(Collections.emptyList());
    }

    @Test
    public void shouldComputeBalanceForAGivenTimeFrameIfAddedInReversedTime() {
        Instant instant = addEntries(true);
        Instant prevHour = instant.minusSeconds(3600);

        Assertions.assertThat(service.getBalance(prevHour, instant))
            .isEqualTo(
                Collections.singletonList(walletEntry(prevHour, "100.40")));

        Instant prevTwoHours = prevHour.minusSeconds(3600);
        Assertions.assertThat(service.getBalance(prevTwoHours, instant))
            .isEqualTo(
                Arrays.asList(walletEntry(prevTwoHours, "25.10"),
                    walletEntry(prevHour, "100.40")));

        Assertions.assertThat(service.getBalance(prevTwoHours.minusSeconds(3600), prevTwoHours))
            .isEqualTo(Collections.emptyList());
    }

    private WalletEntry walletEntry(Instant instant, String amount) {
        return new WalletEntry(instant, new BigDecimal(amount));
    }

    private Instant addEntries(boolean reversedTime) {
        Instant now = Instant.parse("2020-10-01T11:00:00.000Z");
        Instant temp = now;
        BigDecimal amount = new BigDecimal("25.10");
        for (int i = 0; i < COUNT_ENTRIES; i++) {
            service.addEntry(new WalletEntry(temp, amount));
            temp = reversedTime ? temp.minusSeconds(QUARTER_OF_HOUR_SECONDS) :
                temp.plusSeconds(QUARTER_OF_HOUR_SECONDS);
        }
        return now;
    }
}