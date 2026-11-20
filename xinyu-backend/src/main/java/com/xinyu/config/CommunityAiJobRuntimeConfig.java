package com.xinyu.config;

import com.xinyu.mapper.AiJobMapper;
import com.xinyu.mapper.CommunityPostMapper;
import com.xinyu.mapper.TopicMapper;
import com.xinyu.service.AiJobRuntime;
import com.xinyu.service.AiReplyLogService;
import com.xinyu.service.AiServiceClient;
import com.xinyu.service.CommunityAiClient;
import com.xinyu.service.CommunityReplyJobHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

/** 在 worker 进程内为社区回复装配独立执行槽，复用生命周期与有界数据源。 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "AI_WORKER_ENABLED", havingValue = "true")
public class CommunityAiJobRuntimeConfig {
    @Bean
    public AiJobRuntime communityAiJobRuntime(
            AiJobMapper jobs, CommunityPostMapper posts, TopicMapper topics, CommunityAiClient client,
            AiReplyLogService logs, PlatformTransactionManager manager, DataSource dataSource,
            @Value("${AI_COMMUNITY_CONCURRENCY:3}") int concurrency) {
        AiJobRuntimeConfig.requireBoundedDatabase(dataSource);
        CommunityReplyJobHandler handler = new CommunityReplyJobHandler(jobs, posts, topics, client, logs);
        return new AiJobRuntime(handler::recoverInterrupted, handler, client, manager, concurrency,
                AiJobRuntimeConfig.SCAN_INTERVAL, AiJobRuntimeConfig.SHUTDOWN_TIMEOUT,
                AiServiceClient.Limits.COMMUNITY.total());
    }
}
