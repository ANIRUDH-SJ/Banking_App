package com.netbanking.discovery;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Component
public class ServiceResolver {
    private final DiscoveryClient discovery;

    public ServiceResolver(DiscoveryClient discovery) {
        this.discovery = discovery;
    }

    public URI resolve(String service) {
        var instances = discovery.getInstances(service);
        if (instances.size() != 1)
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Exactly one registered instance is required for " + service);
        URI uri = instances.get(0).getUri();
        if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "Invalid service URI");
        return uri;
    }
}
