package io.github.ifamneme.mneme_auth.application;

public class InvalidRefreshTokenException extends RuntimeException {

	public InvalidRefreshTokenException() {
		super("invalid refresh token");
	}
}
