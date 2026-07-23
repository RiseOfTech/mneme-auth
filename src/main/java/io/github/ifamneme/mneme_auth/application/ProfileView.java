package io.github.ifamneme.mneme_auth.application;

import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.UserId;

public record ProfileView(UserId userId, Nickname nickname) {}
