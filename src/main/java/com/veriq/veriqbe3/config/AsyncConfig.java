package com.veriq.veriqbe3.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync; // 🌟 여기로 이사 왔습니다!
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

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
        // 🌟큐가 꽉 차면, 에러 내지 말고 요청한 스레드가 직접 처리하게 함 (데이터 유실 방지)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());

        // 서버 종료 시, 진행 중인 작업이(파이썬 분석)끝날 때까지 기다려줌,분석 결과 서버꺼지기전 db에 저장케함
        executor.setWaitForTasksToCompleteOnShutdown(true);

        //  단, 최대 30초까지만 기다려주고 강제 종료함 (서버 무한 대기 방지)
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
