package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.service.WalletService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(RepositoryConfig.class)
public class ServiceConfig {

    private final RepositoryConfig repositoryConfig;

    @Autowired
    public ServiceConfig(RepositoryConfig repositoryConfig) {
        this.repositoryConfig = repositoryConfig;
    }

    @Bean
    public WalletService walletService() {
        return new WalletService(repositoryConfig.walletRepositoryInMemory());
    }
}
