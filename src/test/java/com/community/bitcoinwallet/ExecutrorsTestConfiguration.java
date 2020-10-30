package com.community.bitcoinwallet;

import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
@Profile("test")
public class ExecutrorsTestConfiguration {

    @Bean
    public ScheduledExecutorService updateBalanceTaskScheduler() {
        return Mockito.mock(ScheduledExecutorService.class);
    }

    @Bean
    public ExecutorService parallelUpdateExecutorService() {
        return Mockito.mock(ExecutorService.class);
    }
}
