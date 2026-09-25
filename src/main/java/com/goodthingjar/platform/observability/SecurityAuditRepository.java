package com.goodthingjar.platform.observability;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityAuditRepository extends JpaRepository<SecurityAuditEventEntity, UUID> {}
