package io.github.ifamneme.mneme_auth.infrastructure.web;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import io.github.ifamneme.mneme_auth.application.GetProfileUseCase;
import io.github.ifamneme.mneme_auth.application.ProfileView;
import io.github.ifamneme.mneme_auth.application.UpdateProfileUseCase;
import io.github.ifamneme.mneme_auth.domain.Nickname;
import io.github.ifamneme.mneme_auth.domain.UserId;

@RestController
public class ProfileController implements ProfileApi {

	private final GetProfileUseCase getProfileUseCase;
	private final UpdateProfileUseCase updateProfileUseCase;

	public ProfileController(GetProfileUseCase getProfileUseCase, UpdateProfileUseCase updateProfileUseCase) {
		this.getProfileUseCase = getProfileUseCase;
		this.updateProfileUseCase = updateProfileUseCase;
	}

	@Override
	public ResponseEntity<ProfileResponse> getProfile(Jwt jwt) {
		ProfileView profile = getProfileUseCase.getProfile(userId(jwt));
		return ResponseEntity.ok(toResponse(profile));
	}

	@Override
	public ResponseEntity<ProfileResponse> updateProfile(Jwt jwt, UpdateProfileRequest request) {
		ProfileView profile = updateProfileUseCase.updateProfile(userId(jwt), new Nickname(request.nickname()));
		return ResponseEntity.ok(toResponse(profile));
	}

	private static UserId userId(Jwt jwt) {
		return UserId.of(UUID.fromString(jwt.getSubject()));
	}

	private static ProfileResponse toResponse(ProfileView profile) {
		String nickname = profile.nickname() == null ? null : profile.nickname().value();
		return new ProfileResponse(profile.userId().value().toString(), nickname);
	}
}
