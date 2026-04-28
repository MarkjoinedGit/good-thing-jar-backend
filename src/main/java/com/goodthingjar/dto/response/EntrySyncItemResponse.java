package com.goodthingjar.dto.response;

import com.goodthingjar.entity.enums.EntryStatus;

import java.util.UUID;

public record EntrySyncItemResponse(
    UUID clientGeneratedId,
    UUID serverId,
    EntryStatus status,
    String error
) {
}

