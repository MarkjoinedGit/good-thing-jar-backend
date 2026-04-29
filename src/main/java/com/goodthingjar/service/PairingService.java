package com.goodthingjar.service;

import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.dto.request.UnpairRequest;
import com.goodthingjar.dto.response.PairingCodeResponse;
import com.goodthingjar.dto.response.PairingStatusResponse;
import com.goodthingjar.dto.response.UnpairResponse;

import java.util.List;
import java.util.UUID;

public interface PairingService {
    PairingCodeResponse generatePairingCode(UUID userId);
    PairingStatusResponse claimPairingCode(UUID userId, ClaimPairingCodeRequest request);
    PairingStatusResponse getPairingStatus(UUID userId);
    @SuppressWarnings("unused")
    UnpairResponse unpair(UUID userId, UnpairRequest request);
    List<PairingStatusResponse> getPairingHistory(UUID userId);
}
