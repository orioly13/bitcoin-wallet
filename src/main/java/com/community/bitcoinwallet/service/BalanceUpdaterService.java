package com.community.bitcoinwallet.service;


import com.community.bitcoinwallet.model.WalletEntry;
import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.util.DateAndAmountUtils;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.log4j.Log4j2;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import javax.annotation.PreDestroy;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

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
        Instant from = atStartOfHour(event.getDatetime());
        Instant to = repository.getLastBalanceTs()
            .map(DateAndAmountUtils::atEndOfHour)
            .orElse(from.plus(1, ChronoUnit.HOURS));
        if (!to.isAfter(from)) {
            to = from.plus(1, ChronoUnit.HOURS);
        }
        long wholeHours = countHoursBetweenFromAndTo(from, to);
        List<Range> ranges = new ArrayList<>();
        long step = wholeHours / threadCount <= 1 ? 1 : wholeHours / threadCount;
        Instant start = from;
        for (int i = 0; i < wholeHours; i += step) {
            Instant end = start.plus(step, ChronoUnit.HOURS);
            ranges.add(new Range(start, end));
            start = end;
        }
        if (wholeHours > threadCount && wholeHours % threadCount > 1) {
            ranges.add(new Range(start, start.plus(wholeHours % threadCount, ChronoUnit.HOURS)));
        }
        if (parallel) {
            try {
                processRangesInParallel(ranges);
            } catch (Exception e) {
                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            }
        } else {
            processRangesSequentially(ranges);
        }
        log.info("Finished processing event {}", event);
    }

    private void processRangesSequentially(List<Range> ranges) {
        for (int i = 0; i < ranges.size(); i++) {
            repository.mergeIntoBalances(
                getBalancesToMerge(ranges.get(i).fromExclusive, ranges.get(i).toInclusive, i == 0));
        }
    }

    private void processRangesInParallel(List<Range> ranges) {

        List<Future<?>> futures = new ArrayList<>(ranges.size());
        for (int i = 0; i < ranges.size(); i++) {
            Instant fromExclusive = ranges.get(i).fromExclusive;
            Instant toInclusive = ranges.get(i).toInclusive;
            boolean addNewBalance = i == 0;
            futures.add(parallelBalanceUpdateExecutor.submit(() ->
                repository.mergeIntoBalances(
                    getBalancesToMerge(fromExclusive, toInclusive, addNewBalance))));
        }
        try {
            for (Future f : futures) {
                f.get();
            }
        } catch (Exception e) {
            log.error("Problem occurred on balance update", e);
            throw new RuntimeException(e);
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
        try {
            service.shutdown();
            if (!service.awaitTermination(5, TimeUnit.SECONDS)) {
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
