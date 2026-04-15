package com.veriq.veriqbe3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync; // 🌟 여기로 이사 왔습니다!
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
@Configuration
@EnableAsync

public class AsyncConfig {
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5); //기본 스레드 수
        executor.setMaxPoolSize(20); //최대 스레드 수
        executor.setQueueCapacity(50); //대기열 크기
        executor.setThreadNamePrefix("VeriQ-Async-");
        executor.initialize();
        return executor;
    }
}
