package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.WalletRepository;
import com.community.bitcoinwallet.util.WalletEntryUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.community.bitcoinwallet.util.WalletEntryUtils.atEndOfHour;
import static com.community.bitcoinwallet.util.WalletEntryUtils.atStartOfHour;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletService {

    WalletRepository repository;

    public void addEntry(WalletEntry entry) {
        validateWalletEntry(entry);
        repository.addEntry(entry);
    }

    /**
     * Uses naive approach of iterating through the whole queue.
     * Should NOT be used on a production load.
     */
    public List<WalletEntry> getBalance(Instant from, Instant to) {
        validateInstants(from, to);
        Instant fromAtStartOfHour = WalletEntryUtils.atStartOfHour(from);
        Instant toAtStartOfHour = WalletEntryUtils.atStartOfHour(to);

        return groupAmountsByHour(repository.getEntries(fromAtStartOfHour, toAtStartOfHour));
    }

    private List<WalletEntry> groupAmountsByHour(Collection<WalletEntry> entries) {
        Map<Instant, BigDecimal> groupedByHour = entries.stream()
            .map(walletEntry -> new WalletEntry(atStartOfHour(walletEntry.getDatetime()), walletEntry.getAmount()))
            .collect(Collectors.groupingBy(WalletEntry::getDatetime, LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, WalletEntry::getAmount, BigDecimal::add)));
        return groupedByHour.entrySet()
            .stream()
            .map(e -> new WalletEntry(e.getKey(), e.getValue()))
            .filter(walletEntry -> walletEntry.getAmount().compareTo(BigDecimal.ZERO) > 0)
            .collect(Collectors.toList());
    }

    private void validateWalletEntry(WalletEntry entry) {
        if (entry == null || entry.getDatetime() == null || entry.getAmount() == null) {
            throw new IllegalArgumentException("Not all fields filled in entry:" + entry);
        }
        if (entry.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount is negative in entry:" + entry);
        }
    }

    private void validateInstants(Instant from, Instant to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException(
                String.format("Not all instants passed correctly: from=%s;to=%s", from, to));
        }
        if (atStartOfHour(to).isBefore(atEndOfHour(from))) {
            throw new IllegalArgumentException(
                String.format("From and to should be in different hours: from=%s;to=%s", from, to));
        }
    }

}
