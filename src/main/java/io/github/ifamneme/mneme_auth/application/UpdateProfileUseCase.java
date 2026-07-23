package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.UserId;

public interface UpdateProfileUseCase {

	ProfileView updateProfile(UserId userId, Nickname nickname);
}
