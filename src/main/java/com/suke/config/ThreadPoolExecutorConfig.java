package com.suke.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @author 自然醒
 * @version 1.0
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

            /**
             * 每当一个任务被提交到线程池中,线程池会调用这个方法来创建一个新的线程来执行任务
             * 如果这份方法被调用时,传递一个null 参数,那么这个方法会抛出一个NullPointerException
             * @param r a runnable to be executed by new thread instance
             * @return
             */
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
        //核心线程数为2，最大线程数为4，队列长度为10，线程空闲时间100秒，线程工厂threadFactory，拒绝策略AbortPolicy
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
}
