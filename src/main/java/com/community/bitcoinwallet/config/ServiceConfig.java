package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@RequiredArgsConstructor
@Import(RepositoryConfig.class)
public class ServiceConfig {

    private final RepositoryConfig repositoryConfig;

    @Bean
    public WalletService walletService() {
        return new WalletService(repositoryConfig.walletRepository());
    }
}
