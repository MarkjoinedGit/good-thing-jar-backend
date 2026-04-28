package com.goodthingjar.dto.mapper;

import com.goodthingjar.dto.response.AuthResponse;
import com.goodthingjar.dto.response.CreateEntryResponse;
import com.goodthingjar.dto.response.EntrySyncItemResponse;
import com.goodthingjar.dto.response.JarSummaryResponse;
import com.goodthingjar.dto.response.NotificationPreferenceResponse;
import com.goodthingjar.dto.response.PairingCodeResponse;
import com.goodthingjar.dto.response.UnlockDateProposalResponse;
import com.goodthingjar.dto.response.UserProfileResponse;
import com.goodthingjar.entity.Entry;
import com.goodthingjar.entity.Jar;
import com.goodthingjar.entity.NotificationPreference;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.UnlockDateProposal;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.EntryStatus;

public final class DtoMapper {

    private DtoMapper() {
    }

    public static AuthResponse toAuthResponse(User user, String token, String refreshToken, long expiresIn) {
        return new AuthResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            token,
            refreshToken,
            expiresIn
        );
    }

    public static UserProfileResponse toUserProfileResponse(User user) {
        return new UserProfileResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getProfilePictureUrl(),
            user.getCreatedAt(),
            user.getLastLogin()
        );
    }

    public static PairingCodeResponse toPairingCodeResponse(PairingCode pairingCode) {
        return new PairingCodeResponse(
            pairingCode.getId(),
            pairingCode.getCode(),
            pairingCode.getExpiresAt(),
            "Share this code with your partner. They have 24 hours to claim it."
        );
    }

    public static JarSummaryResponse toJarSummaryResponse(Jar jar, boolean canWrite) {
        return new JarSummaryResponse(
            jar.getId(),
            jar.getStatus(),
            jar.getUnlocksAt(),
            jar.getEntryCount(),
            canWrite
        );
    }

    public static CreateEntryResponse toCreateEntryResponse(Entry entry, String message) {
        return new CreateEntryResponse(
            entry.getId(),
            entry.getJar().getId(),
            entry.getStatus(),
            entry.getCreatedAt(),
            message
        );
    }

    public static EntrySyncItemResponse toEntrySyncItemResponse(Entry entry) {
        return new EntrySyncItemResponse(
            entry.getClientGeneratedId(),
            entry.getId(),
            entry.getStatus(),
            null
        );
    }

    public static EntrySyncItemResponse toEntrySyncItemError(java.util.UUID clientGeneratedId, String errorMessage) {
        return new EntrySyncItemResponse(
            clientGeneratedId,
            null,
            EntryStatus.PENDING_SYNC,
            errorMessage
        );
    }

    public static UnlockDateProposalResponse toUnlockDateProposalResponse(UnlockDateProposal proposal) {
        return new UnlockDateProposalResponse(
            proposal.getId(),
            proposal.getJar().getId(),
            proposal.getProposedBy().getId(),
            proposal.getApprovedBy() == null ? null : proposal.getApprovedBy().getId(),
            proposal.getNewUnlocksAt(),
            proposal.getStatus(),
            proposal.getExpiresAt(),
            proposal.getRejectionReason(),
            proposal.getCreatedAt(),
            proposal.getRespondedAt()
        );
    }

    public static NotificationPreferenceResponse toNotificationPreferenceResponse(NotificationPreference preference) {
        return new NotificationPreferenceResponse(
            preference.getId(),
            preference.getUser().getId(),
            preference.isEnabled(),
            preference.getFrequency(),
            preference.getLocalTimeZone(),
            preference.getNotificationTime(),
            preference.getCreatedAt(),
            preference.getUpdatedAt()
        );
    }
}

