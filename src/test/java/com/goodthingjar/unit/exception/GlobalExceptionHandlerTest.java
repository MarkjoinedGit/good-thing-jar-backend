package com.goodthingjar.unit.exception;

import com.goodthingjar.dto.ApiResponse;
import com.goodthingjar.dto.ValidationErrorDetail;
import com.goodthingjar.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;

class GlobalExceptionHandlerTest {

    @Test
    void shouldReturnStandardizedValidationErrorForBodyValidationFailure() throws Exception {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new TestBody(""), "testBody");
        bindingResult.addError(new FieldError("testBody", "name", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(
            new HandlerMethod(new TestController(), TestController.class.getMethod("create", TestBody.class)).getMethodParameters()[0],
            bindingResult
        );

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Request-Id", "req-123");

        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentNotValid(ex, request);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertFalse(response.getBody().success());
        org.junit.jupiter.api.Assertions.assertEquals("VALIDATION_ERROR", response.getBody().error().code());
        Object details = response.getBody().error().details();
        org.junit.jupiter.api.Assertions.assertInstanceOf(java.util.List.class, details);
        Object firstDetail = ((java.util.List<?>) details).getFirst();
        org.junit.jupiter.api.Assertions.assertInstanceOf(ValidationErrorDetail.class, firstDetail);
        ValidationErrorDetail detail = (ValidationErrorDetail) firstDetail;
        org.junit.jupiter.api.Assertions.assertEquals("name", detail.field());
        org.junit.jupiter.api.Assertions.assertEquals("must not be blank", detail.message());
        org.junit.jupiter.api.Assertions.assertEquals("req-123", response.getBody().requestId());
    }

    @Test
    void shouldReturnNotFoundErrorForResponseStatusException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<Void>> response =
            handler.handleResponseStatus(new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource was not found"), request);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertEquals("NOT_FOUND", response.getBody().error().code());
        org.junit.jupiter.api.Assertions.assertEquals("Resource was not found", response.getBody().error().message());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody().requestId());
    }

    @Test
    void shouldReturnForbiddenForAccessDeniedException() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        MockHttpServletRequest request = new MockHttpServletRequest();

        ResponseEntity<ApiResponse<Void>> response =
            handler.handleAccessDenied(new AccessDeniedException("Denied"), request);

        org.junit.jupiter.api.Assertions.assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertNotNull(response.getBody());
        org.junit.jupiter.api.Assertions.assertEquals("FORBIDDEN", response.getBody().error().code());
    }

    @RestController
    static class TestController {

        public String create(@RequestBody TestBody body) {
            return body.name();
        }
    }

    record TestBody(String name) {
    }
}



