package com.example.transactionstarter.transaction;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Turns every expected failure into a consistent {@link ApiError} body with a
 * status code that means something.
 *
 * <p>Handling errors in one place rather than with try/catch in each controller
 * method means the status code for a given failure is decided once, and the
 * controller methods stay free of error plumbing.
 *
 * <h2>Status codes and why</h2>
 * <ul>
 *   <li><b>400</b> - the request itself is wrong: a broken field, an
 *       unparseable body, a missing query parameter. The caller must change
 *       what they sent.</li>
 *   <li><b>404</b> - the request is fine, the transaction is not there.</li>
 *   <li><b>409</b> - the request is fine and the transaction is there, but the
 *       request conflicts with the current state of the world: the ID is taken,
 *       or the lifecycle forbids the status move. 409 is the right code for
 *       "well formed, but not possible right now", which is why a duplicate ID
 *       is not a 400.</li>
 * </ul>
 *
 * <p>There is deliberately no catch-all handler for Exception. One would also
 * intercept Spring's own exceptions - the 404 for an unknown URL, the 405 for a
 * wrong method - and report them as 500s. Unexpected errors therefore fall
 * through to Spring's default error response, which is noted as a limitation in
 * the README.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    /**
     * A field failed a Bean Validation constraint. Every broken field is
     * reported, sorted, so the caller can fix them all in one go.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidationFailure(
            MethodArgumentNotValidException exception, HttpServletRequest request) {

        List<String> details = exception.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
                .sorted()
                .toList();

        return build(HttpStatus.BAD_REQUEST, "Validation failed", details, request);
    }

    /**
     * The body could not be read at all - malformed JSON, or a value Jackson
     * could not convert.
     *
     * <p>The common case is an enum: a currency or type outside the permitted
     * set. Jackson rejects it before Bean Validation ever runs, so without this
     * handler the caller would get an opaque 400. Here the permitted values are
     * read off the enum itself and returned, so the message stays correct if a
     * currency is ever added.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(
            HttpMessageNotReadableException exception, HttpServletRequest request) {

        String detail = "Request body is missing or is not valid JSON";

        if (exception.getCause() instanceof InvalidFormatException cause) {
            String field = cause.getPath().isEmpty()
                    ? "value"
                    : cause.getPath().get(cause.getPath().size() - 1).getFieldName();
            Class<?> targetType = cause.getTargetType();

            if (targetType != null && targetType.isEnum()) {
                detail = field + ": '" + cause.getValue() + "' is not permitted; allowed values are "
                        + Arrays.toString(targetType.getEnumConstants());
            } else {
                detail = field + ": '" + cause.getValue() + "' is not a valid value";
            }
        }

        return build(HttpStatus.BAD_REQUEST, "Malformed request", List.of(detail), request);
    }

    /**
     * A required query parameter was not supplied - in practice, a customer
     * lookup with no customerId.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {

        return build(HttpStatus.BAD_REQUEST, "Missing request parameter",
                List.of("Required query parameter '" + exception.getParameterName() + "' is missing"),
                request);
    }

    /**
     * The transaction asked for does not exist.
     */
    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(
            TransactionNotFoundException exception, HttpServletRequest request) {

        return build(HttpStatus.NOT_FOUND, "Transaction not found",
                List.of(exception.getMessage()), request);
    }

    /**
     * The transaction ID supplied on create is already in use.
     */
    @ExceptionHandler(DuplicateTransactionException.class)
    public ResponseEntity<ApiError> handleDuplicate(
            DuplicateTransactionException exception, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, "Duplicate transaction ID",
                List.of(exception.getMessage()), request);
    }

    /**
     * The requested status move is not allowed from the current status.
     */
    @ExceptionHandler(InvalidStatusTransitionException.class)
    public ResponseEntity<ApiError> handleInvalidTransition(
            InvalidStatusTransitionException exception, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, "Invalid status transition",
                List.of(exception.getMessage()), request);
    }

    /**
     * A database constraint was violated. The service checks for a duplicate ID
     * before inserting, so this should not normally fire; it is here as a
     * backstop in case two concurrent creates race past that check, and it
     * makes sure such a request fails with a 409 rather than a 500.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrityViolation(
            DataIntegrityViolationException exception, HttpServletRequest request) {

        return build(HttpStatus.CONFLICT, "Conflict",
                List.of("The request conflicts with data that already exists"), request);
    }

    private ResponseEntity<ApiError> build(HttpStatus status,
                                           String message,
                                           List<String> details,
                                           HttpServletRequest request) {

        ApiError body = new ApiError(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                details,
                request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }
}
