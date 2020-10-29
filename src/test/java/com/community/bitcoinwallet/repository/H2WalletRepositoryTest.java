package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;


@RunWith(SpringRunner.class)
@SpringBootTest(classes = {BitcoinWalletApplication.class},
    properties = {"spring.profiles.active=h2","bitcoin-wallet.balance.async-balance=false"})
@Transactional
public class H2WalletRepositoryTest extends GenericRepositoryTest {

    @Autowired
    private WalletRepository walletRepository;

    @Override
    public WalletRepository getRepository() {
        return walletRepository;
    }

}