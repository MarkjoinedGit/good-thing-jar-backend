package com.goodthingjar.platform.throttle;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AbuseThrottleBucketRepository
    extends JpaRepository<AbuseThrottleBucketEntity, AbuseThrottleBucketEntity.Key> {

  @Modifying
  @Query("delete from AbuseThrottleBucketEntity b where b.expiresAt <= :now")
  int deleteExpired(@Param("now") Instant now);
}
