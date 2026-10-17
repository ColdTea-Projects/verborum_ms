package de.coldtea.verborum.msgateway.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * P5-06: the one place that answers CORS. A browser app on another origin asks the gateway first (the
 * preflight); the services never see that request and send no CORS headers of their own.
 * <p>
 * Origins are an exact list per environment (`CORS_ALLOWED_ORIGINS`) — no wildcard, no pattern. Requests carry
 * a Bearer token in a header, never a cookie, so credentials stay off. Spring Security runs this filter
 * before authentication, so a preflight needs no token and a 401 for an allowed origin still carries the
 * CORS headers (the browser app must be able to read that 401 to refresh its token).
 */
@Configuration
public class CorsConfig {

    private static final List<String> METHODS = List.of("GET", "POST", "PUT", "DELETE", "OPTIONS");

    private static final List<String> HEADERS = List.of(
            "Authorization", "Content-Type", "Accept", "Accept-Language", "X-Device-Id");

    private static final long PREFLIGHT_MAX_AGE_SECONDS = 3600;

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${verborum.cors.allowed-origins:}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(METHODS);
        configuration.setAllowedHeaders(HEADERS);
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(PREFLIGHT_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
