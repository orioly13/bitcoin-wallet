package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static com.community.bitcoinwallet.util.DateAndAmountUtils.*;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class WalletService {

    H2WalletRepository repository;

    public void addEntry(WalletEntry entry) {
        validateWalletEntry(entry);
        repository.addEntry(entry);
    }

    public List<WalletEntry> getBalanceFull(Instant from, Instant to, boolean sync) {
        validateInstants(from, to);
        return fillMissingStartOfHours(getBalancesWithHoles(from, to, sync),
            atEndOfHour(from), atStartOfHour(to));
    }

    public List<WalletEntry> getBalancesWithHoles(Instant from, Instant to, boolean sync) {
        return sync ? getBalancesByHourSync(from, to) : getBalancesByHourAsync(from, to);
    }

    private List<WalletEntry> getBalancesByHourSync(Instant from, Instant to) {
        Instant fromAtStart = atStartOfHour(from);
        Instant toStart = atStartOfHour(to);
        WalletEntry beforeFrom = repository.getWalletSumBeforeFrom(fromAtStart)
            .map(w -> new WalletEntry(fromAtStart, w.getAmount()))
            .orElse(new WalletEntry(fromAtStart, DateAndAmountUtils.toBigDecimal(0.0)));
        BigDecimal[] incrementHolder = new BigDecimal[]{beforeFrom.getAmount()};
        List<WalletEntry> balanceByHour = repository.getWalletSumInRangeByHour(fromAtStart, toStart)
            .stream()
            .map(e -> {
                BigDecimal increment = incrementHolder[0];
                BigDecimal amount = e.getAmount().add(increment);
                incrementHolder[0] = increment.add(e.getAmount());
                return new WalletEntry(e.getDatetime().plus(1, ChronoUnit.HOURS), amount);
            })
            .collect(Collectors.toCollection(LinkedList::new));
        balanceByHour.add(0, beforeFrom);
        return balanceByHour;
    }

    protected List<WalletEntry> getBalancesByHourAsync(Instant from, Instant to) {
        List<WalletEntry> res = new LinkedList<>(repository.getBalancesWithinRange(from, to));
        Instant fromAtStartOfHour = atStartOfHour(from);
        if (res.isEmpty() || res.get(0).getDatetime().isAfter(fromAtStartOfHour)) {
            res.add(0, repository.getBalanceBeforeRange(from).orElse(
                new WalletEntry(fromAtStartOfHour, toBigDecimal("0.0"))));
        }
        return res;
    }

    private List<WalletEntry> fillMissingStartOfHours(Collection<WalletEntry> entries,
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
