package com.community.bitcoinwallet.controller;

import com.community.bitcoinwallet.BitcoinWalletApplication;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = {BitcoinWalletApplication.class},
    properties = {"spring.profiles.active=h2","bitcoin-wallet.balance.async-balance=false"})
public class H2WalletControllerTest extends WalletControllerTest{
}
