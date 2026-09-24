package com.netbanking.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.discovery.EurekaClientConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ServiceRegistryApplicationTests {

    @Autowired private Environment environment;
    @Autowired private EurekaClientConfig eurekaClientConfig;

    @Test
    void startsAsAStandaloneRegistry() {
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("service-registry");
        assertThat(eurekaClientConfig.shouldRegisterWithEureka()).isFalse();
        assertThat(eurekaClientConfig.shouldFetchRegistry()).isFalse();
    }
}
