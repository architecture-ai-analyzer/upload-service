package com.fiap.hackathon.upload_service.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableAsync
public class SecurityConfig {

    private final GatewayTrustProperties gatewayTrustProperties;
    private final GatewayTrustAuthenticationFilter gatewayTrustAuthenticationFilter;
    private final UploadRateLimitFilter uploadRateLimitFilter;
    private final CorsProperties corsProperties;

    public SecurityConfig(
            GatewayTrustProperties gatewayTrustProperties,
            GatewayTrustAuthenticationFilter gatewayTrustAuthenticationFilter,
            UploadRateLimitFilter uploadRateLimitFilter,
            CorsProperties corsProperties
    ) {
        this.gatewayTrustProperties = gatewayTrustProperties;
        this.gatewayTrustAuthenticationFilter = gatewayTrustAuthenticationFilter;
        this.uploadRateLimitFilter = uploadRateLimitFilter;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (gatewayTrustProperties.isEnabled()) {
            if (gatewayTrustProperties.getSharedSecret() == null || gatewayTrustProperties.getSharedSecret().isBlank()) {
                throw new IllegalStateException("app.security.gateway-trust.shared-secret must be configured when gateway-trust is enabled");
            }
            http.addFilterBefore(gatewayTrustAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(uploadRateLimitFilter, GatewayTrustAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth
                            .requestMatchers(
                                    "/actuator/health",
                                    "/actuator/health/**",
                                    "/swagger-ui/**",
                                    "/v3/api-docs/**"
                            ).permitAll()
                            .requestMatchers(HttpMethod.POST, "/v1/uploads", "/v1/projects").hasAuthority("SCOPE_upload:write")
                            .requestMatchers(HttpMethod.GET, "/v1/projects/**").hasAnyAuthority("SCOPE_upload:read", "SCOPE_upload:write")
                            .requestMatchers(HttpMethod.GET, "/v1/uploads", "/v1/uploads/**").hasAnyAuthority("SCOPE_upload:read", "SCOPE_upload:write")
                            .requestMatchers(HttpMethod.GET, "/v1/audit/**").hasAnyAuthority("SCOPE_audit:read", "SCOPE_admin")
                            .anyRequest().authenticated()
                    );
        } else {
            http.addFilterBefore(uploadRateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        }

        return http.build();
    }

    @Bean
    public FilterRegistrationBean<GatewayTrustAuthenticationFilter> gatewayTrustAuthenticationFilterRegistration(
            GatewayTrustAuthenticationFilter filter
    ) {
        FilterRegistrationBean<GatewayTrustAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<UploadRateLimitFilter> uploadRateLimitFilterRegistration(UploadRateLimitFilter filter) {
        FilterRegistrationBean<UploadRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.getAllowedOrigins());
        config.setAllowedMethods(corsProperties.getAllowedMethods());
        config.setAllowedHeaders(corsProperties.getAllowedHeaders());
        config.setAllowCredentials(corsProperties.isAllowCredentials());
        config.setMaxAge(corsProperties.getMaxAge());

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
