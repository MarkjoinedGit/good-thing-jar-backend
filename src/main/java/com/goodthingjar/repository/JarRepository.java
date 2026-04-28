package com.goodthingjar.repository;

import com.goodthingjar.entity.Jar;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface JarRepository extends JpaRepository<Jar, UUID> {
}
