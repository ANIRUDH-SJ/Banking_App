package com.netbanking.auth.api;
import java.util.Set;
public record AuthenticationResponse(String accessToken, String tokenType, Long userId, String username, Set<String> roles) { }
