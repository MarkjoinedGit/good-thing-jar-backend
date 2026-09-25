package com.goodthingjar.identity.application;

import com.goodthingjar.identity.persistence.AccountEntity;
import com.goodthingjar.identity.persistence.AccountRepository;
import com.goodthingjar.platform.error.ProtectedResourceErrors;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class IdentityAccess {
  public record AccountView(UUID id, String normalizedEmail, boolean verified) {}

  private final AccountRepository accounts;

  public IdentityAccess(AccountRepository accounts) {
    this.accounts = accounts;
  }

  public AccountView require(UUID id) {
    AccountEntity a = accounts.findById(id).orElseThrow(ProtectedResourceErrors::notFound);
    return view(a);
  }

  public AccountView requireForUpdate(UUID id) {
    return view(accounts.findByIdForUpdate(id).orElseThrow(ProtectedResourceErrors::notFound));
  }

  public java.util.Optional<AccountView> byEmail(String email) {
    return accounts.findByEmailNormalized(email).map(IdentityAccess::view);
  }

  public List<AccountView> lockOrdered(UUID first, UUID second) {
    return java.util.stream.Stream.of(first, second).sorted().map(this::requireForUpdate).toList();
  }

  private static AccountView view(AccountEntity a) {
    return new AccountView(a.getId(), a.getEmailNormalized(), a.isVerified());
  }
}
