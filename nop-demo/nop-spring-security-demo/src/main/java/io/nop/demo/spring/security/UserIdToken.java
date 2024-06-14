package io.nop.demo.spring.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Collections;

public class UserIdToken extends AbstractAuthenticationToken {
    private final String userId;

    public UserIdToken(String userId) {
        super(Collections.emptyList());
        this.userId = userId;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return userId;
    }

    @Override
    public Object getPrincipal() {
        return userId;
    }
}
