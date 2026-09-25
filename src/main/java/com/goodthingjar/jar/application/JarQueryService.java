package com.goodthingjar.jar.application;

import com.goodthingjar.jar.persistence.*;
import com.goodthingjar.pairing.application.PairAccessService;
import com.goodthingjar.platform.error.ProtectedResourceErrors;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JarQueryService {
  private final JarRepository jars;
  private final PairAccessService pairs;

  public JarQueryService(JarRepository j, PairAccessService p) {
    jars = j;
    pairs = p;
  }

  @Transactional(readOnly = true)
  public List<JarEntity> history(UUID account) {
    return jars.findByPairIdOrderBySequenceNumberDesc(pairs.requireCurrent(account).id());
  }

  @Transactional(readOnly = true)
  public JarEntity detail(UUID account, UUID jar) {
    var pair = pairs.requireCurrent(account);
    return jars.findByIdAndPairId(jar, pair.id()).orElseThrow(ProtectedResourceErrors::notFound);
  }
}
