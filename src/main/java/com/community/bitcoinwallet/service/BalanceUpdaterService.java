package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.H2WalletRepository;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.community.bitcoinwallet.util.DateAndAmountUtils.atEndOfHour;
import static com.community.bitcoinwallet.util.DateAndAmountUtils.atStartOfHour;


@Log4j2
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class BalanceUpdaterService {

    H2WalletRepository repository;
    WalletService walletService;
    ScheduledExecutorService updateBalanceTaskScheduler;
    ExecutorService parallelBalanceUpdateExecutor;
    int threadCount;


    public BalanceUpdaterService(H2WalletRepository repository, WalletService walletService,
                                 long scheduledUpdatePeriodMillis,
                                 ScheduledExecutorService updateBalanceTaskSheduler,
                                 ExecutorService parallelBalanceUpdateExecutor, int threadCount) {
        this.repository = repository;
        this.walletService = walletService;
        this.updateBalanceTaskScheduler = updateBalanceTaskSheduler;
        this.parallelBalanceUpdateExecutor = parallelBalanceUpdateExecutor;
        this.threadCount = threadCount;
        updateBalanceTaskSheduler.scheduleAtFixedRate(() -> updateBalances(true),
            scheduledUpdatePeriodMillis, scheduledUpdatePeriodMillis, TimeUnit.MILLISECONDS);
    }


    @Transactional
    public void updateBalances(boolean parallel) {
        // 1) extract and delete all events from queue
        // 2) split into ranges, if parallel - split between workers
        // 3) call service.getBalanceInRange();
        // for every range -> save to balance table
        Optional<WalletEntry> firstEventAndClearQueue = repository.getFirstEventAndClearQueue();
        if (firstEventAndClearQueue.isEmpty()) {
            return;
        }
        WalletEntry event = firstEventAndClearQueue.get();
        // 2)
        Instant from = atStartOfHour(event.getDatetime());
        Instant to = repository.getLastBalanceTs().orElse(from.plus(1, ChronoUnit.HOURS));
        if (!to.isAfter(from)) {
            to = from.plus(1, ChronoUnit.HOURS);
        }
        long wholeHours = countHoursBetweenFromAndTo(from, to);
        List<Range> ranges = new ArrayList<>();
        long step = wholeHours / threadCount <= 1 ? 1 : wholeHours / threadCount;
        Instant start = from;
        for (int i = 0; i < wholeHours; i += step) {
            ranges.add(new Range(start, start.plus(step, ChronoUnit.HOURS)));
        }
        processRangesSequentially(ranges);
    }

    private void processRangesSequentially(List<Range> ranges) {
        for (int i = 0; i < ranges.size(); i++) {
            repository.mergeIntoBalances(
                getBalancesToMerge(ranges.get(i).fromExclusive, ranges.get(i).toInclusive, i == 0));
        }
    }

    private long countHoursBetweenFromAndTo(Instant from, Instant to) {
        return (to.toEpochMilli() - from.toEpochMilli()) / 1000 / 3600;
    }

    private List<WalletEntry> getBalancesToMerge(Instant fromExclusive, Instant toInclusive,
                                                 boolean addNewBalance) {
        List<WalletEntry> result = new LinkedList<>(
            walletService.getBalancesWithHoles(fromExclusive, toInclusive, true));
        WalletEntry first = result.get(0);
        if (addNewBalance) {
            result.set(0, new WalletEntry(first.getDatetime().plus(1, ChronoUnit.HOURS),
                first.getAmount()));
        } else {
            // don't need exclusive from
            result.remove(0);
        }
        return result;
    }

    @PreDestroy
    public void shutDownExecutors() {
        shutDown(updateBalanceTaskScheduler);
        shutDown(parallelBalanceUpdateExecutor);
    }

    private void shutDown(ExecutorService service) {
        service.shutdown();
        try {
            if (!service.awaitTermination(60, TimeUnit.SECONDS)) {
                log.warn("Failed to shutdown gracefully");
                service.shutdownNow();
            }
        } catch (Exception e) {
            log.error("Something went wrong with executor service", e);
            try {
                service.shutdownNow();
            } catch (Exception ignored) {

            }
        }
    }

    @AllArgsConstructor
    private static class Range {
        Instant fromExclusive;
        Instant toInclusive;
    }
}
