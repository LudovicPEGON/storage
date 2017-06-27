package com.everteam.storage.service;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurerSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class AsyncConfiguration extends AsyncConfigurerSupport{
	private static final String THREAD_NAME_PREFIX = "extractor-agent-Executor-";
	
	@Value("${everteam.async.core-pool-size:#{2}}")
    private int corePoolSize;

    @Value("${everteam.async.max-pool-size:#{10}}")
    private int maxPoolSize;

    @Value("${everteam.async.queue-capacity:#{10000}}")
    private int queueCapacity;

    @Value("${everteam.async.timeout:#{100}}")
    private int threadTimeout;
	
	
	
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(THREAD_NAME_PREFIX);
        executor.setKeepAliveSeconds(threadTimeout);
        executor.initialize();
        
        return executor;        
    }
}
