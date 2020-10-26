package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.repository.InMemoryWalletRepository;
import com.community.bitcoinwallet.repository.WalletRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@Import(H2JdbcConfig.class)
public class RepositoryConfig {

    private final H2JdbcConfig h2JdbcConfig;

    @Autowired
    public RepositoryConfig(H2JdbcConfig h2JdbcConfig) {
        this.h2JdbcConfig = h2JdbcConfig;
    }


    @Bean("walletReporitory")
    @Profile("in-memory")
    public WalletRepository walletRepositoryInMemory() {
        return new InMemoryWalletRepository();
    }

    @Bean("walletReporitory")
    @Profile("h2")
    public WalletRepository walletRepositoryH2() {
        return new H2WalletRepository(h2JdbcConfig.h2NamedParameterJdbcTemplate());
    }
}
