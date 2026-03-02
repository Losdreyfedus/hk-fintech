package com.hk-fintech.hk.common.logging;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnWebApplication
public class LoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(LoggingConfigProvider.class)
    public LoggingConfigProvider defaultLoggingConfigProvider() {
        return new DefaultLoggingConfigProvider();
    }

    @Bean
    public HttpLoggingFilter httpLoggingFilter(LoggingConfigProvider configProvider) {
        return new HttpLoggingFilter(configProvider);
    }

    @Bean
    @ConditionalOnClass(name = "feign.Client")
    public FeignLoggingInterceptor feignLoggingInterceptor(LoggingConfigProvider configProvider) {
        return new FeignLoggingInterceptor(configProvider);
    }

    @Bean
    @ConditionalOnClass(name = "feign.Client")
    public CorrelationIdInterceptor correlationIdInterceptor() {
        return new CorrelationIdInterceptor();
    }

    @Bean
    @ConditionalOnClass(name = "feign.Client")
    public AuthForwardingInterceptor authForwardingInterceptor() {
        return new AuthForwardingInterceptor();
    }
}
