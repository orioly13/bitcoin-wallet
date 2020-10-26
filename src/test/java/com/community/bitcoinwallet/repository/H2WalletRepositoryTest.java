package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import com.community.bitcoinwallet.config.H2JdbcConfig;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = {BitcoinWalletApplication.class, H2JdbcConfig.class})
public class H2WalletRepositoryTest extends GenericRepositoryTest {

    @Autowired
    private H2JdbcConfig h2JdbcConfig;

    @Override
    public WalletRepository getRepository() {
        return new H2WalletRepository(h2JdbcConfig.h2NamedParameterJdbcTemplate());
    }

}