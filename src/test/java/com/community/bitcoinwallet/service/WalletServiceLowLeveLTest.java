package com.community.bitcoinwallet.service;

import com.community.bitcoinwallet.SpringTest;
import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;


public class WalletServiceLowLeveLTest extends SpringTest {

    @Autowired
    private H2WalletRepository h2WalletRepository;
    @Autowired
    private WalletService walletService;
    @Autowired
    private BalanceUpdaterService balanceUpdaterService;

    @BeforeEach
    public void setUpRepository() {
        h2WalletRepository.clear();
    }

    private void assertSyncAndAsyncExections(Instant from, Instant to,
                                             List<WalletEntry> expecetedResult) {
        List<WalletEntry> syncRes = walletService.getBalancesWithHoles(from, to, true);
        Assertions.assertThat(syncRes).isEqualTo(expecetedResult);
        balanceUpdaterService.updateBalances(false);
        List<WalletEntry> asyncRes = walletService.getBalancesWithHoles(from, to, false);
        Assertions.assertThat(asyncRes).isEqualTo(expecetedResult);
    }


    @Test
    public void shouldAddEntriesAndReturnBalances() {
        WalletEntry walletEntry1 = addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
            "11.00");
        WalletEntry walletEntryRes = new WalletEntry(Instant.parse("2020-10-01T12:00:00.000Z"),
            DateAndAmountUtils.toBigDecimal("11.00"));

        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:00.001Z"),
            Collections.singletonList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0"))));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:59:59.999Z"),
            Collections.singletonList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0"))));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z"),
            Collections.singletonList(walletEntryRes));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.001Z"),
            Arrays.asList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0")), walletEntryRes));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:59:59.999Z"),
            Instant.parse("2020-10-01T12:00:00.001Z"),
            Arrays.asList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0")), walletEntryRes));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T12:59:59.999Z"),
            Collections.singletonList(walletEntryRes));
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T13:00:00.000Z"),
            Collections.singletonList(walletEntryRes));
    }

    @Test
    public void addingToRepoShouldCalculateBalanceAtEndOfHour() {
        //empty
        addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z"),
            Arrays.asList(simpleWalletEntry("2020-10-01T11:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 10)));

        addWalletEntry(Instant.parse("2020-10-01T10:30:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:30:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:35:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:40:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z"),
            Arrays.asList(simpleWalletEntry("2020-10-01T11:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 50)));

        // exactly at first hour
        addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z"),
            Arrays.asList(simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 60)));

        // before first hour
        addWalletEntry(Instant.parse("2020-10-01T10:35:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z"),
            Arrays.asList(simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70)));

        // exactly at last hour
        addWalletEntry(Instant.parse("2020-10-01T12:00:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T13:00:00.000Z"),
            Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 80)));

        // after last hour
        addWalletEntry(Instant.parse("2020-10-01T13:30:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T14:00:00.000Z"),
            Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 80),
                simpleWalletEntry("2020-10-01T14:00:00.000Z", 90)));


        // between first and last hours
        addWalletEntry(Instant.parse("2020-10-01T11:45:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T14:00:00.000Z"),
            Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 80),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 90),
                simpleWalletEntry("2020-10-01T14:00:00.000Z", 100)));
    }

    @Test
    public void shouldRetrieveOnlyDiffs() {
        // between first and last hours
        addWalletEntry(Instant.parse("2020-10-01T11:45:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T12:45:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T15:00:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T18:01:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T22:30:00.000Z"), "10.00");
        assertSyncAndAsyncExections(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T23:00:00.000Z"),
            Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T16:00:00.000Z", 30),
                simpleWalletEntry("2020-10-01T19:00:00.000Z", 40),
                simpleWalletEntry("2020-10-01T23:00:00.000Z", 50)));

        assertSyncAndAsyncExections(Instant.parse("2020-10-01T11:45:00.000Z"),
            Instant.parse("2020-10-01T23:00:00.000Z"),
            Arrays.asList(
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T16:00:00.000Z", 30),
                simpleWalletEntry("2020-10-01T19:00:00.000Z", 40),
                simpleWalletEntry("2020-10-01T23:00:00.000Z", 50)));

    }

    private WalletEntry simpleWalletEntry(String ts, double amount) {
        return new WalletEntry(Instant.parse(ts), DateAndAmountUtils.toBigDecimal(amount));
    }

    private WalletEntry addWalletEntry(Instant instant, String amount) {
        WalletEntry walletEntry = new WalletEntry(instant, DateAndAmountUtils.toBigDecimal(amount));
        h2WalletRepository.addEntry(walletEntry);
        return walletEntry;
    }
}