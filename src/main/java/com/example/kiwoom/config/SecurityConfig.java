package com.example.kiwoom.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.userdetails.MapReactiveUserDetailsService;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.HttpStatusServerEntryPoint;
import org.springframework.security.web.server.authentication.logout.HttpStatusReturningServerLogoutSuccessHandler;

@Configuration
public class SecurityConfig {
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    MapReactiveUserDetailsService users(@Value("${app.security.username}") String username,
                                        @Value("${app.security.password}") String password,
                                        PasswordEncoder encoder) {
        return new MapReactiveUserDetailsService(User.withUsername(username)
                .password(encoder.encode(password)).roles("USER", "ADMIN").build());
    }

    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/", "/index.html", "/assets/**", "/actuator/health").permitAll()
                        .pathMatchers("/actuator/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").hasRole("ADMIN")
                        .pathMatchers("/api/watchlist/**", "/api/portfolio/**", "/api/alerts/**").authenticated()
                        .pathMatchers("/api/kiwoom/**").authenticated()
                        .anyExchange().permitAll())
                .exceptionHandling(errors -> errors.authenticationEntryPoint(
                        new HttpStatusServerEntryPoint(HttpStatus.UNAUTHORIZED)))
                .formLogin(form -> form.authenticationSuccessHandler((webFilterExchange, authentication) -> {
                    webFilterExchange.getExchange().getResponse().setStatusCode(HttpStatus.NO_CONTENT);
                    return webFilterExchange.getExchange().getResponse().setComplete();
                }))
                .logout(logout -> logout.logoutSuccessHandler(
                        new HttpStatusReturningServerLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
                .build();
    }
}
