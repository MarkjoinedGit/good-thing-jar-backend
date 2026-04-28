package com.goodthingjar.dto.request;

import java.util.List;

public record SyncEntriesRequest(
    List<OfflineEntryRequest> entries
) {
}

