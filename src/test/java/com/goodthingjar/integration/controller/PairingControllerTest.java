package com.goodthingjar.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.dto.request.UnpairRequest;
import com.goodthingjar.dto.response.JarSummaryResponse;
import com.goodthingjar.dto.response.PairingCodeResponse;
import com.goodthingjar.dto.response.PairingStatusResponse;
import com.goodthingjar.entity.enums.CoupleStatus;
import com.goodthingjar.entity.enums.JarStatus;
import com.goodthingjar.exception.PairingBusinessException;
import static org.mockito.Mockito.doNothing;
import com.goodthingjar.security.UserPrincipal;
import com.goodthingjar.service.PairingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Constructor;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for PairingController endpoints.
 * Tests the API contract for pairing-related operations (T042-T044, T163-T172).
 */
class PairingControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PairingService pairingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        pairingService = mock(PairingService.class);
        Object pairingController = createController("com.goodthingjar.controller.PairingController", pairingService);
        mockMvc = MockMvcBuilders
            .standaloneSetup(pairingController)
            .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    // ====== T042: Generate Pairing Code ======
    @Test
    void generateCodeShouldReturnCreatedWithValidCode() throws Exception {
        UUID userId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        authenticate(userId);

        PairingCodeResponse response = new PairingCodeResponse(
            UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"),
            "ABCD-1234-EFGH",
            OffsetDateTime.parse("2026-04-17T10:30:00Z"),
            "Share this code with your partner. They have 24 hours to claim it."
        );

        when(pairingService.generatePairingCode(userId)).thenReturn(response);

        mockMvc.perform(post("/api/v1/pairing/generate-code")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.code").value("ABCD-1234-EFGH"))
            .andExpect(jsonPath("$.data.expiresAt").value("2026-04-17T10:30:00Z"));
    }

    // ====== T043: Claim Pairing Code ======
    @Test
    void claimCodeShouldReturnCreatedWithCoupleInfo() throws Exception {
        UUID userId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        authenticate(userId);

        JarSummaryResponse jarSummary = new JarSummaryResponse(
            UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            0,
            true
        );

        PairingStatusResponse response = new PairingStatusResponse(
            true,
            UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee"),
            UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"),
            "Partner Name",
            "partner@example.com",
            "https://cdn.example.com/profile.jpg",
            OffsetDateTime.parse("2026-04-23T10:30:00Z"),
            CoupleStatus.ACTIVE,
            jarSummary,
            "Paired successfully"
        );

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class)))
            .thenReturn(response);

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("ABCD-1234-EFGH"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isPaired").value(true))
            .andExpect(jsonPath("$.data.partnerName").value("Partner Name"))
            .andExpect(jsonPath("$.data.currentJar.status").value("WRITABLE"));
    }

    // ====== T044: Get Pairing Status ======
    @Test
    void getStatusShouldReturnPairingInfoWhenPaired() throws Exception {
        UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        authenticate(userId);

        JarSummaryResponse jarSummary = new JarSummaryResponse(
            UUID.fromString("22222222-2222-2222-2222-222222222222"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            5,
            true
        );

        PairingStatusResponse response = new PairingStatusResponse(
            true,
            UUID.fromString("33333333-3333-3333-3333-333333333333"),
            UUID.fromString("44444444-4444-4444-4444-444444444444"),
            "John Doe",
            "john@example.com",
            null,
            OffsetDateTime.parse("2026-04-16T10:30:00Z"),
            CoupleStatus.ACTIVE,
            jarSummary,
            "Successfully paired"
        );

        when(pairingService.getPairingStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pairing/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isPaired").value(true))
            .andExpect(jsonPath("$.data.partnerName").value("John Doe"))
            .andExpect(jsonPath("$.data.pairedAt").value("2026-04-16T10:30:00Z"))
            .andExpect(jsonPath("$.data.currentJar.entryCount").value(5));
    }

    @Test
    void getStatusShouldReturnNotPairedWhenNoCoupleExists() throws Exception {
        UUID userId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        authenticate(userId);

        PairingStatusResponse response = new PairingStatusResponse(false, null, null, null, null, null, null, null, null, "You are not currently paired");

        when(pairingService.getPairingStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pairing/status")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.isPaired").value(false));
    }

    // Additional business-rule tests
    @Test
    void getStatusShouldReturnSuspendedWhenPairingSuspended() throws Exception {
        UUID userId = UUID.fromString("12121212-1212-1212-1212-121212121212");
        authenticate(userId);

        JarSummaryResponse jarSummary = new JarSummaryResponse(
            UUID.fromString("21212121-2121-2121-2121-212121212121"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            2,
            false // cannot write when suspended
        );

        PairingStatusResponse response = new PairingStatusResponse(
            true,
            UUID.fromString("31313131-3131-3131-3131-313131313131"),
            UUID.fromString("41414141-4141-4141-4141-414141414141"),
            "Suspended Partner",
            "suspended@example.com",
            null,
            OffsetDateTime.parse("2026-01-01T00:00:00Z"),
            CoupleStatus.SUSPENDED,
            jarSummary,
            "Pairing suspended"
        );

        when(pairingService.getPairingStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pairing/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.status").value("SUSPENDED"))
            .andExpect(jsonPath("$.data.currentJar.canWrite").value(false));
    }

    @Test
    void unpairThenGetStatusShouldShowNotPaired() throws Exception {
        UUID userId = UUID.fromString("51515151-5151-5151-5151-515151515151");
        authenticate(userId);

        // unpair call succeeds (void)
        doNothing().when(pairingService).unpair(eq(userId), any(UnpairRequest.class));

        // after unpair, service reports not paired
        PairingStatusResponse notPaired = new PairingStatusResponse(false, null, null, null, null, null, null, null, null, "You are not currently paired");
        when(pairingService.getPairingStatus(userId)).thenReturn(notPaired);

        mockMvc.perform(post("/api/v1/pairing/unpair")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UnpairRequest("No longer together"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        mockMvc.perform(get("/api/v1/pairing/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.isPaired").value(false));
    }

    @Test
    void getPairingHistoryShouldSupportEmptyHistoryAndPagination() throws Exception {
        UUID userId = UUID.fromString("61616161-6161-6161-6161-616161616161");
        authenticate(userId);

        // empty history
        List<PairingStatusResponse> empty = List.of();
        when(pairingService.getPairingHistory(userId)).thenReturn(empty);

        mockMvc.perform(get("/api/v1/pairing/history")
                .param("limit", "10")
                .param("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.couples").isArray())
            .andExpect(jsonPath("$.data.couples.length()").value(0));
    }

    // ====== T163: Invalid Code Format ======
    @Test
    void claimCodeWithInvalidFormatShouldReturnBadRequest() throws Exception {
        UUID userId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        authenticate(userId);

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("INVALID"))))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("INVALID_CODE_FORMAT"));
    }

    // ====== T164: Code Not Found ======
    @Test
    void claimCodeWithNonExistentCodeShouldReturnNotFound() throws Exception {
        UUID userId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        authenticate(userId);

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class)))
            .thenThrow(PairingBusinessException.codeNotFound());

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("AAAA-BBBB-CCCC"))))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("RESOURCE_NOT_FOUND"));
    }

    // ====== T165: Code Expired or Already Claimed ======
    @Test
    void claimCodeWhenExpiredShouldReturnGone() throws Exception {
        UUID userId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        authenticate(userId);

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class)))
            .thenThrow(PairingBusinessException.codeExpiredOrClaimed());

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("ABCD-1234-EFGH"))))
            .andExpect(status().isGone())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("CODE_EXPIRED_OR_CLAIMED"));
    }

    // ====== T166: Already Paired - Generate Code Conflict ======
    @Test
    void generateCodeWhenAlreadyPairedShouldReturnConflict() throws Exception {
        UUID userId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        authenticate(userId);

        when(pairingService.generatePairingCode(userId))
            .thenThrow(PairingBusinessException.userAlreadyPaired());

        mockMvc.perform(post("/api/v1/pairing/generate-code")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("USER_ALREADY_PAIRED"));
    }

    // ====== T167: Claimer Already Paired ======
    @Test
    void claimCodeWhenClaimerAlreadyPairedShouldReturnConflict() throws Exception {
        UUID userId = UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffffffff0000");
        authenticate(userId);

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class)))
            .thenThrow(PairingBusinessException.alreadyPaired());

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("ABCD-1234-EFGH"))))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("ALREADY_PAIRED"));
    }

    // ====== T168: Unpair ======
    @Test
    void unpairShouldReturnOkAndSuspendPairing() throws Exception {
        UUID userId = UUID.fromString("bbbbcccc-dddd-eeee-ffff-0000aaaabbbb");
        authenticate(userId);

        doNothing().when(pairingService).unpair(eq(userId), any(UnpairRequest.class));

        mockMvc.perform(post("/api/v1/pairing/unpair")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new UnpairRequest("Breaking up"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.message").value("Pairing has been ended"));
    }

    // ====== T169: Get Pairing History ======
    @Test
    void getPairingHistoryShouldReturnAllPastAndCurrentPairings() throws Exception {
        UUID userId = UUID.fromString("ccccdddd-eeee-ffff-0000-aaaa1111bbbb");
        authenticate(userId);

        JarSummaryResponse jar1 = new JarSummaryResponse(
            UUID.fromString("11112222-3333-4444-5555-666677778888"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            5,
            true
        );

        JarSummaryResponse jar2 = new JarSummaryResponse(
            UUID.fromString("99990000-1111-2222-3333-444455556666"),
            JarStatus.ARCHIVED,
            OffsetDateTime.parse("2025-12-31T23:59:59Z"),
            23,
            false
        );

        // The service returns a list of PairingStatusResponse; the controller maps this to the history payload
        PairingStatusResponse entry1 = new PairingStatusResponse(
            true,
            UUID.fromString("aaaabbbb-cccc-dddd-eeee-ffffff000011"),
            UUID.fromString("22223333-4444-5555-6666-777788889999"),
            "Current Partner",
            "current@example.com",
            null,
            OffsetDateTime.parse("2026-04-16T10:30:00Z"),
            CoupleStatus.ACTIVE,
            jar1,
            ""
        );

        PairingStatusResponse entry2 = new PairingStatusResponse(
            true,
            UUID.fromString("bbbbcccc-dddd-eeee-ffff-000011112222"),
            UUID.fromString("33334444-5555-6666-7777-888899990000"),
            "Past Partner",
            "past@example.com",
            null,
            OffsetDateTime.parse("2025-04-16T10:30:00Z"),
            CoupleStatus.SUSPENDED,
            jar2,
            ""
        );

        List<PairingStatusResponse> history = List.of(entry1, entry2);

        when(pairingService.getPairingHistory(userId)).thenReturn(history);

        mockMvc.perform(get("/api/v1/pairing/history")
                .param("limit", "20")
                .param("offset", "0"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.couples").isArray())
            .andExpect(jsonPath("$.data.couples.length()").value(2))
            .andExpect(jsonPath("$.data.couples[0].partnerName").value("Current Partner"))
            .andExpect(jsonPath("$.data.couples[0].status").value("ACTIVE"))
            .andExpect(jsonPath("$.data.couples[1].partnerName").value("Past Partner"))
            .andExpect(jsonPath("$.data.couples[1].status").value("SUSPENDED"));
    }

    // ====== T171: Get Status with All Fields ======
    @Test
    void getStatusShouldIncludePartnerNameProfilePictureAndCurrentJar() throws Exception {
        UUID userId = UUID.fromString("ddddeeee-ffff-0000-1111-2222333344aa");
        authenticate(userId);

        JarSummaryResponse jarSummary = new JarSummaryResponse(
            UUID.fromString("44445555-6666-7777-8888-999900001111"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            3,
            true
        );

        PairingStatusResponse response = new PairingStatusResponse(
            true,
            UUID.fromString("55556666-7777-8888-9999-000011112222"),
            UUID.fromString("66667777-8888-9999-0000-111122223333"),
            "Jane Doe",
            "jane@example.com",
            "https://cdn.example.com/profile/jane.jpg",
            OffsetDateTime.parse("2026-04-16T10:30:00Z"),
            CoupleStatus.ACTIVE,
            jarSummary,
            "Successfully retrieved pairing status"
        );

        when(pairingService.getPairingStatus(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/pairing/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.partnerName").value("Jane Doe"))
            .andExpect(jsonPath("$.data.partnerProfilePicture").value("https://cdn.example.com/profile/jane.jpg"))
            .andExpect(jsonPath("$.data.pairedAt").value("2026-04-16T10:30:00Z"))
            .andExpect(jsonPath("$.data.currentJar").exists())
            .andExpect(jsonPath("$.data.currentJar.jarId").value("44445555-6666-7777-8888-999900001111"))
            .andExpect(jsonPath("$.data.currentJar.status").value("WRITABLE"));
    }

    // Helper methods
    private void authenticate(UUID userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new UserPrincipal(userId, "user@example.com"),
                null,
                AuthorityUtils.NO_AUTHORITIES
            )
        );
    }

    private Object createController(String className, Object dependency) throws Exception {
        try {
            Class<?> controllerClass = Class.forName(className);
            for (Constructor<?> constructor : controllerClass.getConstructors()) {
                if (constructor.getParameterCount() == 1) {
                    Class<?>[] paramTypes = constructor.getParameterTypes();
                    Class<?> interfaceType = dependency.getClass().getInterfaces()[0];
                    if (paramTypes[0].isAssignableFrom(interfaceType)) {
                        return constructor.newInstance(dependency);
                    }
                }
            }
            throw new AssertionError("Controller constructor does not match expected dependency injection signature for " + className);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Missing controller for test: " + className, ex);
        }
    }
}



