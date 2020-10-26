package com.community.bitcoinwallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({ServiceConfig.class})
public class AppConfig {
}
