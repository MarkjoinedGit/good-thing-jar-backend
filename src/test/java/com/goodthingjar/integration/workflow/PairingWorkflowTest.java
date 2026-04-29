package com.goodthingjar.integration.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.dto.response.JarSummaryResponse;
import com.goodthingjar.dto.response.PairingStatusResponse;
import com.goodthingjar.entity.enums.CoupleStatus;
import com.goodthingjar.entity.enums.JarStatus;
import com.goodthingjar.exception.GlobalExceptionHandler;
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
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PairingWorkflowTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private PairingService pairingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() throws Exception {
        pairingService = mock(PairingService.class);
        Object pairingController = createController("com.goodthingjar.controller.PairingController", pairingService);
        mockMvc = MockMvcBuilders
            .standaloneSetup(pairingController)
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void claimCreatesInitialWritableJarWithDefaultUnlockDateAndZeroEntries() throws Exception {
        UUID userId = UUID.fromString("10101010-1010-1010-1010-101010101010");
        authenticate(userId);

        JarSummaryResponse currentJar = new JarSummaryResponse(
            UUID.fromString("20202020-2020-2020-2020-202020202020"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            0,
            true
        );

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class))).thenReturn(
            new PairingStatusResponse(
                true,
                UUID.fromString("30303030-3030-3030-3030-303030303030"),
                UUID.fromString("40404040-4040-4040-4040-404040404040"),
                "Partner",
                "partner@example.com",
                null,
                OffsetDateTime.parse("2026-04-23T00:00:00Z"),
                CoupleStatus.ACTIVE,
                currentJar,
                "Paired successfully"
            )
        );

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("ABCD-1234-EFGH"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.currentJar.status").value("WRITABLE"))
            .andExpect(jsonPath("$.data.currentJar.unlocksAt").value("2026-12-31T23:59:59Z"))
            .andExpect(jsonPath("$.data.currentJar.entryCount").value(0));
    }

    @Test
    void beforeUnlockDateEntryContentIsForbidden() throws Exception {
        UUID userId = UUID.fromString("50505050-5050-5050-5050-505050505050");
        authenticate(userId);

        mockMvc.perform(get("/api/v1/entries")
                .param("jarId", "20202020-2020-2020-2020-202020202020"))
            .andExpect(status().isForbidden());
    }

    @Test
    void claimCreatesInitialWritableJarWithDefaultUnlockDateAndZeroEntriesT170() throws Exception {
        // T170: Write failing workflow test: claim creates initial WRITABLE jar with default Dec 31 unlock date and entryCount=0
        UUID userId = UUID.fromString("70707070-7070-7070-7070-707070707070");
        authenticate(userId);

        JarSummaryResponse currentJar = new JarSummaryResponse(
            UUID.fromString("a0a0a0a0-a0a0-a0a0-a0a0-a0a0a0a0a0a0"),
            JarStatus.WRITABLE,
            OffsetDateTime.parse("2026-12-31T23:59:59Z"),
            0,
            true
        );

        when(pairingService.claimPairingCode(eq(userId), any(ClaimPairingCodeRequest.class))).thenReturn(
            new PairingStatusResponse(
                true,
                UUID.fromString("b0b0b0b0-b0b0-b0b0-b0b0-b0b0b0b0b0b0"),
                UUID.fromString("c0c0c0c0-c0c0-c0c0-c0c0-c0c0c0c0c0c0"),
                "Partner",
                "partner@example.com",
                null,
                OffsetDateTime.parse("2026-04-29T00:00:00Z"),
                CoupleStatus.ACTIVE,
                currentJar,
                "Paired successfully"
            )
        );

        mockMvc.perform(post("/api/v1/pairing/claim-code")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new ClaimPairingCodeRequest("ABCD-1234-EFGH"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.currentJar.status").value("WRITABLE"))
            .andExpect(jsonPath("$.data.currentJar.unlocksAt").value("2026-12-31T23:59:59Z"))
            .andExpect(jsonPath("$.data.currentJar.entryCount").value(0));
    }

    @Test
    void afterPairingAndBeforeUnlockDateEntryContentRemainsForbiddenT172() throws Exception {
        // T172: Write failing workflow test: after pairing and before unlock date, entry content remains locked/forbidden
        UUID userId = UUID.fromString("d0d0d0d0-d0d0-d0d0-d0d0-d0d0d0d0d0d0");
        authenticate(userId);

        // Attempt to retrieve entries from a writable (locked) jar
        // The endpoint should return 403 Forbidden with locked message
        mockMvc.perform(get("/api/v1/entries")
                .param("jarId", "e0e0e0e0-e0e0-e0e0-e0e0-e0e0e0e0e0e0"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("ENTRIES_LOCKED"))
            .andExpect(jsonPath("$.error.unlocksAt").exists());
    }

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
                if (constructor.getParameterCount() == 1
                    && constructor.getParameterTypes()[0].isAssignableFrom(dependency.getClass().getInterfaces()[0])) {
                    return constructor.newInstance(dependency);
                }
            }
            throw new AssertionError("Controller constructor does not match expected dependency injection signature for " + className);
        } catch (ClassNotFoundException ex) {
            throw new AssertionError("Missing controller for workflow test: " + className, ex);
        }
    }
}

