package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.community.bitcoinwallet.util.DateAndAmountUtils.atEndOfHour;
import static com.community.bitcoinwallet.util.DateAndAmountUtils.atStartOfHour;

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

        return groupAmountsByHour(repository.getBalancesByHour(from, to),
            DateAndAmountUtils.atEndOfHour(from), DateAndAmountUtils.atStartOfHour(to));
    }

    private List<WalletEntry> groupAmountsByHour(Collection<WalletEntry> entries,
                                                 Instant fromAtEndOfHour,
                                                 Instant toAtStartOfHour) {
        List<WalletEntry> res = new ArrayList<>();
        Iterator<WalletEntry> iterator = entries.iterator();
        WalletEntry currentDiff = iterator.next();
        Optional<WalletEntry> nextDiff = getNext(iterator);
        Instant currentHour = fromAtEndOfHour;

        if (nextDiff.isPresent() &&
            (currentHour.isAfter(nextDiff.get().getDatetime()) ||
                currentHour.equals(nextDiff.get().getDatetime()))) {
            currentDiff = nextDiff.get();
            nextDiff = getNext(iterator);
        }
        while (currentHourIsAfterCurrentDiff(currentHour, currentDiff) &&
            currentHourIsBeforeNextDiff(currentHour, nextDiff)) {
            res.add(new WalletEntry(currentHour, currentDiff.getAmount()));
            currentHour = currentHour.plus(1, ChronoUnit.HOURS);

            if (currentHour.isAfter(nextDiff.get().getDatetime()) ||
                currentHour.equals(nextDiff.get().getDatetime())) {
                currentDiff = nextDiff.get();
                nextDiff = getNext(iterator);
            }
        }
        while (currentHour.isBefore(toAtStartOfHour) || currentHour.equals(toAtStartOfHour)) {
            res.add(new WalletEntry(currentHour, currentDiff.getAmount()));
            currentHour = currentHour.plus(1, ChronoUnit.HOURS);
        }
        return res;
    }

    private boolean currentHourIsAfterCurrentDiff(Instant currentHour, WalletEntry currentDiff) {
        Instant diffTs = currentDiff.getDatetime();
        return currentHour.equals(diffTs) || currentHour.isAfter(diffTs);
    }

    private boolean currentHourIsBeforeNextDiff(Instant currentHour, Optional<WalletEntry> nextDiff) {
        return nextDiff.isPresent() && currentHour.isBefore(nextDiff.get().getDatetime());
    }

    private Optional<WalletEntry> getNext(Iterator<WalletEntry> entryIterator) {
        return entryIterator.hasNext() ? Optional.of(entryIterator.next()) : Optional.empty();
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
