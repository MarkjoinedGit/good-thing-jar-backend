package com.goodthingjar.pairing.application;

import com.goodthingjar.pairing.persistence.*;
import com.goodthingjar.platform.error.ProtectedResourceErrors;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class PairAccessService {
  public record PairView(
      UUID id, String timeZone, List<UUID> memberAccountIds, java.time.Instant createdAt) {
    public PairView {
      memberAccountIds = List.copyOf(memberAccountIds);
    }

    @Override
    public List<UUID> memberAccountIds() {
      return List.copyOf(memberAccountIds);
    }
  }

  private final PairRepository pairs;
  private final PairMemberRepository members;

  public PairAccessService(PairRepository p, PairMemberRepository m) {
    pairs = p;
    members = m;
  }

  public Optional<PairView> current(UUID account) {
    return members
        .findByAccountId(account)
        .map(m -> view(pairs.findById(m.getPairId()).orElseThrow()));
  }

  public PairView requireCurrent(UUID account) {
    return current(account).orElseThrow(ProtectedResourceErrors::notFound);
  }

  public PairView requireMember(UUID pair, UUID account) {
    PairMemberEntity m =
        members
            .findByAccountId(account)
            .filter(x -> x.getPairId().equals(pair))
            .orElseThrow(ProtectedResourceErrors::notFound);
    return view(pairs.findById(m.getPairId()).orElseThrow(ProtectedResourceErrors::notFound));
  }

  public PairEntity lock(UUID pair) {
    return pairs.findByIdForUpdate(pair).orElseThrow(ProtectedResourceErrors::notFound);
  }

  public boolean paired(UUID account) {
    return members.existsByAccountId(account);
  }

  private PairView view(PairEntity p) {
    return new PairView(
        p.getId(),
        p.getTimeZone(),
        members.findByPairIdOrderByAccountId(p.getId()).stream()
            .map(PairMemberEntity::getAccountId)
            .toList(),
        p.getCreatedAt());
  }
}
