package com.goodthingjar.pairing.api;

import com.goodthingjar.identity.application.IdentityAccess;
import com.goodthingjar.pairing.application.*;
import com.goodthingjar.pairing.persistence.*;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.*;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/invitations")
public class InvitationController {
  enum Direction {
    incoming,
    outgoing
  }

  private final InvitationService service;
  private final PairingService pairing;
  private final PairAccessService pairs;
  private final IdentityAccess identity;

  public InvitationController(
      InvitationService s, PairingService p, PairAccessService pairs, IdentityAccess i) {
    service = s;
    pairing = p;
    this.pairs = pairs;
    identity = i;
  }

  @PostMapping
  ResponseEntity<InvitationDtos.InvitationSummary> create(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @Valid @RequestBody InvitationDtos.CreateInvitationRequest request) {
    var i = service.create(actor.accountId(), request.email(), request.timeZone());
    return ResponseEntity.accepted().body(summary(i, actor.accountId()));
  }

  @GetMapping
  List<InvitationDtos.InvitationSummary> list(
      @AuthenticationPrincipal AuthenticatedAccount actor, @RequestParam Direction direction) {
    return service.list(actor.accountId()).stream()
        .map(i -> summary(i, actor.accountId()))
        .filter(i -> i.direction().equalsIgnoreCase(direction.name()))
        .toList();
  }

  @PostMapping("/{id}/accept")
  ResponseEntity<PairResponse> accept(
      @AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
    var pair = pairing.accept(actor.accountId(), id);
    var view = pairs.requireCurrent(actor.accountId());
    return ResponseEntity.created(URI.create("/pairs/current"))
        .body(
            new PairResponse(
                pair.getId(), pair.getTimeZone(), view.memberAccountIds(), pair.getCreatedAt()));
  }

  @DeleteMapping("/{id}")
  ResponseEntity<Void> cancel(
      @AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
    service.cancel(actor.accountId(), id);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/delivery-retries")
  ResponseEntity<InvitationDtos.InvitationSummary> retry(
      @AuthenticationPrincipal AuthenticatedAccount actor, @PathVariable UUID id) {
    return ResponseEntity.accepted()
        .body(summary(service.retry(actor.accountId(), id), actor.accountId()));
  }

  private InvitationDtos.InvitationSummary summary(InvitationEntity i, UUID actor) {
    boolean outgoing = i.getInviterAccountId().equals(actor);
    String counterpart =
        outgoing
            ? i.getTargetEmailNormalized()
            : identity.require(i.getInviterAccountId()).normalizedEmail();
    return new InvitationDtos.InvitationSummary(
        i.getId(),
        outgoing ? "OUTGOING" : "INCOMING",
        counterpart,
        i.getStatus(),
        i.getTimeZone(),
        i.getExpiresAt(),
        i.getCreatedAt());
  }
}
