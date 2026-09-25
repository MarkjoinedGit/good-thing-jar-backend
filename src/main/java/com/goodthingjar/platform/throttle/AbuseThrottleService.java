package com.goodthingjar.platform.throttle;

import com.goodthingjar.platform.error.ThrottledException;
import com.goodthingjar.platform.security.SecretHasher;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AbuseThrottleService {
  private final JdbcTemplate jdbc;
  private final ThrottleProperties properties;
  private final SecretHasher hasher;
  private final Clock clock;
  private final AbuseThrottleBucketRepository buckets;

  public AbuseThrottleService(
      JdbcTemplate jdbc,
      ThrottleProperties properties,
      SecretHasher hasher,
      Clock clock,
      AbuseThrottleBucketRepository buckets) {
    this.jdbc = jdbc;
    this.properties = properties;
    this.hasher = hasher;
    this.clock = clock;
    this.buckets = buckets;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void check(String operation, String rawScope) {
    ThrottleProperties.Limit limit = properties.forOperation(operation);
    Instant now = clock.instant();
    Duration window = limit.getWindow();
    long seconds = Math.max(1, window.toSeconds());
    Instant start = Instant.ofEpochSecond((now.getEpochSecond() / seconds) * seconds);
    Instant expires = start.plusSeconds(seconds);
    Integer count =
        jdbc.queryForObject(
            """
            INSERT INTO abuse_throttle_bucket(operation,scope_hash,window_started_at,request_count,expires_at)
            VALUES (?,?,?,?,?) ON CONFLICT(operation,scope_hash,window_started_at)
            DO UPDATE SET request_count=abuse_throttle_bucket.request_count+1
            RETURNING request_count
            """,
            Integer.class,
            operation,
            hasher.pseudonymousScope(rawScope),
            Timestamp.from(start),
            1,
            Timestamp.from(expires));
    if (count != null && count > limit.getCapacity())
      throw new ThrottledException(Math.max(1, expires.getEpochSecond() - now.getEpochSecond()));
  }

  @Scheduled(fixedDelayString = "${gtj.throttle.cleanup-delay:PT10M}")
  @Transactional
  public void cleanupExpiredBuckets() {
    buckets.deleteExpired(clock.instant());
  }
}
