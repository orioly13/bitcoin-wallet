package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

import javax.annotation.PreDestroy;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

import static com.community.bitcoinwallet.util.DateAndAmountUtils.atEndOfHour;
import static com.community.bitcoinwallet.util.DateAndAmountUtils.atStartOfHour;

@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class InMemoryWalletRepository implements WalletRepository {

    boolean asyncCalculation;
    PriorityBlockingQueue<WalletEntry> entries = new PriorityBlockingQueue<>();
    Deque<WalletEntry> balances = new ConcurrentLinkedDeque<>();
    ExecutorService executorService;

    public InMemoryWalletRepository(boolean asyncBalanceCalculation) {
        this.asyncCalculation = asyncBalanceCalculation;
        if (asyncBalanceCalculation) {
            executorService = Executors.newSingleThreadExecutor();
        } else {
            executorService = null;
        }
    }

    @Override
    public void addEntry(WalletEntry entry) {
        entries.add(entry);
        if (asyncCalculation) {
            executorService.submit(() -> incrementBalancesFromEntry(entry));
        }
    }

    /**
     * Uses naive approach of iterating through the whole queue.
     * Should NOT be used on a production load.
     */
    @Override
    public List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive) {
        return asyncCalculation ? asyncApproach(fromExclusive, toInclusive) : syncApproach(fromExclusive, toInclusive);
    }

    private List<WalletEntry> syncApproach(Instant from, Instant to) {
        Instant fromAtStart = atStartOfHour(from);
        Instant toStart = DateAndAmountUtils.atStartOfHour(to);

        WalletEntry beforeFrom = entries.stream().filter(walletEntry ->
            walletEntry.getDatetime().isBefore(fromAtStart))
            .reduce((walletEntry, walletEntry2) -> new WalletEntry(null,
                walletEntry.getAmount().add(walletEntry2.getAmount())))
            .map(walletEntry -> new WalletEntry(fromAtStart,
                walletEntry.getAmount()))
            .orElse(
                new WalletEntry(fromAtStart, DateAndAmountUtils.toBigDecimal(0.0)));


        BigDecimal[] incrementHolder = new BigDecimal[]{beforeFrom.getAmount()};
        List<WalletEntry> balanceByHour = new LinkedList<>(entries.stream()
            .filter(walletEntry -> (walletEntry.getDatetime().equals(fromAtStart) ||
                walletEntry.getDatetime().isAfter(fromAtStart)) &&
                (walletEntry.getDatetime().isBefore(toStart)))
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
            .collect(Collectors.toList()));
        balanceByHour.add(0, beforeFrom);
        return balanceByHour;
    }

    private List<WalletEntry> asyncApproach(Instant fromExclusive, Instant toInclusive) {
        List<WalletEntry> res = balances.stream()
            .filter(entry -> {
                Instant datetime = entry.getDatetime();
                return (datetime.isAfter(fromExclusive) && datetime.isBefore(toInclusive)) ||
                    datetime.equals(toInclusive);
            }).collect(Collectors.toCollection(LinkedList::new));

        Instant instant = DateAndAmountUtils.atStartOfHour(fromExclusive);
        if (res.isEmpty() || res.get(0).getDatetime().isAfter(instant)) {
            res.add(0, balances.stream().filter(walletEntry ->
                walletEntry.getDatetime().isBefore(fromExclusive) ||
                    walletEntry.getDatetime().equals(fromExclusive))
                .max(WalletEntry::compareTo)
                .orElse(new WalletEntry(instant, DateAndAmountUtils.toBigDecimal("0.0"))));
        }
        return res;
    }

    public void clear() {
        entries.clear();
        balances.clear();
    }

    /**
     * This method is not actually thread safe and should not be used in production.
     * That's why Executor service is forcefully single-threaded.
     */
    private void incrementBalancesFromEntry(WalletEntry walletEntry) {
        Instant atEndOfHour = DateAndAmountUtils.atEndOfHour(walletEntry.getDatetime());
        if (balances.isEmpty()) {
            balances.add(new WalletEntry(atEndOfHour, walletEntry.getAmount()));
            return;
        }

        balances.stream()
            .filter(b -> b.getDatetime().isAfter(walletEntry.getDatetime()))
            .forEach(b -> b.setAmount(b.getAmount().add(walletEntry.getAmount())));

        if (balances.getFirst().getDatetime().isAfter(atEndOfHour)) {
            balances.addFirst(new WalletEntry(atEndOfHour, walletEntry.getAmount()));
        }

        BigDecimal lastAmount = balances.getLast().getAmount();
        if (balances.getLast().getDatetime().isBefore(atEndOfHour)) {
            balances.addLast(new WalletEntry(atEndOfHour, walletEntry.getAmount().add(lastAmount)));
        }
    }

    @PreDestroy
    public void shutDownExecutor() {
        if (asyncCalculation) {
            executorService.shutdownNow();
        }
    }

}
