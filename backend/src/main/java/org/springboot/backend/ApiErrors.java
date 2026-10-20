package org.springboot.backend;

import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.Map;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> api(ApiException e) { return error(e.status(), e.code(), e.getMessage()); }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> size(MaxUploadSizeExceededException e) { return error(413, "size_limit", "ZIP exceeds the upload size limit (20 MB compressed)"); }

    @ExceptionHandler({HttpMessageNotReadableException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class, MultipartException.class, IllegalArgumentException.class})
    ResponseEntity<?> input(Exception e) { return error(400, "invalid_input", "Invalid request; provide valid JSON or a ZIP in multipart field 'file'"); }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<?> missing(Exception e) { return error(404, "not_found", "Resource not found"); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) { return error(405, "method_not_allowed", "HTTP method is not supported for this endpoint"); }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<?> media(Exception e) { return error(415, "unsupported_media_type", "Request content type is not supported"); }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        return error(500, "internal_error", "The analysis could not be completed");
    }

    private ResponseEntity<?> error(int status, String code, String message) {
        return ResponseEntity.status(status).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(Map.of("status", status, "code", code, "message", message, "timestamp", Instant.now().toString()));
    }
}
