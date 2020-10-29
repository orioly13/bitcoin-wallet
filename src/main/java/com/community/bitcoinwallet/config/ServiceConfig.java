package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@RequiredArgsConstructor
@Import(RepositoryConfig.class)
public class ServiceConfig {

    @Value("${wallet.balance-update.period-millis:10}")
    private long updatePeriod;
    @Value("${wallet.balance-update.thread-count:8}")
    private int batchSize;

    private final RepositoryConfig repositoryConfig;

    @Bean
    public WalletService walletService() {
        return new WalletService(repositoryConfig.walletRepository());
    }
}
