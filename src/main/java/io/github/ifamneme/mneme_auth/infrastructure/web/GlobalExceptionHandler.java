package io.github.ifamneme.mneme_auth.infrastructure.web;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.ifamneme.mneme_auth.application.EmailAlreadyExistsException;
import io.github.ifamneme.mneme_auth.application.InvalidCredentialsException;

@RestControllerAdvice
class GlobalExceptionHandler {

	private static final URI BLANK_TYPE = URI.create("about:blank");

	@ExceptionHandler(MethodArgumentNotValidException.class)
	ProblemDetail handleValidationFailed(MethodArgumentNotValidException ex) {
		return problemDetail(HttpStatus.BAD_REQUEST, "validation_failed");
	}

	@ExceptionHandler(EmailAlreadyExistsException.class)
	ProblemDetail handleEmailAlreadyExists(EmailAlreadyExistsException ex) {
		return problemDetail(HttpStatus.CONFLICT, "email_taken");
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	ProblemDetail handleInvalidCredentials(InvalidCredentialsException ex) {
		return problemDetail(HttpStatus.UNAUTHORIZED, "invalid_credentials");
	}

	private static ProblemDetail problemDetail(HttpStatusCode status, String code) {
		ProblemDetail problem = ProblemDetail.forStatus(status);
		problem.setType(BLANK_TYPE);
		problem.setProperty("code", code);
		return problem;
	}
}
