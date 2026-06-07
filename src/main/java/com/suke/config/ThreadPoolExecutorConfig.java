package com.suke.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.*;

/**
 * @author 自然醒
 * @version 1.0
 * @date 2026-05-29 02:07
 * @description 线程池配置，提供异步任务和SSE专用线程池
 */
//线程池配置类
@Configuration
public class ThreadPoolExecutorConfig {
    @Bean
    public ThreadPoolExecutor threadPoolExecutor(){
        //创建线程工厂
        ThreadFactory threadFactory = new ThreadFactory() {

            //初始化线程数
            private int count = 1;

            @Override
            public Thread newThread(@NotNull Runnable r) {
                //创建线程
                Thread thread = new Thread(r);
                //设置线程名称
                thread.setName("线程" + count);
                count++;
                return thread;
            }
        };
        //创建线程池
        ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(
          2,
          4,
          100,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(10),
          threadFactory,
          new ThreadPoolExecutor.CallerRunsPolicy()
        );
        return threadPoolExecutor;
    }


    @Bean("taskExecutor")
    public ThreadPoolTaskExecutor asyncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.initialize();
        return executor;
    }
}
