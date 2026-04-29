package com.goodthingjar.service.impl;

import com.goodthingjar.dto.request.ClaimPairingCodeRequest;
import com.goodthingjar.dto.request.UnpairRequest;
import com.goodthingjar.dto.response.JarSummaryResponse;
import com.goodthingjar.dto.response.PairingCodeResponse;
import com.goodthingjar.dto.response.PairingStatusResponse;
import com.goodthingjar.dto.response.UnpairResponse;
import com.goodthingjar.entity.Couple;
import com.goodthingjar.entity.Jar;
import com.goodthingjar.entity.PairingCode;
import com.goodthingjar.entity.User;
import com.goodthingjar.entity.enums.CoupleStatus;
import com.goodthingjar.entity.enums.JarStatus;
import com.goodthingjar.exception.PairingBusinessException;
import com.goodthingjar.service.factory.CoupleFactory;
import com.goodthingjar.service.factory.JarFactory;
import com.goodthingjar.service.factory.PairingCodeFactory;
import com.goodthingjar.repository.CoupleRepository;
import com.goodthingjar.repository.JarRepository;
import com.goodthingjar.repository.PairingCodeRepository;
import com.goodthingjar.repository.UserRepository;
import com.goodthingjar.service.PairingService;
import com.goodthingjar.util.ValidationUtil;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PairingServiceImpl implements PairingService {
    private final UserRepository userRepository;
    private final CoupleRepository coupleRepository;
    private final JarRepository jarRepository;
    private final PairingCodeRepository pairingCodeRepository;
    private final PairingCodeFactory pairingCodeFactory;
    private final CoupleFactory coupleFactory;
    private final JarFactory jarFactory;

    private static final int MAX_RETRY = 5;
    private static final int BASE_DELAY_MS  = 10;
    private static final int MAX_DELAY_MS  = 300;
    private static final String INSTRUCTION = "Share this code with your partner. They have 24 hours to claim it.";

    @Override
    public PairingCodeResponse generatePairingCode(UUID userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ValidationUtil.validateUserCanGenerateCode(user, coupleRepository, pairingCodeRepository);

        // Retry loop runs outside any transaction so no DB connection is held
        // during the backoff sleep. Each attempt opens and closes its own
        // short transaction via saveNewPairingCode().
        for (int attempt = 1; attempt <= MAX_RETRY; attempt++) {
            String code = pairingCodeFactory.generateCode();

            try {
                PairingCode saved = saveNewPairingCode(code, user);

                log.info("Pairing code generated, userId={}, code={}, attempt={}", userId, code, attempt);

                return new PairingCodeResponse(
                        saved.getId(),
                        saved.getCode(),
                        saved.getExpiresAt(),
                        INSTRUCTION);
            } catch (DataIntegrityViolationException e) {
                log.warn("Duplicate pairing code userId={}, code={}, attempt={}", userId, code, attempt);

                if (attempt == MAX_RETRY) {
                    log.error("Failed to generate pairing code after retries, userId={}", userId, e);
                    break;
                }

                long delay = backoffWithJitter(attempt);
                sleep(delay); // safe: no transaction is open here
            }
        }
        throw new RuntimeException("Pairing code generation failed");
    }

    /**
     * Opens a short, self-contained transaction just for the INSERT.
     * Keeping this separate from the retry loop ensures the DB connection
     * is never held open across a backoff sleep.
     */
    @Transactional
    protected PairingCode saveNewPairingCode(String code, User user) {
        PairingCode pc = pairingCodeFactory.create(code, user);
        return pairingCodeRepository.save(pc);
    }

    private void sleep(long millis) {

        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Retry interrupted", e);
        }
    }

    private long backoffWithJitter(int attempt) {

        long exponential = BASE_DELAY_MS * (1L << (attempt-1));
        long jitter = ThreadLocalRandom.current().nextLong(BASE_DELAY_MS);
        return Math.min(exponential + jitter, MAX_DELAY_MS);
    }

    @Override
    @Transactional
    public PairingStatusResponse claimPairingCode(UUID userId, ClaimPairingCodeRequest request) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        ValidationUtil.validateUserCanClaimCode(user, coupleRepository);

        if (!ValidationUtil.isValidFormat(request.code())) {
            throw PairingBusinessException.invalidCodeFormat();
        }

        PairingCode pc = pairingCodeRepository.findByCode(request.code())
                .orElseThrow(PairingBusinessException::codeNotFound);

        ValidationUtil.validateClaimableCode(pc, user);

        pc.setClaimedBy(user);
        pc.setClaimedAt(OffsetDateTime.now());
        pairingCodeRepository.save(pc);

        Couple couple = coupleFactory.createByCode(pc);
        coupleRepository.save(couple);

        Jar jar = jarFactory.createByCouple(couple);
        jarRepository.save(jar);

        couple.getJars().add(jar);

        JarSummaryResponse summaryResponse = new JarSummaryResponse(
                jar.getId(),
                jar.getStatus(),
                jar.getUnlocksAt(),
                jar.getEntryCount(),
                true
        );

        User claimer = pc.getClaimedBy();

        return new PairingStatusResponse(
                true,
                couple.getId(),
                claimer.getId(),
                claimer.getFullName(),
                claimer.getEmail(),
                claimer.getProfilePictureUrl(),
                couple.getPairedAt(),
                couple.getStatus(),
                summaryResponse,
                "Paired successfully"
        );
    }

    @Override
    public PairingStatusResponse getPairingStatus(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Optional<Couple> coupleOpt = coupleRepository.findByUser1OrUser2(user, user);

        if (coupleOpt.isEmpty() || coupleOpt.get().getStatus() != CoupleStatus.ACTIVE) {
            return PairingStatusResponse.notPaired();
        }

        Couple couple = coupleOpt.get();

        User partner = couple.getUser1().getId().equals(user.getId()) ? couple.getUser2() : couple.getUser1();

        Optional<Jar> currentJarOpt = jarRepository.findTopByCoupleAndStatusOrderByCreatedAtDesc(
                couple, JarStatus.WRITABLE
        );

        JarSummaryResponse jarSummary = currentJarOpt.map(jar -> new JarSummaryResponse(
                jar.getId(),
                jar.getStatus(),
                jar.getUnlocksAt(),
                jar.getEntryCount(),
                jar.getStatus() == JarStatus.WRITABLE
        )).orElse(null);
        return toPairingStatusResponse(couple, partner, jarSummary);
    }

    @Override
    @Transactional
    public List<PairingStatusResponse> getPairingHistory(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        List<Couple> couples = coupleRepository.findByUser1OrUser2OrderByPairedAtDesc(user, user);

        return couples.stream()
                .map(couple -> {
                    User partner = couple.getUser1().getId().equals(user.getId()) ? couple.getUser2() : couple.getUser1();
                    JarSummaryResponse jarSummary = resolveHistoryJarSummary(couple);
                    return toPairingStatusResponse(couple, partner, jarSummary);
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public UnpairResponse unpair(UUID userId, UnpairRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        Couple couple = coupleRepository.findByUser1OrUser2(user, user)
                .orElseThrow(() -> new IllegalArgumentException("User is not paired"));

        UUID coupleId = couple.getId();

        log.info("Suspending pairing user={}, couple={}", userId, coupleId);

        couple.setStatus(CoupleStatus.DELETED);
        couple.setSuspendedAt(OffsetDateTime.now());
        coupleRepository.save(couple);

        Set<Jar> jars = couple.getJars();
        jars.forEach(jar -> jar.setStatus(JarStatus.ARCHIVED));
        couple.setJars(jars);

        return UnpairResponse.of(coupleId);
    }

    private JarSummaryResponse resolveHistoryJarSummary(Couple couple) {
        return jarRepository.findByCouple(couple).stream()
                .max(Comparator.comparing(Jar::getCreatedAt))
                .map(jar -> new JarSummaryResponse(
                        jar.getId(),
                        jar.getStatus(),
                        jar.getUnlocksAt(),
                        jar.getEntryCount(),
                        jar.getStatus() == JarStatus.WRITABLE && couple.getStatus() == CoupleStatus.ACTIVE
                ))
                .orElse(null);
    }

    private PairingStatusResponse toPairingStatusResponse(Couple couple, User partner, JarSummaryResponse jarSummary) {
        return new PairingStatusResponse(
                true,
                couple.getId(),
                partner.getId(),
                partner.getFullName(),
                partner.getEmail(),
                partner.getProfilePictureUrl(),
                couple.getPairedAt(),
                couple.getStatus(),
                jarSummary,
                null
        );
    }
}
