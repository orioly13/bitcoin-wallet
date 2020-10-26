package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.repository.InMemoryWalletRepository;
import com.community.bitcoinwallet.repository.WalletRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class RepositoryConfig {

    @Bean("walletReporitory")
    @Profile("in-memory")
    public WalletRepository walletRepositoryInMemory() {
        return new InMemoryWalletRepository();
    }

    @Bean("walletReporitory")
    @Profile("h2")
    public WalletRepository walletRepositoryH2() {
        return new InMemoryWalletRepository();
    }
}
