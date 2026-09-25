package com.goodthingjar.pairing.persistence;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface InvitationRepository extends JpaRepository<InvitationEntity, UUID> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from InvitationEntity i where i.id=:id")
  Optional<InvitationEntity> findByIdForUpdate(@Param("id") UUID id);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from InvitationEntity i where i.inviterAccountId=:inviter and i.targetEmailNormalized=:target and i.status in ('PENDING_DELIVERY','PENDING','DELIVERY_FAILED')")
  List<InvitationEntity> findActiveMatchingForUpdate(
      @Param("inviter") UUID inviter, @Param("target") String target);

  @Query(
      "select i from InvitationEntity i where i.inviterAccountId=:account or i.targetEmailNormalized=:email order by i.createdAt desc")
  List<InvitationEntity> visibleTo(@Param("account") UUID account, @Param("email") String email);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select i from InvitationEntity i where (i.inviterAccountId=:account or i.targetEmailNormalized=:email) and i.status in ('PENDING_DELIVERY','PENDING','DELIVERY_FAILED')")
  List<InvitationEntity> activeInvolvingForUpdate(
      @Param("account") UUID account, @Param("email") String email);
}
