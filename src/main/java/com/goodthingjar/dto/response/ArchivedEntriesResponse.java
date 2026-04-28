package com.goodthingjar.dto.response;

import java.util.List;

public record ArchivedEntriesResponse(
    int total,
    List<UnlockedEntryResponse> entries
) {
}

