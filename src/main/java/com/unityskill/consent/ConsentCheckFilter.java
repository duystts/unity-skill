package com.unityskill.consent;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * NOT @Component — registered manually by SecurityConfig to prevent double-registration
 * in @WebMvcTest contexts (same pattern as JwtAuthFilter).
 *
 * Runs AFTER JwtAuthFilter (SecurityContext already populated).
 * Skips check when consentRepository is null — happens in @WebMvcTest contexts
 * where ConsentRepository is not available; see SecurityConfig for wiring details.
 */
public class ConsentCheckFilter extends OncePerRequestFilter {

    /** Paths that are exempt from consent enforcement. */
    private static final Set<String> EXEMPT_PREFIXES = Set.of(
            "/api/v1/auth/",
            "/api/v1/public/",
            "/api/v1/webhooks/",
            "/actuator/",
            "/ws/"
    );

    /** Exact path of the consent endpoint itself — must be accessible without consent. */
    private static final String CONSENT_PATH = "/api/v1/users/me/consent";

    /** May be null in @WebMvcTest contexts. Filter is a no-op when null. */
    private final ConsentRepository consentRepository;

    public ConsentCheckFilter(ConsentRepository consentRepository) {
        this.consentRepository = consentRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // No-op when repository unavailable (e.g., @WebMvcTest contexts)
        if (consentRepository == null) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getServletPath();

        // Exempt: consent endpoint itself and public paths
        if (CONSENT_PATH.equals(path) || EXEMPT_PREFIXES.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Only check authenticated (non-anonymous) users
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = (String) authentication.getPrincipal();
        if (!consentRepository.existsByUserId(UUID.fromString(userId))) {
            response.setStatus(HttpStatus.FORBIDDEN.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"status\":403,\"error\":\"CONSENT_REQUIRED\"," +
                    "\"message\":\"Consent required before accessing the platform\"," +
                    "\"timestamp\":\"" + Instant.now() + "\"}"
            );
            return;
        }

        filterChain.doFilter(request, response);
    }
}
