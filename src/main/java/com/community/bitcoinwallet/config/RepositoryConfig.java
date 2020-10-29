package com.community.bitcoinwallet.config;

import com.community.bitcoinwallet.repository.H2WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@RequiredArgsConstructor
@Import(H2JdbcConfig.class)
public class RepositoryConfig {

    private final H2JdbcConfig h2JdbcConfig;

    @Bean
    public H2WalletRepository walletRepository() {
        return new H2WalletRepository(h2JdbcConfig.h2NamedParameterJdbcTemplate());
    }
}
