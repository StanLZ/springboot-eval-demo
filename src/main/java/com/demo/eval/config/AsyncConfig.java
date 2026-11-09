package com.demo.eval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * 评测任务线程池。
 * 单机 demo 使用单线程顺序执行，避免并发压垮被测 RAG 服务；
 * 后续要跑大数据集时可调大 core/max pool。
 */
@Configuration
public class AsyncConfig {

    @Bean("evalExecutor")
    public Executor evalExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(2);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("eval-run-");
        executor.initialize();
        return executor;
    }
}
