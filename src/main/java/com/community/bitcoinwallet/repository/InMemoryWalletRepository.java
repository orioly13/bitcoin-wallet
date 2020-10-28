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
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.stream.Collectors;

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
        } else {
            incrementBalancesFromEntry(entry);
        }
    }

    /**
     * Uses naive approach of iterating through the whole queue.
     * Should NOT be used on a production load.
     */
    @Override
    public List<WalletEntry> getBalancesByHour(Instant fromExclusive, Instant toInclusive) {
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
