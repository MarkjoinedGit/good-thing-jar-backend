package com.goodthingjar.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.goodthingjar.support.PostgresIntegrationSupport;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.parser.OpenAPIV3Parser;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class OpenApiContractTest extends PostgresIntegrationSupport {
  @Autowired
  @Qualifier("requestMappingHandlerMapping")
  RequestMappingHandlerMapping handlerMappings;

  @Autowired Environment environment;

  @Test
  void everyImplementedApiOperationIsDocumentedWithItsResponses() {
    var parsed = new OpenAPIV3Parser().readLocation(contractPath().toString(), null, null);
    assertThat(parsed.getMessages()).isEmpty();
    OpenAPI api = parsed.getOpenAPI();
    assertThat(api).isNotNull();
    assertThat(api.getServers()).extracting(server -> server.getUrl()).containsExactly("/api/v1");
    assertThat(environment.getProperty("server.servlet.context-path")).isEqualTo("/api/v1");

    Set<String> documented = new LinkedHashSet<>();
    api.getPaths()
        .forEach(
            (path, item) ->
                item.readOperationsMap()
                    .forEach(
                        (method, operation) -> {
                          documented.add(canonical(method.name(), path));
                          assertThat(operation.getOperationId()).isNotBlank();
                          assertThat(operation.getResponses()).isNotEmpty();
                        }));

    Set<String> implemented = new LinkedHashSet<>();
    handlerMappings
        .getHandlerMethods()
        .forEach(
            (mapping, handler) -> {
              if (!handler.getBeanType().getPackageName().startsWith("com.goodthingjar")) return;
              Set<RequestMethod> methods = mapping.getMethodsCondition().getMethods();
              for (String pattern : mapping.getPatternValues()) {
                for (RequestMethod method : methods) {
                  implemented.add(canonical(method.name(), pattern));
                }
              }
            });
    assertThat(implemented).isEqualTo(documented);
  }

  @Test
  void reusablePrivacyThrottleAndVerificationContractsAreComplete() {
    OpenAPI api = new OpenAPIV3Parser().read(contractPath().toString());
    var components = api.getComponents();
    assertThat(components.getSchemas())
        .containsKeys(
            "EmailVerificationResendRequest",
            "EmailVerificationResendResponse",
            "Problem",
            "EntryPage");

    var throttle = components.getResponses().get("ThrottledProblem");
    assertThat(throttle).isNotNull();
    assertThat(throttle.getHeaders()).containsKey("Retry-After");
    assertThat(throttle.getContent()).containsKey("application/problem+json");

    assertResponse(api, "/auth/email-verification-resends", PathItem.HttpMethod.POST, "202");
    assertResponse(api, "/auth/email-verification-resends", PathItem.HttpMethod.POST, "429");
    assertResponse(api, "/invitations", PathItem.HttpMethod.POST, "202");
    assertResponse(api, "/invitations", PathItem.HttpMethod.POST, "429");
    assertResponse(api, "/jars/{jarId}/entries", PathItem.HttpMethod.POST, "204");
    assertResponse(api, "/jars/{jarId}/entries", PathItem.HttpMethod.GET, "404");
    assertResponse(api, "/jars/{jarId}/entries", PathItem.HttpMethod.GET, "423");
  }

  private void assertResponse(OpenAPI api, String path, PathItem.HttpMethod method, String status) {
    assertThat(api.getPaths().get(path).readOperationsMap().get(method).getResponses())
        .containsKey(status);
  }

  private Path contractPath() {
    return Path.of(System.getProperty("openapi.spec.path")).toAbsolutePath().normalize();
  }

  private String canonical(String method, String path) {
    return method + " " + path.replaceAll("\\{[^/}]+}", "{}");
  }
}
