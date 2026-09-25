package com.goodthingjar.jar.api;

import com.goodthingjar.jar.application.UnlockProposalService;
import com.goodthingjar.jar.domain.JarLockPolicy;
import com.goodthingjar.jar.persistence.JarEntity;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Clock;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jars/{jarId}/unlock-proposals")
public class UnlockProposalController {
  private final UnlockProposalService service;
  private final JarLockPolicy locks;
  private final Clock clock;

  public UnlockProposalController(UnlockProposalService s, JarLockPolicy l, Clock c) {
    service = s;
    locks = l;
    clock = c;
  }

  @PostMapping
  ResponseEntity<UnlockProposalDtos.UnlockProposalResponse> create(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @Valid @RequestBody UnlockProposalDtos.CreateUnlockProposalRequest request) {
    var p = service.propose(actor.accountId(), jarId, request.proposedUnlockAt());
    return ResponseEntity.created(URI.create("/jars/" + jarId + "/unlock-proposals/" + p.getId()))
        .body(UnlockProposalDtos.UnlockProposalResponse.from(p));
  }

  @PostMapping("/{id}/approval")
  JarDtos.JarDetail approve(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @PathVariable UUID id) {
    return detail(service.approve(actor.accountId(), jarId, id));
  }

  @PostMapping("/{id}/rejection")
  ResponseEntity<Void> reject(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @PathVariable UUID id) {
    service.reject(actor.accountId(), jarId, id);
    return ResponseEntity.noContent().build();
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> cancel(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @PathVariable UUID id) {
    service.cancel(actor.accountId(), jarId, id);
    return ResponseEntity.noContent().build();
  }

  private JarDtos.JarDetail detail(JarEntity j) {
    return new JarDtos.JarDetail(
        j.getId(),
        j.getSequenceNumber(),
        j.isCurrent(),
        j.getTimeZone(),
        j.getEffectiveUnlockAt(),
        locks.isLocked(clock.instant(), j.getEffectiveUnlockAt()) ? "LOCKED" : "UNLOCKED",
        j.getCreatedAt(),
        null);
  }
}
