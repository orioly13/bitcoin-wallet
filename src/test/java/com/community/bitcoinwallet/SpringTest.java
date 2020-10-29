package com.community.bitcoinwallet;

import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = {BitcoinWalletApplication.class, ExecutrorsTestConfiguration.class},
    properties = "spring.profiles.active=test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
public class SpringTest {
}
