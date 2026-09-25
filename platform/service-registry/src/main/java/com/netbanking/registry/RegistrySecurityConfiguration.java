package com.netbanking.registry;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class RegistrySecurityConfiguration {
    @Bean
    PasswordEncoder registryPasswordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService registryUsers(
            @Value("${app.registry.username}") String username,
            @Value("${app.registry.password}") String password,
            PasswordEncoder passwordEncoder) {
        if (username == null || username.isBlank() || username.startsWith("REPLACE_")) {
            throw new IllegalStateException(
                    "A non-placeholder app.registry.username must be configured.");
        }
        if (password == null || password.length() < 16 || password.startsWith("REPLACE_")) {
            throw new IllegalStateException(
                    "app.registry.password must contain at least 16 characters.");
        }
        return new InMemoryUserDetailsManager(
                User.withUsername(username)
                        .password(passwordEncoder.encode(password))
                        .roles("EUREKA_CLIENT")
                        .build());
    }

    @Bean
    SecurityFilterChain registrySecurityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.ignoringRequestMatchers("/eureka/**"))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .requestMatchers("/actuator/health", "/actuator/health/**")
                                        .permitAll()
                                        .anyRequest()
                                        .authenticated())
                .httpBasic(Customizer.withDefaults())
                .build();
    }
}
