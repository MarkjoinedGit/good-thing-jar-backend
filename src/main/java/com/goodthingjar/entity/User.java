package com.goodthingjar.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users",
		indexes = {
				@Index(name = "idx_users_deleted_at", columnList = "deleted_at"),
				@Index(name = "idx_users_created_at", columnList = "created_at")
		})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(nullable = false, unique = true)
	private String email;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Column(name = "profile_picture_url", length = 500)
	private String profilePictureUrl;

	@Column(name = "last_login")
	private OffsetDateTime lastLogin;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	@Column(name = "deleted_at")
	private OffsetDateTime deletedAt;

	@OneToMany(mappedBy = "user1")
	private Set<Couple> couplesAsUser1;

	@OneToMany(mappedBy = "user2")
	private Set<Couple> couplesAsUser2;

	@OneToMany(mappedBy = "author")
	private List<Entry> entries;

	@OneToMany(mappedBy = "generatedBy")
	private List<PairingCode> generatedPairingCodes;

	@OneToMany(mappedBy = "claimedBy")
	private List<PairingCode> claimedPairingCodes;

	@OneToMany(mappedBy = "proposedBy")
	private List<UnlockDateProposal> proposedUnlockDateProposals;

	@OneToMany(mappedBy = "approvedBy")
	private List<UnlockDateProposal> approvedUnlockDateProposals;

	@OneToOne(mappedBy = "user")
	private NotificationPreference notificationPreference;

	public String getFullName() {
		return firstName + " " + lastName;
	}
}
