package com.goodthingjar.dto.response;

import java.util.List;

public record EntrySyncResponse(
    int synced,
    int failed,
    List<EntrySyncItemResponse> entries
) {
}

