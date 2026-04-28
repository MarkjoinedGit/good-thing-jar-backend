package com.goodthingjar.entity;

import com.goodthingjar.entity.enums.UnlockDateProposalStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "unlock_date_proposals",
    indexes = {
        @Index(name = "idx_unlock_date_proposals_jar_status", columnList = "jar_id, status"),
        @Index(name = "idx_unlock_date_proposals_propose_by", columnList = "proposed_by"),
        @Index(name = "idx_unlock_date_proposals_expires_at", columnList = "expires_at")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnlockDateProposal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "jar_id", nullable = false)
    private Jar jar;

    @ManyToOne
    @JoinColumn(name = "proposed_by", nullable = false)
    private User proposedBy;

    @ManyToOne
    @JoinColumn(name = "approved_by")
    private User approvedBy;

    @Column(name = "new_unlocks_at", nullable = false)
    private OffsetDateTime newUnlocksAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UnlockDateProposalStatus status;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "rejection_reason", length = 255)
    private String rejectionReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "responded_at")
    private OffsetDateTime respondedAt;
}

