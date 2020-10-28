package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;

import java.time.Instant;
import java.util.List;

public interface WalletRepository {

    void addEntry(WalletEntry entry);

    List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive);

    void clear();

}
