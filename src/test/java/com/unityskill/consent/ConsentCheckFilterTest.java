package com.unityskill.consent;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConsentCheckFilterTest {

    @Mock ConsentRepository consentRepository;

    private ConsentCheckFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ConsentCheckFilter(consentRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setAuthenticatedUser(UUID userId) {
        var auth = new UsernamePasswordAuthenticationToken(
                userId.toString(), null, Collections.emptyList());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void filter_noConsentRecord_returns403ConsentRequired() throws Exception {
        // AC4: no consent → 403 CONSENT_REQUIRED
        setAuthenticatedUser(UUID.randomUUID());
        when(consentRepository.existsByUserId(any())).thenReturn(false);

        MockHttpServletRequest  req   = new MockHttpServletRequest("GET", "/api/v1/workspaces/123");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("CONSENT_REQUIRED");
        assertThat(chain.getRequest()).isNull();  // chain was NOT called through
    }

    @Test
    void filter_hasConsentRecord_passesThrough() throws Exception {
        // AC4: consented user → request proceeds
        setAuthenticatedUser(UUID.randomUUID());
        when(consentRepository.existsByUserId(any())).thenReturn(true);

        MockHttpServletRequest  req   = new MockHttpServletRequest("GET", "/api/v1/workspaces/123");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();  // chain WAS called through
        verifyNoMoreInteractions(consentRepository); // only existsByUserId called
    }

    @Test
    void filter_exemptConsentPath_passesThrough() throws Exception {
        // AC4: the consent endpoint itself is exempt — un-consented users must be able to POST it
        setAuthenticatedUser(UUID.randomUUID());

        MockHttpServletRequest  req   = new MockHttpServletRequest("POST", "/api/v1/users/me/consent");
        req.setServletPath("/api/v1/users/me/consent");  // MockHttpServletRequest(method, uri) does NOT set servletPath
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(consentRepository);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void filter_anonymousAuthentication_passesThrough() throws Exception {
        // Anonymous requests skip consent check — Spring Security handles 401 for protected routes
        AnonymousAuthenticationToken anon = new AnonymousAuthenticationToken(
                "key", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS")));
        SecurityContextHolder.getContext().setAuthentication(anon);

        MockHttpServletRequest  req   = new MockHttpServletRequest("GET", "/api/v1/workspaces/123");
        MockHttpServletResponse res   = new MockHttpServletResponse();
        MockFilterChain         chain = new MockFilterChain();

        filter.doFilter(req, res, chain);

        verifyNoInteractions(consentRepository);
        assertThat(chain.getRequest()).isNotNull();
    }
}
