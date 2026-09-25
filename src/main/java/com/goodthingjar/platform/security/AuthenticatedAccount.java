package com.goodthingjar.platform.security;

import java.util.UUID;

public record AuthenticatedAccount(UUID accountId, UUID sessionId) {}
