package com.goodthingjar.entity;

import com.goodthingjar.entity.enums.JarStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "jars",
    indexes = {
            @Index(name = "idx_jars_couple_status", columnList = "couple_id, status"),
            @Index(name = "idx_jars_unlocks_at", columnList = "unlocks_at"),
            @Index(name = "idx_jars_couple_status_unlocks_at", columnList = "couple_id, status, unlocks_at"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Jar {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "couple_id", nullable = false)
    private Couple couple;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JarStatus status;

    @Column(name = "unlocks_at", nullable = false)
    private OffsetDateTime unlocksAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    @Column(name = "entry_count")
    private int entryCount;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "jar")
    private List<Entry> entries;

    @OneToMany(mappedBy = "jar")
    private List<UnlockDateProposal> unlockDateProposals;

    @PrePersist
    public void prePersist() {
        if (entryCount < 0) {
            entryCount = 0;
        }
    }
}
