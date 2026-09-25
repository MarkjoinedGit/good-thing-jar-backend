package com.goodthingjar.jar.api;

import com.goodthingjar.jar.application.*;
import com.goodthingjar.platform.security.AuthenticatedAccount;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/jars/{jarId}/entries")
public class EntryController {
  private final EntryCommandService commands;
  private final EntryQueryService queries;

  public EntryController(EntryCommandService c, EntryQueryService q) {
    commands = c;
    queries = q;
  }

  @PostMapping
  ResponseEntity<Void> create(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @Valid @RequestBody JarDtos.CreateEntryRequest request) {
    commands.add(actor.accountId(), jarId, request.text());
    return ResponseEntity.noContent().build();
  }

  @GetMapping
  JarDtos.EntryPage list(
      @AuthenticationPrincipal AuthenticatedAccount actor,
      @PathVariable UUID jarId,
      @RequestParam(required = false) @Size(max = 512) String cursor,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
    var page = queries.page(actor.accountId(), jarId, cursor, limit);
    return new JarDtos.EntryPage(
        page.items().stream()
            .map(
                e ->
                    new JarDtos.EntryResponse(
                        e.getId(), e.getText(), e.getAuthorAccountId(), e.getCreatedAt()))
            .toList(),
        page.nextCursor(),
        page.hasMore());
  }
}
