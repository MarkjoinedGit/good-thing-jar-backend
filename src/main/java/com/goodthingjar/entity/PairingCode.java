package com.goodthingjar.entity;

import com.goodthingjar.entity.enums.PairingCodeStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "pairing_codes",
        indexes = {
                @Index(name = "idx_pairing_codes_generated_by_user_expires", columnList = "generated_by_user_id, expires_at"),
                @Index(name = "idx_pairing_codes_status_expires_at", columnList = "status, expires_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PairingCode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @ManyToOne
    @JoinColumn(name = "generated_by_user_id", nullable = false)
    private User generatedBy;

    @ManyToOne
    @JoinColumn(name = "claimed_by_user_id")
    private User claimedBy;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "claimed_at")
    private OffsetDateTime claimedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PairingCodeStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

//    public static PairingCode create(String code, User user) {
//        OffsetDateTime now = OffsetDateTime.now();
//
//        PairingCode pc = new PairingCode();
//        pc.code = code;
//        pc.generatedBy = user;
//        pc.status = PairingCodeStatus.ACTIVE;
//        pc.createdAt = now;
//        pc.expiresAt = now.plusDays(1);
//
//        return pc;
//    }
//
//    public void claim(User user) {
//        if (generatedBy.getId().equals(user.getId())) {
//            throw new IllegalStateException("Cannot claim your own code");
//        }
//
//        if (status != PairingCodeStatus.ACTIVE) {
//            throw new IllegalStateException("Invalid state");
//        }
//
//        if (isExpired()) {
//            status = PairingCodeStatus.EXPIRED;
//            throw new IllegalStateException("Pairing code has expired");
//        }
//
//        this.claimedBy = user;
//        this.claimedAt = OffsetDateTime.now();
//        this.status = PairingCodeStatus.CLAIMED;
//    }
//
//    private boolean isExpired() {
//        return OffsetDateTime.now().isAfter(expiresAt);
//    }
}
