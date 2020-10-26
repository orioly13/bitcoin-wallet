package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

public class InMemoryWalletRepositoryTest {

    private WalletRepository repository;

    @BeforeEach
    public void setUpRepository() {
        repository = new InMemoryWalletRepository();
    }

    @Test
    public void shouldAddAndReturnEntries() {
        WalletEntry walletEntry1 = walletEntry(Instant.parse("2020-10-01T11:00:00.000Z"), "11.00");
        WalletEntry walletEntry2 = walletEntry(Instant.parse("2020-10-01T11:00:10.000Z"), "12.00");
        WalletEntry walletEntry3 = walletEntry(Instant.parse("2020-10-01T11:00:20.000Z"), "13.00");
        WalletEntry walletEntry4 = walletEntry(Instant.parse("2020-10-01T11:00:30.000Z"), "14.00");

        Assertions.assertThat(repository.getEntries(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:00.000Z")))
            .isEqualTo(Collections.singletonList(walletEntry1));
        Assertions.assertThat(repository.getEntries(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:10.000Z")))
            .isEqualTo(Collections.singletonList(walletEntry1));
        Assertions.assertThat(repository.getEntries(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:19.000Z")))
            .isEqualTo(Arrays.asList(walletEntry1, walletEntry2));
        Assertions.assertThat(repository.getEntries(Instant.parse("2020-10-01T11:00:00.000Z"),
            Instant.parse("2020-12-01T11:00:19.000Z")))
            .isEqualTo(Arrays.asList(walletEntry1, walletEntry2, walletEntry3, walletEntry4));
        Assertions.assertThat(repository.getEntries(Instant.parse("2020-10-01T10:00:00.000Z"),
            Instant.parse("2020-10-01T11:00:00.000Z")))
            .isEqualTo(Collections.emptyList());
    }

    private WalletEntry walletEntry(Instant instant, String amount) {
        WalletEntry walletEntry = new WalletEntry(instant, new BigDecimal(amount));
        repository.addEntry(walletEntry);
        return walletEntry;
    }
}