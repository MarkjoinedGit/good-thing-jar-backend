package com.goodthingjar.unit.security;

import com.goodthingjar.security.SecurityUtils;
import com.goodthingjar.security.UserPrincipal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldReturnCurrentUserIdWhenPrincipalIsUserPrincipal() {
        UUID expectedUserId = UUID.randomUUID();
        UserPrincipal principal = new UserPrincipal(expectedUserId, "user@example.com");

        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            principal,
            null,
            AuthorityUtils.NO_AUTHORITIES
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UUID actualUserId = SecurityUtils.currentUserId();

        assertEquals(expectedUserId, actualUserId);
    }

    @Test
    void shouldThrowWhenNoAuthenticationPresent() {
        assertThrows(AuthenticationCredentialsNotFoundException.class, SecurityUtils::currentUserId);
    }

    @Test
    void shouldThrowWhenPrincipalIsNotUserPrincipal() {
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
            "user@example.com",
            null,
            AuthorityUtils.NO_AUTHORITIES
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        assertThrows(AuthenticationCredentialsNotFoundException.class, SecurityUtils::currentUserId);
    }
}

