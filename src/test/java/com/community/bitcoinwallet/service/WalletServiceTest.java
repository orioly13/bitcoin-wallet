package com.community.bitcoinwallet.service;

import com.community.bitcoinwallet.SpringTest;
import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class WalletServiceTest extends SpringTest {

    private static final int COUNT_ENTRIES = 6;
    private static final int QUARTER_OF_HOUR_SECONDS = 15 * 60;

    @Autowired
    private WalletService service;
    @Autowired
    private BalanceUpdaterService balanceUpdaterService;
    @Autowired
    private H2WalletRepository repository;

    @BeforeEach
    private void setUp() {
        repository.clear();
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
        Instant now = Instant.parse("2020-09-01T11:00:00.000Z");
        Assertions.assertThatThrownBy(() -> service.getBalanceFull(null, now, true))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalanceFull(now, null, true))
            .isInstanceOf(IllegalArgumentException.class);

        Assertions.assertThatThrownBy(() -> service.getBalanceFull(now, now, true))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalanceFull(now, now.minusNanos(1), true))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalanceFull(now, now.plusNanos(1), true))
            .isInstanceOf(IllegalArgumentException.class);
        Assertions.assertThatThrownBy(() -> service.getBalanceFull(now, now.plusSeconds(3599), true))
            .isInstanceOf(IllegalArgumentException.class);

        service.getBalanceFull(now, now.plusSeconds(3600),true);
    }

    private void assertSyncAndAsyncBalances(Instant from, Instant to,
                                            List<WalletEntry> expecetedResult) {
        List<WalletEntry> syncRes = service.getBalanceFull(from, to, true);
        Assertions.assertThat(syncRes).isEqualTo(expecetedResult);
        balanceUpdaterService.updateBalances(false);
        List<WalletEntry> asyncRes = service.getBalanceFull(from, to, false);
        Assertions.assertThat(asyncRes).isEqualTo(expecetedResult);
    }

    @Test
    public void shouldComputeBalanceForAGivenTimeFrame() {
        Instant instant = addEntries(false);
        Instant nextHour = instant.plusSeconds(3600);
        Instant nextHour2 = nextHour.plusSeconds(3600);
        assertSyncAndAsyncBalances(instant, nextHour,
            Collections.singletonList(walletEntry(nextHour, "100.40")));

        assertSyncAndAsyncBalances(instant, nextHour2,
            Arrays.asList(walletEntry(nextHour, "100.40"),
                walletEntry(nextHour2, "150.60")));

        Instant nextHour3 = nextHour2.plusSeconds(3600);
        assertSyncAndAsyncBalances(nextHour3, nextHour3.plusSeconds(3600),
            Collections.singletonList(walletEntry(nextHour3.plusSeconds(3600), "150.60")));
    }

    @Test
    public void shouldComputeBalanceForAGivenTimeFrameIfAddedInReversedTime() {
        Instant instant = addEntries(true);
        Instant prevHour = instant.minusSeconds(3600);

        assertSyncAndAsyncBalances(prevHour, instant,
            Collections.singletonList(walletEntry(instant, "125.5")));

        Instant prevTwoHours = prevHour.minusSeconds(3600);
        assertSyncAndAsyncBalances(prevTwoHours, instant,
            Arrays.asList(walletEntry(prevHour, "25.10"),
                walletEntry(instant, "125.50")));

        assertSyncAndAsyncBalances(prevTwoHours.minusSeconds(3600), prevTwoHours,
            Collections.singletonList(walletEntry(prevTwoHours, "0.0")));
    }

    @Test
    public void shouldFillEmptyHoursWithCurrentBalance() {
        BigDecimal amount = DateAndAmountUtils.toBigDecimal("25.10");
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:00:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:30:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), amount));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T14:15:00.000Z"), amount));

        assertSyncAndAsyncBalances(Instant.parse("2020-09-01T09:00:00.000Z"),
            Instant.parse("2020-09-01T17:00:00.000Z"),
            Arrays.asList(
                walletEntry(Instant.parse("2020-09-01T10:00:00.000Z"), "0.0"),
                walletEntry(Instant.parse("2020-09-01T11:00:00.000Z"), "0.0"),
                walletEntry(Instant.parse("2020-09-01T12:00:00.000Z"), "50.20"),
                walletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), "50.20"),
                walletEntry(Instant.parse("2020-09-01T14:00:00.000Z"), "75.30"),
                walletEntry(Instant.parse("2020-09-01T15:00:00.000Z"), "100.4"),
                walletEntry(Instant.parse("2020-09-01T16:00:00.000Z"), "100.4"),
                walletEntry(Instant.parse("2020-09-01T17:00:00.000Z"), "100.4")));
    }

    @Test
    public void shouldComputeCorrectAmount() {
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:00:00.000Z"),
            DateAndAmountUtils.toBigDecimal("1")));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T11:30:00.000Z"),
            DateAndAmountUtils.toBigDecimal("2")));
        service.addEntry(new WalletEntry(Instant.parse("2020-09-01T12:00:00.000Z"),
            DateAndAmountUtils.toBigDecimal("1")));

        assertSyncAndAsyncBalances(Instant.parse("2020-09-01T11:00:00.000Z"),
            Instant.parse("2020-09-01T13:00:00.000Z"),
            Arrays.asList(
                walletEntry(Instant.parse("2020-09-01T12:00:00.000Z"), "3"),
                walletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), "4")));

        assertSyncAndAsyncBalances(Instant.parse("2020-09-01T10:00:00.000Z"),
            Instant.parse("2020-09-01T13:00:00.000Z"),
            Arrays.asList(
                walletEntry(Instant.parse("2020-09-01T11:00:00.000Z"), "0"),
                walletEntry(Instant.parse("2020-09-01T12:00:00.000Z"), "3"),
                walletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), "4")));

        assertSyncAndAsyncBalances(Instant.parse("2020-09-01T12:00:00.000Z"),
            Instant.parse("2020-09-01T13:00:00.000Z"),
            Arrays.asList(
                walletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), "4")));
    }

    @Test
    public void shouldReturnZeroesIfBalanceIsEmpty() {
        assertSyncAndAsyncBalances(Instant.parse("2020-09-01T11:00:00.000Z"),
            Instant.parse("2020-09-01T13:00:00.000Z"),
            Arrays.asList(
                walletEntry(Instant.parse("2020-09-01T12:00:00.000Z"), "0"),
                walletEntry(Instant.parse("2020-09-01T13:00:00.000Z"), "0")));
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