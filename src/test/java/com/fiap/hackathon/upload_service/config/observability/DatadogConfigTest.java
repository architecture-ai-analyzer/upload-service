package com.fiap.hackathon.upload_service.config.observability;

import com.timgroup.statsd.StatsDClient;
import io.opentracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestPropertySource(properties = {
        "dd.trace.enabled=false",
        "datadog.statsd.host=localhost",
        "datadog.statsd.port=8125"
})
class DatadogConfigTest {

    @Autowired
    private Tracer tracer;

    @Autowired
    private StatsDClient statsDClient;

    @Test
    void contextLoads_datadogBeansAreRegistered() {
        assertThat(tracer).isNotNull();
        assertThat(statsDClient).isNotNull();
    }
}
