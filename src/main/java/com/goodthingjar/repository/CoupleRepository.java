package com.goodthingjar.repository;

import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface CoupleRepository extends JpaRepository<Couple, UUID> {
    boolean existsByUser1(User user);

    boolean existsByUser2(User user);

    Optional<Couple> findByUser1OrUser2(User user1, User user2);

    @SuppressWarnings("unused")
    java.util.List<Couple> findByUser1OrUser2OrderByPairedAtDesc(User user1, User user2);
}
