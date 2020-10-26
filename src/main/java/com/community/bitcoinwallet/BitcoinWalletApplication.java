package com.community.bitcoinwallet;

import com.community.bitcoinwallet.config.AppConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.webservices.WebServicesAutoConfiguration;
import org.springframework.context.annotation.Import;

@SpringBootApplication(exclude = WebServicesAutoConfiguration.class)
@Import(AppConfig.class)
public class BitcoinWalletApplication {

	public static void main(String[] args) {
		SpringApplication.run(BitcoinWalletApplication.class, args);
	}

}
