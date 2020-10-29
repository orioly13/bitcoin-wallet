package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;


@RunWith(SpringRunner.class)
@Transactional
public abstract class GenericRepositoryTest {

    @Autowired
    private WalletRepository repository;

    @BeforeEach
    public void setUpRepository() {
        repository.clear();
    }

    @Test
    public void shouldAddEntriesAndReturnBalances() {
        WalletEntry walletEntry1 = addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
            "11.00");
        WalletEntry walletEntryRes = new WalletEntry(Instant.parse("2020-10-01T12:00:00.000Z"),
            DateAndAmountUtils.toBigDecimal("11.00"));

        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:00.001Z")))
            .isEqualTo(Collections.singletonList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0"))));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:59:59.999Z")))
            .isEqualTo(Collections.singletonList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0"))));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z")))
            .isEqualTo(Collections.singletonList(walletEntryRes));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.001Z")))
            .isEqualTo(Arrays.asList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0")), walletEntryRes));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:59:59.999Z"),
            Instant.parse("2020-10-01T12:00:00.001Z")))
            .isEqualTo(Arrays.asList(new WalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"),
                DateAndAmountUtils.toBigDecimal("0.0")), walletEntryRes));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T12:59:59.999Z")))
            .isEqualTo(Collections.singletonList(walletEntryRes));
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T12:00:00.000Z"),
            Instant.parse("2020-10-01T13:00:00.000Z")))
            .isEqualTo(Collections.singletonList(walletEntryRes));
    }

    @Test
    public void addingToRepoShouldCalculateBalanceAtEndOfHour() {
        //empty
        addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z")))
            .isEqualTo(Arrays.asList(simpleWalletEntry("2020-10-01T11:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 10)));

        addWalletEntry(Instant.parse("2020-10-01T10:30:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:30:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:35:00.000Z"), "10.00");
        addWalletEntry(Instant.parse("2020-10-01T11:40:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z")))
            .isEqualTo(Arrays.asList(simpleWalletEntry("2020-10-01T11:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 50)));

        // exactly at first hour
        addWalletEntry(Instant.parse("2020-10-01T11:00:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z")))
            .isEqualTo(Arrays.asList(simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 60)));

        // before first hour
        addWalletEntry(Instant.parse("2020-10-01T10:35:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T12:00:00.000Z")))
            .isEqualTo(Arrays.asList(simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70)));

        // exactly at last hour
        addWalletEntry(Instant.parse("2020-10-01T12:00:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T13:00:00.000Z")))
            .isEqualTo(Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 80)));

        // after last hour
        addWalletEntry(Instant.parse("2020-10-01T13:30:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T14:00:00.000Z")))
            .isEqualTo(Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T11:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 70),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 80),
                simpleWalletEntry("2020-10-01T14:00:00.000Z", 90)));


        // between first and last hours
        addWalletEntry(Instant.parse("2020-10-01T11:45:00.000Z"), "10.00");
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T14:00:00.000Z")))
            .isEqualTo(Arrays.asList(
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
        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T23:00:00.000Z")))
            .isEqualTo(Arrays.asList(
                simpleWalletEntry("2020-10-01T10:00:00.000Z", 0),
                simpleWalletEntry("2020-10-01T12:00:00.000Z", 10),
                simpleWalletEntry("2020-10-01T13:00:00.000Z", 20),
                simpleWalletEntry("2020-10-01T16:00:00.000Z", 30),
                simpleWalletEntry("2020-10-01T19:00:00.000Z", 40),
                simpleWalletEntry("2020-10-01T23:00:00.000Z", 50)));

        Assertions.assertThat(repository.getBalancesByHour(Instant.parse("2020-10-01T11:45:00.000Z"),
            Instant.parse("2020-10-01T23:00:00.000Z")))
            .isEqualTo(Arrays.asList(
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
        repository.addEntry(walletEntry);
        return walletEntry;
    }
}