package com.goodthingjar.security;

import java.security.Principal;
import java.util.UUID;

public record UserPrincipal(UUID userId, String email) implements Principal {

    @Override
    public String getName() {
        return email;
    }
}

