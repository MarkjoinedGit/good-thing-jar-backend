package com.goodthingjar.repository;

import com.goodthingjar.entity.UnlockDateProposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UnlockDateProposalRepository extends JpaRepository<UnlockDateProposal, UUID> {
}
