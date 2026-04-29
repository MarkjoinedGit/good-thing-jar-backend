package com.goodthingjar.repository;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.Jar;
import com.goodthingjar.entity.enums.JarStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JarRepository extends JpaRepository<Jar, UUID> {
    Optional<Jar> findTopByCoupleAndStatusOrderByCreatedAtDesc(Couple couple, JarStatus jarStatus);

    List<Jar> findByCouple(Couple couple);
}
