package io.github.ifamneme.mneme_auth.application;

public class InvalidCredentialsException extends RuntimeException {

	public InvalidCredentialsException() {
		super("invalid credentials");
	}
}
