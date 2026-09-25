package com.goodthingjar.pairing.api;

import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pairs")
public class PairController {
  private final PairAccessService pairs;

  public PairController(PairAccessService p) {
    pairs = p;
  }

  @GetMapping("/current")
  PairResponse current(@AuthenticationPrincipal AuthenticatedAccount actor) {
    var pair = pairs.requireCurrent(actor.accountId());
    return new PairResponse(pair.id(), pair.timeZone(), pair.memberAccountIds(), pair.createdAt());
  }
}
