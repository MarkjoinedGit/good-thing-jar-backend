package com.goodthingjar.unit.service;

import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.dto.response.PairingCodeResponse;
import com.goodthingjar.dto.response.PairingStatusResponse;
import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.Jar;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.CoupleStatus;
import com.goodthingjar.entity.enums.JarStatus;
import com.goodthingjar.entity.enums.PairingCodeStatus;
import com.goodthingjar.exception.PairingBusinessException;
import com.goodthingjar.repository.CoupleRepository;
import com.goodthingjar.repository.JarRepository;
import com.goodthingjar.repository.PairingCodeRepository;
import com.goodthingjar.repository.UserRepository;
import com.goodthingjar.service.factory.CoupleFactory;
import com.goodthingjar.service.factory.JarFactory;
import com.goodthingjar.service.factory.PairingCodeFactory;
import com.goodthingjar.service.impl.PairingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PairingServiceTest {

    // --- Repositories: @Mock (no real DB) ---
    @Mock private UserRepository userRepository;
    @Mock private CoupleRepository coupleRepository;
    @Mock private JarRepository jarRepository;
    @Mock private PairingCodeRepository pairingCodeRepository;

    // --- Factories ---
    // Spy: real logic runs, but generateCode() can be stubbed to control randomness
    @Spy private PairingCodeFactory pairingCodeFactory;
    // Real: pure builders with no external dependencies
    private final CoupleFactory coupleFactory = new CoupleFactory();
    private final JarFactory jarFactory       = new JarFactory();

    // --- SUT: manual constructor — full, explicit control over what is injected ---
    private PairingServiceImpl pairingService;

    @BeforeEach
    void setUp() {
        pairingService = new PairingServiceImpl(
                userRepository,
                coupleRepository,
                jarRepository,
                pairingCodeRepository,
                pairingCodeFactory,
                coupleFactory,
                jarFactory
        );
    }

    // -------------------------------------------------------------------------
    // generatePairingCode
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("generatePairingCode")
    class GeneratePairingCodeTests {

        @Test
        void shouldFailWhenUserDoesNotExist() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            when(userRepository.findById(userId)).thenReturn(Optional.empty());

            // WHEN
            IllegalArgumentException ex = assertThrows(
                    IllegalArgumentException.class,
                    () -> pairingService.generatePairingCode(userId)
            );

            // THEN
            assertEquals("User not found", ex.getMessage());
            verifyNoInteractions(coupleRepository, pairingCodeRepository);
        }

        @Test
        void shouldRejectWhenUserAlreadyPaired() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(true);

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.generatePairingCode(userId)
            );

            // THEN
            assertEquals("USER_ALREADY_PAIRED", ex.getErrorCode());
            verify(pairingCodeRepository, never()).save(any());
        }

        @Test
        void shouldRejectWhenActiveCodeExists() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(false);
            when(coupleRepository.existsByUser2(user)).thenReturn(false);
            when(pairingCodeRepository
                    .existsByGeneratedByAndStatusAndExpiresAtAfter(
                            eq(user), eq(PairingCodeStatus.ACTIVE), any()))
                    .thenReturn(true);

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.generatePairingCode(userId)
            );

            // THEN
            assertEquals("ACTIVE_PAIRING_CODE_EXISTS", ex.getErrorCode());
            verify(pairingCodeRepository, never()).save(any());
        }

        @Test
        void shouldGenerateCodeWhenEligible() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(false);
            when(coupleRepository.existsByUser2(user)).thenReturn(false);
            when(pairingCodeRepository
                    .existsByGeneratedByAndStatusAndExpiresAtAfter(
                            eq(user), eq(PairingCodeStatus.ACTIVE), any()))
                    .thenReturn(false);

            // Spy: intercept only generateCode() — real create() still runs
            doReturn("ABCD-1234-EFGH").when(pairingCodeFactory).generateCode();

            // Repository echoes back the entity with a new ID assigned
            when(pairingCodeRepository.save(any())).thenAnswer(inv -> {
                PairingCode pc = inv.getArgument(0);
                pc.setId(UUID.randomUUID());
                return pc;
            });

            // WHEN
            PairingCodeResponse response = pairingService.generatePairingCode(userId);

            // THEN
            assertNotNull(response);
            assertEquals("ABCD-1234-EFGH", response.code());
            assertNotNull(response.pairingCodeId());
            assertTrue(response.code().matches("^[A-Z0-9]{4}-[A-Z0-9]{4}-[A-Z0-9]{4}$"));

            // Deep-check the object handed to the repository
            verify(pairingCodeRepository).save(argThat(pc ->
                    pc.getCode().equals("ABCD-1234-EFGH")
                            && pc.getGeneratedBy().equals(user)
                            && pc.getStatus() == PairingCodeStatus.ACTIVE
                            && pc.getExpiresAt().isAfter(OffsetDateTime.now())
            ));
        }

        @Test
        void shouldRetryWhenDuplicateCodeOccurs() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(false);
            when(coupleRepository.existsByUser2(user)).thenReturn(false);
            when(pairingCodeRepository
                    .existsByGeneratedByAndStatusAndExpiresAtAfter(
                            eq(user), eq(PairingCodeStatus.ACTIVE), any()))
                    .thenReturn(false);

            // First call collides, second is the winner
            doReturn("DUPE-DUPE-DUPE", "ABCD-1234-EFGH")
                    .when(pairingCodeFactory).generateCode();

            // First save throws, second saves normally
            when(pairingCodeRepository.save(any()))
                    .thenThrow(new DataIntegrityViolationException("duplicate"))
                    .thenAnswer(inv -> {
                        PairingCode pc = inv.getArgument(0);
                        pc.setId(UUID.randomUUID());
                        return pc;
                    });

            // WHEN
            PairingCodeResponse response = pairingService.generatePairingCode(userId);

            // THEN
            assertNotNull(response);
            assertEquals("ABCD-1234-EFGH", response.code());
            assertNotNull(response.pairingCodeId());

            verify(pairingCodeRepository, times(2)).save(any());
            verify(pairingCodeFactory, times(2)).generateCode();

            // Both save attempts must carry the correct owner and status
            verify(pairingCodeRepository, times(2)).save(argThat(pc ->
                    pc.getGeneratedBy().equals(user)
                            && pc.getStatus() == PairingCodeStatus.ACTIVE
            ));
        }
    }

    // -------------------------------------------------------------------------
    // claimPairingCode
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("claimPairingCode")
    class ClaimPairingCodeTests {

        @Test
        void shouldFailWhenUserAlreadyPaired() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(true);

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest("ABCD-1234-EFGH"))
            );

            // THEN
            assertEquals("ALREADY_PAIRED", ex.getErrorCode());
            verifyNoInteractions(pairingCodeRepository);
        }

        @Test
        void shouldFailWhenCodeFormatIsInvalid() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.existsByUser1(user)).thenReturn(false);
            when(coupleRepository.existsByUser2(user)).thenReturn(false);

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest("invalid-code"))
            );

            // THEN
            assertEquals("INVALID_CODE_FORMAT", ex.getErrorCode());
            verifyNoInteractions(pairingCodeRepository);
        }

        @Test
        void shouldFailWhenCodeNotFound() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(pairingCodeRepository.findByCode("ABCD-1234-EFGH")).thenReturn(Optional.empty());

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest("ABCD-1234-EFGH"))
            );

            // THEN
            assertEquals("CODE_NOT_FOUND", ex.getErrorCode());
        }

        @Test
        void shouldFailWhenCodeExpired() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            PairingCode code = PairingCode.builder()
                    .code("ABCD-1234-EFGH")
                    .status(PairingCodeStatus.EXPIRED)
                    .expiresAt(OffsetDateTime.now().minusMinutes(1))
                    .generatedBy(user(UUID.randomUUID()))
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(pairingCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest(code.getCode()))
            );

            // THEN
            assertEquals("CODE_EXPIRED_OR_CLAIMED", ex.getErrorCode());
        }

        @Test
        void shouldFailWhenCodeAlreadyClaimed() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            PairingCode code = PairingCode.builder()
                    .code("ABCD-1234-EFGH")
                    .status(PairingCodeStatus.CLAIMED)
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .generatedBy(user(UUID.randomUUID()))
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(pairingCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest(code.getCode()))
            );

            // THEN
            assertEquals("CODE_EXPIRED_OR_CLAIMED", ex.getErrorCode());
        }

        @Test
        void shouldFailWhenSelfClaimingCode() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            PairingCode code = PairingCode.builder()
                    .code("ABCD-1234-EFGH")
                    .status(PairingCodeStatus.ACTIVE)
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .generatedBy(user)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(pairingCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));

            // WHEN
            PairingBusinessException ex = assertThrows(
                    PairingBusinessException.class,
                    () -> pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest(code.getCode()))
            );

            // THEN
            assertEquals("CANNOT_PAIR_WITH_SELF", ex.getErrorCode());
        }

        @Test
        void shouldClaimSuccessfully() {
            // GIVEN
            UUID userId = UUID.randomUUID();
            User claimer = user(userId);
            User owner   = user(UUID.randomUUID());

            PairingCode code = PairingCode.builder()
                    .id(UUID.randomUUID())
                    .code("ABCD-1234-EFGH")
                    .status(PairingCodeStatus.ACTIVE)
                    .expiresAt(OffsetDateTime.now().plusHours(1))
                    .generatedBy(owner)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(claimer));
            when(pairingCodeRepository.findByCode(code.getCode())).thenReturn(Optional.of(code));
            when(pairingCodeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(coupleRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(jarRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // WHEN
            var response = pairingService.claimPairingCode(userId, new ClaimPairingCodeRequest(code.getCode()));

            // THEN
            assertNotNull(response);
            assertTrue(response.isPaired());

            // PairingCode must be stamped with claimer + timestamp before persisting
            verify(pairingCodeRepository).save(argThat(pc ->
                    pc.getClaimedBy().equals(claimer)
                            && pc.getClaimedAt() != null
            ));

            // Couple must link owner → claimer, be ACTIVE, and have a pairedAt timestamp
            verify(coupleRepository).save(argThat((Couple c) ->
                    c.getUser1().equals(owner)
                            && c.getUser2().equals(claimer)
                            && c.getStatus() == CoupleStatus.ACTIVE
                            && c.getPairedAt() != null
            ));

            // Jar must be WRITABLE, tied to a couple, default unlock on Dec 31
            verify(jarRepository).save(argThat((Jar j) ->
                    j.getStatus() == JarStatus.WRITABLE
                            && j.getCouple() != null
                            && j.getUnlocksAt().getMonthValue() == 12
                            && j.getUnlocksAt().getDayOfMonth() == 31
                            && j.getEntryCount() == 0
            ));
        }
    }

    // -------------------------------------------------------------------------
    // getPairingHistory
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getPairingHistory")
    class GetPairingHistoryTests {

        @Test
        void shouldReturnEmptyListWhenNoCouplesExist() {
            UUID userId = UUID.randomUUID();
            User user = user(userId);

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.findByUser1OrUser2OrderByPairedAtDesc(user, user)).thenReturn(List.of());

            List<PairingStatusResponse> history = pairingService.getPairingHistory(userId);

            assertNotNull(history);
            assertTrue(history.isEmpty());
            verifyNoInteractions(jarRepository);
        }

        @Test
        void shouldReturnAllPairingsOrderedByMostRecentFirst() {
            UUID userId = UUID.randomUUID();
            User user = user(userId);
            User currentPartner = user(UUID.randomUUID());
            currentPartner.setFirstName("Current");
            currentPartner.setLastName("Partner");
            User pastPartner = user(UUID.randomUUID());
            pastPartner.setFirstName("Past");
            pastPartner.setLastName("Partner");

            Couple currentCouple = Couple.builder()
                    .id(UUID.randomUUID())
                    .user1(user)
                    .user2(currentPartner)
                    .status(CoupleStatus.ACTIVE)
                    .pairedAt(OffsetDateTime.parse("2026-04-16T10:30:00Z"))
                    .build();
            Couple pastCouple = Couple.builder()
                    .id(UUID.randomUUID())
                    .user1(pastPartner)
                    .user2(user)
                    .status(CoupleStatus.DELETED)
                    .pairedAt(OffsetDateTime.parse("2025-04-16T10:30:00Z"))
                    .build();

            Jar currentJar = Jar.builder()
                    .id(UUID.randomUUID())
                    .couple(currentCouple)
                    .status(JarStatus.WRITABLE)
                    .unlocksAt(OffsetDateTime.parse("2026-12-31T23:59:59Z"))
                    .entryCount(7)
                    .build();
            Jar pastJar = Jar.builder()
                    .id(UUID.randomUUID())
                    .couple(pastCouple)
                    .status(JarStatus.ARCHIVED)
                    .unlocksAt(OffsetDateTime.parse("2025-12-31T23:59:59Z"))
                    .entryCount(23)
                    .build();

            when(userRepository.findById(userId)).thenReturn(Optional.of(user));
            when(coupleRepository.findByUser1OrUser2OrderByPairedAtDesc(user, user))
                    .thenReturn(List.of(currentCouple, pastCouple));
            when(jarRepository.findByCouple(currentCouple)).thenReturn(List.of(currentJar));
            when(jarRepository.findByCouple(pastCouple)).thenReturn(List.of(pastJar));

            List<PairingStatusResponse> history = pairingService.getPairingHistory(userId);

            assertEquals(2, history.size());
            assertEquals("Current Partner", history.get(0).partnerName());
            assertEquals(CoupleStatus.ACTIVE, history.get(0).status());
            assertEquals("Past Partner", history.get(1).partnerName());
            assertEquals(CoupleStatus.DELETED, history.get(1).status());
            assertNotNull(history.get(0).currentJar());
            assertEquals(JarStatus.WRITABLE, history.get(0).currentJar().status());
            assertTrue(history.get(0).currentJar().canWrite());
            assertEquals(JarStatus.ARCHIVED, history.get(1).currentJar().status());
            assertFalse(history.get(1).currentJar().canWrite());
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static User user(UUID id) {
        return User.builder()
                .id(id)
                .email("user@example.com")
                .passwordHash("hashed")
                .build();
    }
}