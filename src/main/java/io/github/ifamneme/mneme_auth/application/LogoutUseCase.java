package io.github.ifamneme.mneme_auth.application;

public interface LogoutUseCase {

	void logout(String rawRefreshToken);
}
