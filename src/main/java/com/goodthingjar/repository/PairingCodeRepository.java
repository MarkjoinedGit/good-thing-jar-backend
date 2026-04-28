package com.goodthingjar.repository;

import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.PairingCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PairingCodeRepository extends JpaRepository<PairingCode, UUID> {

    boolean existsByCode(String code);

    boolean existsByGeneratedByAndStatusAndExpiresAtAfter(User user, PairingCodeStatus pairingCodeStatus, OffsetDateTime now);

    Optional<PairingCode> findByCode(String code);
}
