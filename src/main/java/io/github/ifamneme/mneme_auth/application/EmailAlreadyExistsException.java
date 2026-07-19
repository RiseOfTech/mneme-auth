package io.github.ifamneme.mneme_auth.application;

public class EmailAlreadyExistsException extends RuntimeException {

	public EmailAlreadyExistsException() {
		super("email already registered");
	}
}
