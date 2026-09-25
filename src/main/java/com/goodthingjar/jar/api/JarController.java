package com.goodthingjar.jar.api;

import com.goodthingjar.jar.application.*;
import com.goodthingjar.jar.domain.JarLockPolicy;
import com.goodthingjar.jar.persistence.JarEntity;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/jars")
public class JarController {
  private final JarQueryService queries;
  private final JarLifecycleService lifecycle;
  private final UnlockProposalService proposals;
  private final JarLockPolicy locks;
  private final Clock clock;

  public JarController(
      JarQueryService q,
      JarLifecycleService l,
      UnlockProposalService p,
      JarLockPolicy policy,
      Clock c) {
    queries = q;
    lifecycle = l;
    proposals = p;
    locks = policy;
    clock = c;
  }

  @GetMapping
  List<JarDtos.JarSummary> list(@AuthenticationPrincipal AuthenticatedAccount actor) {
    return queries.history(actor.accountId()).stream().map(this::summary).toList();
  }

  @PostMapping
  ResponseEntity<JarDtos.JarSummary> next(@AuthenticationPrincipal AuthenticatedAccount actor) {
    var jar = lifecycle.createNext(actor.accountId());
    return ResponseEntity.created(URI.create("/jars/" + jar.getId())).body(summary(jar));
  }

  @GetMapping("/{id}")
  JarDtos.JarDetail detail(
      @AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
    var j = queries.detail(actor.accountId(), id);
    var s = summary(j);
    Object pending =
        proposals
            .pending(actor.accountId(), j)
            .map(UnlockProposalDtos.UnlockProposalResponse::from)
            .orElse(null);
    return new JarDtos.JarDetail(
        s.id(),
        s.sequenceNumber(),
        s.current(),
        s.timeZone(),
        s.effectiveUnlockAt(),
        s.lockStatus(),
        s.createdAt(),
        pending);
  }

  private JarDtos.JarSummary summary(JarEntity j) {
    return new JarDtos.JarSummary(
        j.getId(),
        j.getSequenceNumber(),
        j.isCurrent(),
        j.getTimeZone(),
        j.getEffectiveUnlockAt(),
        locks.isLocked(clock.instant(), j.getEffectiveUnlockAt()) ? "LOCKED" : "UNLOCKED",
        j.getCreatedAt());
  }
}
