package com.marketguard.config;

import java.nio.charset.StandardCharsets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

/** Operator authentication and browser security headers. */
@Configuration
public class SecurityConfig {

    private static final String PRODUCTION_CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' https://cdn.jsdelivr.net https://unpkg.com; "
            + "style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; "
            + "object-src 'none'; base-uri 'self'; form-action 'none'; frame-ancestors 'none'";
    private static final String LOCAL_CONTENT_SECURITY_POLICY = "default-src 'self'; "
            + "script-src 'self' 'unsafe-inline' 'unsafe-eval' https://cdn.jsdelivr.net https://unpkg.com; "
            + "style-src 'self' 'unsafe-inline'; connect-src 'self'; img-src 'self' data:; "
            + "object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'self'";

    @Bean
    SecurityFilterChain applicationSecurity(HttpSecurity http,
                                            MarketGuardSecurityProperties properties,
                                            ApiRateLimitFilter apiRateLimitFilter) throws Exception {
        http.csrf(csrf -> {
                    if (!properties.enabled()) {
                        csrf.ignoringRequestMatchers("/h2-console/**");
                    }
                })
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(properties.enabled()
                                ? PRODUCTION_CONTENT_SECURITY_POLICY
                                : LOCAL_CONTENT_SECURITY_POLICY))
                        .permissionsPolicyHeader(permissions -> permissions.policy("camera=(), microphone=(), geolocation=()"))
                        .frameOptions(frame -> {
                            if (properties.enabled()) {
                                frame.deny();
                            } else {
                                frame.sameOrigin();
                            }
                        }))
                .addFilterBefore(apiRateLimitFilter, BasicAuthenticationFilter.class);

        if (properties.enabled()) {
            http.authorizeHttpRequests(authorize -> authorize
                            .requestMatchers("/actuator/health/liveness", "/actuator/health/readiness").permitAll()
                            .anyRequest().authenticated())
                    .httpBasic(basic -> basic.authenticationEntryPoint((request, response, exception) -> {
                        response.setStatus(401);
                        response.setHeader("WWW-Authenticate", "Basic realm=\"marketguard\"");
                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                        response.getWriter().write(
                                "{\"code\":\"AUTHENTICATION_REQUIRED\",\"message\":\"Authentication is required\"}");
                    }));
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .httpBasic(Customizer.withDefaults());
        }
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService operatorUsers(MarketGuardSecurityProperties properties, PasswordEncoder encoder) {
        if (!properties.enabled()) {
            return new InMemoryUserDetailsManager();
        }
        return new InMemoryUserDetailsManager(User.withUsername(properties.username())
                .password(encoder.encode(properties.password()))
                .roles("OPERATOR")
                .build());
    }
}
