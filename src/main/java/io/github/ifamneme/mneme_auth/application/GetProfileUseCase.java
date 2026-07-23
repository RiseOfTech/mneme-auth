package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.UserId;

public interface GetProfileUseCase {

	ProfileView getProfile(UserId userId);
}
