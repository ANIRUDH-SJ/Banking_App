package com.netbanking.security;

import com.netbanking.common.api.ApiErrorWriter;
import com.netbanking.common.api.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfiguration {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final InternalAuthenticationFilter internalFilter;
    private final CorrelationIdFilter correlationIdFilter;
    private final ApiErrorWriter errors;

    public SecurityConfiguration(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            InternalAuthenticationFilter internalFilter,
            CorrelationIdFilter correlationIdFilter,
            ApiErrorWriter errors) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.internalFilter = internalFilter;
        this.correlationIdFilter = correlationIdFilter;
        this.errors = errors;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @org.springframework.beans.factory.annotation.Value(
                            "${app.security.cors.allowed-origins}")
                    String origins) {

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(origins.split(",")));
        configuration.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(
                List.of("Authorization", "Content-Type", CorrelationIdFilter.HEADER));
        configuration.setExposedHeaders(List.of(CorrelationIdFilter.HEADER));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .cors(cors -> {})
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        authorize ->
                                authorize
                                        .dispatcherTypeMatchers(
                                                jakarta.servlet.DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                "/api/v1/health",
                                                "/api/v1/auth/**",
                                                "/actuator/health/**")
                                        .permitAll()
                                        .requestMatchers("/internal/**")
                                        .hasRole("SERVICE")
                                        .requestMatchers("/api/v1/admin/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/**")
                                        .hasRole("CUSTOMER")
                                        .anyRequest()
                                        .authenticated())
                .exceptionHandling(
                        errors ->
                                errors
                                        .authenticationEntryPoint(
                                                (request, response, failure) ->
                                                        this.errors.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse.SC_UNAUTHORIZED,
                                                                "UNAUTHORIZED",
                                                                "Authentication is required."))
                                        .accessDeniedHandler(
                                                (request, response, failure) ->
                                                        this.errors.write(
                                                                request,
                                                                response,
                                                                HttpServletResponse.SC_FORBIDDEN,
                                                                "FORBIDDEN",
                                                                "You do not have permission to access this resource.")))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(internalFilter, JwtAuthenticationFilter.class)
                .addFilterBefore(correlationIdFilter, InternalAuthenticationFilter.class)
                .build();
    }
}
