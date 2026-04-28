package com.goodthingjar.entity;

import com.goodthingjar.entity.enums.EntryStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "entries",
    indexes = {
            @Index(name = "idx_entries_jar_created_desc", columnList = "jar_id, created_at"),
            @Index(name = "idx_entries_author_id", columnList = "author_id"),
            @Index(name = "idx_entries_status", columnList = "status"),
            @Index(name = "idx_entries_jar_status", columnList = "jar_id, status"),
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "jar_id", nullable = false)
    private Jar jar;

    @ManyToOne
    @JoinColumn(name = "author_id", nullable = false)
    private User author;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EntryStatus status;

    @Column(name = "synced_at")
    private OffsetDateTime syncedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "client_generated_id")
    private UUID clientGeneratedId;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (this.content == null || this.content.isEmpty()) {
            throw new IllegalArgumentException("Content cannot be null or empty");
        }
    }

}
