package de.coldtea.verborum.msgateway.common.config;

import org.springframework.cloud.gateway.server.mvc.filter.HttpHeadersFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

import java.util.Locale;
import java.util.Set;

/**
 * P5-03: nothing identity-shaped is forwarded to a service. Who the caller is comes from the JWT and
 * nowhere else — every service validates the token itself and takes the owner from its subject. A
 * header such as `X-User-Id` sent by a client would be worthless today, and dangerous the day a service
 * starts to trust it, so the gateway removes the whole family on the way in. The gateway adds none of
 * its own either.
 * <p>
 * `Authorization` and `X-Device-Id` are not identity headers in this sense and pass through: the first
 * is the token each service re-validates, the second names an app install for push targeting.
 */
@Component
public class IdentityHeaderFilter implements HttpHeadersFilter.RequestHttpHeadersFilter, Ordered {

    private static final Set<String> BLOCKED_PREFIXES = Set.of(
            "x-user", "x-auth", "x-authenticated", "x-jwt", "x-keycloak", "x-verborum",
            "x-forwarded-user", "x-forwarded-email", "x-forwarded-preferred", "x-forwarded-groups",
            "x-remote-user", "x-claim");

    private static final Set<String> BLOCKED_NAMES = Set.of("remote-user", "remote-addr-user");

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public HttpHeaders apply(HttpHeaders input, ServerRequest request) {
        HttpHeaders filtered = new HttpHeaders();
        input.forEach((name, values) -> {
            if (!isIdentityHeader(name)) {
                filtered.addAll(name, values);
            }
        });
        return filtered;
    }

    static boolean isIdentityHeader(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return BLOCKED_NAMES.contains(lower) || BLOCKED_PREFIXES.stream().anyMatch(lower::startsWith);
    }
}
