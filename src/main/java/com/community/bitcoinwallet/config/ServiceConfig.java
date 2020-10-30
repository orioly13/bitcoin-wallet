package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.service.BalanceUpdaterService;
import com.community.bitcoinwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@RequiredArgsConstructor
@Import(RepositoryConfig.class)
public class ServiceConfig {

    @Value("${wallet.balance-update.period-millis:10}")
    private long updatePeriod;
    @Value("${wallet.balance-update.thread-count:8}")
    private int threadCount;

    private final RepositoryConfig repositoryConfig;

    @Bean
    public WalletService walletService() {
        return new WalletService(repositoryConfig.walletRepository());
    }

    @Bean
    public BalanceUpdaterService balanceUpdaterService() {
        return new BalanceUpdaterService(repositoryConfig.walletRepository(),
            walletService(), updatePeriod, updateBalanceTaskScheduler(),
            parallelUpdateExecutorService(), threadCount);
    }

    @Bean
    @Profile("!test")
    public ScheduledExecutorService updateBalanceTaskScheduler() {
        return Executors.newSingleThreadScheduledExecutor();
    }

    @Bean
    @Profile("!test")
    public ExecutorService parallelUpdateExecutorService() {
        return Executors.newFixedThreadPool(threadCount);
    }
}
