package com.goodthingjar.entity;

import com.goodthingjar.entity.enums.CoupleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "couples",
        indexes = {
                @Index(name = "idx_couples_user1_id_status", columnList = "user1_id, status"),
                @Index(name = "idx_couples_user2_id_status", columnList = "user2_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Couple {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "user1_id", nullable = false)
    private User user1;

    @ManyToOne
    @JoinColumn(name = "user2_id", nullable = false)
    private User user2;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CoupleStatus status;

    @Column(name = "paired_at", nullable = false)
    private OffsetDateTime pairedAt;

    @Column(name = "suspended_at")
    private OffsetDateTime suspendedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "couple")
    private Set<Jar> jars;
}
