package com.community.bitcoinwallet.repository;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import org.springframework.boot.test.context.SpringBootTest;


@SpringBootTest(classes = {BitcoinWalletApplication.class},
    properties = {"spring.profiles.active=in-memory", "bitcoin-wallet.balance.async-balance=false"})
public class InMemoryWalletRepositoryTest extends GenericRepositoryTest {
}