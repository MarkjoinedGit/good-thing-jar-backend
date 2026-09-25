package com.goodthingjar.jar.application;

import com.goodthingjar.jar.domain.JarLockPolicy;
import com.goodthingjar.jar.persistence.*;
import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.error.*;
import java.time.Clock;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EntryQueryService {
  public record Page(List<EntryEntity> items, String nextCursor, boolean hasMore) {
    public Page {
      items = List.copyOf(items);
    }

    @Override
    public List<EntryEntity> items() {
      return List.copyOf(items);
    }
  }

  private final JarRepository jars;
  private final EntryRepository entries;
  private final PairAccessService pairs;
  private final JarLockPolicy locks;
  private final EntryCursorCodec cursors;
  private final Clock clock;

  public EntryQueryService(
      JarRepository j,
      EntryRepository e,
      PairAccessService p,
      JarLockPolicy l,
      EntryCursorCodec c,
      Clock clock) {
    jars = j;
    entries = e;
    pairs = p;
    locks = l;
    cursors = c;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public Page page(UUID account, UUID jarId, String cursor, int limit) {
    var pair = pairs.requireCurrent(account);
    JarEntity jar =
        jars.findByIdAndPairId(jarId, pair.id()).orElseThrow(ProtectedResourceErrors::notFound);
    if (locks.isLocked(clock.instant(), jar.getEffectiveUnlockAt()))
      throw new ApiException(ProblemCode.LOCKED, "Jar is locked");
    int size = Math.max(1, Math.min(limit, 100));
    EntryCursorCodec.Cursor decoded = cursor == null ? null : cursors.decode(cursor);
    List<EntryEntity> rows =
        decoded == null
            ? entries.firstPage(jarId, PageRequest.of(0, size + 1))
            : entries.pageAfter(
                jarId, decoded.createdAt(), decoded.id(), PageRequest.of(0, size + 1));
    boolean more = rows.size() > size;
    List<EntryEntity> page = more ? rows.subList(0, size) : rows;
    String next =
        more ? cursors.encode(page.getLast().getCreatedAt(), page.getLast().getId()) : null;
    return new Page(List.copyOf(page), next, more);
  }
}
