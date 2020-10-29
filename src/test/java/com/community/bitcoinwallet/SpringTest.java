package com.community.bitcoinwallet;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {ExecutrorsTestConfiguration.class, BitcoinWalletApplication.class},
properties = "spring.profiles.active=test")
public class SpringTest {
}
