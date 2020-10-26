package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InMemoryWalletRepository implements WalletRepository {

    PriorityBlockingQueue<WalletEntry> entries = new PriorityBlockingQueue<>();

    @Override
    public void addEntry(WalletEntry entry) {
        entries.add(entry);
    }

    /**
     * Uses naive approach of iterating through the whole queue.
     * Should NOT be used on a production load.
     */
    @Override
    public List<WalletEntry> getEntries(Instant fromInclusive, Instant toExclusive) {
        return entries.stream()
            .filter(entry -> {
                Instant datetime = entry.getDatetime();
                return datetime.equals(fromInclusive) ||
                    datetime.isAfter(fromInclusive) && datetime.isBefore(toExclusive);
            })
            .collect(Collectors.toList());
    }
}
