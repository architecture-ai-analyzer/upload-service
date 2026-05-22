package com.fiap.hackathon.upload_service.config.observability;

import com.timgroup.statsd.StatsDClient;
import io.opentracing.Tracer;
import io.opentracing.util.GlobalTracer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DatadogConfig {

    @Value("${datadog.statsd.host:localhost}")
    private String statsdHost;

    @Value("${datadog.statsd.port:8125}")
    private int statsdPort;

    @Bean
    public Tracer tracer() {
        return GlobalTracer.get();
    }

    @Bean
    public StatsDClient statsDClient() {
        return new com.timgroup.statsd.NonBlockingStatsDClientBuilder()
                .prefix("upload")
                .hostname(statsdHost)
                .port(statsdPort)
                .build();
    }
}
