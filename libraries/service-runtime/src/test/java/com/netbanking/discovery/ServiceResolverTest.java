package com.netbanking.discovery;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.springframework.cloud.client.DefaultServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

class ServiceResolverTest {
    @Test
    void resolvesOnlyASingleDiscoveredInstance() {
        var discovery = mock(DiscoveryClient.class);
        var resolver = new ServiceResolver(discovery);
        var instance =
                new DefaultServiceInstance(
                        "a", "accounts-ledger-service", "localhost", 8082, false);
        when(discovery.getInstances("accounts-ledger-service")).thenReturn(List.of(instance));
        assertThat(resolver.resolve("accounts-ledger-service").toString())
                .isEqualTo("http://localhost:8082");
        when(discovery.getInstances("accounts-ledger-service")).thenReturn(List.of());
        assertThatThrownBy(() -> resolver.resolve("accounts-ledger-service"))
                .isInstanceOf(ResponseStatusException.class);
        when(discovery.getInstances("accounts-ledger-service"))
                .thenReturn(List.of(instance, instance));
        assertThatThrownBy(() -> resolver.resolve("accounts-ledger-service"))
                .isInstanceOf(ResponseStatusException.class);
    }
}
