package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.repository.H2WalletRepository;
import com.community.bitcoinwallet.repository.InMemoryWalletRepository;
import com.community.bitcoinwallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

@Configuration
@RequiredArgsConstructor
@Import(H2JdbcConfig.class)
public class RepositoryConfig {

    @Value("${bitcoin-wallet.balance.async-balance:false}")
    private boolean asyncBalance;

    private final H2JdbcConfig h2JdbcConfig;

    @Bean("walletRepository")
    @Profile("in-memory")
    public WalletRepository walletRepositoryInMemory() {
        return new InMemoryWalletRepository(asyncBalance);
    }

    @Bean("walletRepository")
    @Profile("h2")
    public WalletRepository walletRepositoryH2() {
        return new H2WalletRepository(h2JdbcConfig.h2NamedParameterJdbcTemplate());
    }
}
