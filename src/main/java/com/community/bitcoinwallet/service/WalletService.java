package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import liquibase.pro.packaged.B;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

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
        Instant fromAtStartOfHour = DateAndAmountUtils.atStartOfHour(from);
        Instant toAtStartOfHour = DateAndAmountUtils.atStartOfHour(to);

        return groupAmountsByHour(repository.getEntries(fromAtStartOfHour, toAtStartOfHour),
            DateAndAmountUtils.atEndOfHour(from), DateAndAmountUtils.atStartOfHour(to));
    }

    private List<WalletEntry> groupAmountsByHour(Collection<WalletEntry> entries,
                                                 Instant fromAtEndOfHour,
                                                 Instant toAtStartOfHour) {
        BigDecimal amountBeforeTimeFrame = entries.stream().filter(
            walletEntry -> walletEntry.getDatetime().isBefore(fromAtEndOfHour))
            .map(WalletEntry::getAmount)
            .reduce(BigDecimal::add)
            .orElse(DateAndAmountUtils.toBigDecimal(0.0));

        BigDecimal[] incrementHolder = new BigDecimal[]{amountBeforeTimeFrame.add(BigDecimal.ZERO)};
        List<WalletEntry> balanceByHour = entries.stream()
            .filter(walletEntry -> walletEntry.getDatetime().equals(fromAtEndOfHour)
                || walletEntry.getDatetime().isAfter(fromAtEndOfHour))
            .map(walletEntry -> new WalletEntry(atEndOfHour(walletEntry.getDatetime()), walletEntry.getAmount()))
            .collect(Collectors.groupingBy(WalletEntry::getDatetime, LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, WalletEntry::getAmount, BigDecimal::add)))
            .entrySet()
            .stream()
            .map(e -> {
                BigDecimal increment = incrementHolder[0];
                BigDecimal amount = e.getValue().add(increment);
                incrementHolder[0] = increment.add(e.getValue());
                return new WalletEntry(e.getKey(), amount);
            })
            .collect(Collectors.toList());

        List<WalletEntry> result = new LinkedList<>();
        for (int i = 0; i < balanceByHour.size(); i++) {
            WalletEntry current = balanceByHour.get(i);
            WalletEntry next = null;
            if (i < balanceByHour.size() - 1) {
                next = balanceByHour.get(i + 1);
            }
            result.add(current);
            if (next == null) {
                continue;
            }
            Instant temp = current.getDatetime().plus(1, ChronoUnit.HOURS);
            while (temp.isBefore(next.getDatetime())) {
                result.add(new WalletEntry(temp, current.getAmount()));
                temp = temp.plus(1, ChronoUnit.HOURS);
            }
        }

        Instant temp = result.isEmpty() ? toAtStartOfHour :
            result.get(0).getDatetime().minus(1, ChronoUnit.HOURS);
        while (temp.isAfter(fromAtEndOfHour) || temp.equals(fromAtEndOfHour)) {
            result.add(0, new WalletEntry(temp, amountBeforeTimeFrame));
            temp = temp.minus(1, ChronoUnit.HOURS);
        }

        temp = result.isEmpty() ? null : result.get(result.size() - 1).getDatetime()
            .plus(1, ChronoUnit.HOURS);
        WalletEntry lastEntry = result.isEmpty() ? null : result.get(result.size() - 1);
        while (!result.isEmpty() && temp.isBefore(toAtStartOfHour)) {
            result.add(new WalletEntry(temp, lastEntry.getAmount()));
            temp = temp.plus(1, ChronoUnit.HOURS);
        }
        return result;
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
