package com.netbanking.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.discovery.EurekaClientConfig;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ServiceRegistryApplicationTests {

    @Autowired private Environment environment;
    @Autowired private EurekaClientConfig eurekaClientConfig;
    @Autowired private TestRestTemplate restTemplate;

    @Test
    void startsAsAStandaloneRegistry() {
        assertThat(environment.getProperty("spring.application.name"))
                .isEqualTo("service-registry");
        assertThat(eurekaClientConfig.shouldRegisterWithEureka()).isFalse();
        assertThat(eurekaClientConfig.shouldFetchRegistry()).isFalse();
    }

    @Test
    void protectsTheRegistryWhileKeepingHealthChecksPublic() {
        assertThat(restTemplate.getForEntity("/", String.class).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(restTemplate.getForEntity("/actuator/health", String.class).getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        restTemplate
                                .withBasicAuth("test-registry", "test-registry-password")
                                .getForEntity("/", String.class)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }
}
