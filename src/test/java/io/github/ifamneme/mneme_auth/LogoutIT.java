package io.github.ifamneme.mneme_auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LogoutIT extends PostgresIntegrationTestBase {

	private record AuthRequest(String email, String password) {}

	private record AuthResponse(String accessToken) {}

	@Autowired
	private TestRestTemplate restTemplate;

	private String refreshCookieFrom(ResponseEntity<AuthResponse> response) {
		String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).isNotNull();
		return setCookie.split(";", 2)[0];
	}

	private ResponseEntity<AuthResponse> registerNewUser(String email) {
		return restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest(email, "password123"), AuthResponse.class);
	}

	private ResponseEntity<AuthResponse> refreshWith(String cookie) {
		HttpHeaders headers = new HttpHeaders();
		headers.add(HttpHeaders.COOKIE, cookie);
		return restTemplate.exchange(
				"/v1/auth/refresh", HttpMethod.POST, new HttpEntity<>(headers), AuthResponse.class);
	}

	private ResponseEntity<Void> logoutWith(String cookie) {
		HttpHeaders headers = new HttpHeaders();
		if (cookie != null) {
			headers.add(HttpHeaders.COOKIE, cookie);
		}
		return restTemplate.exchange("/v1/auth/logout", HttpMethod.POST, new HttpEntity<>(headers), Void.class);
	}

	@Test
	void logoutRevokesTheLineageSoASubsequentRefreshIsRejected() {
		String cookie = refreshCookieFrom(registerNewUser("logout-revoke@example.com"));

		ResponseEntity<Void> logoutResponse = logoutWith(cookie);

		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
		String setCookie = logoutResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("Max-Age=0").contains("Path=/v1/auth");

		ResponseEntity<AuthResponse> refreshAfterLogout = refreshWith(cookie);
		assertThat(refreshAfterLogout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void logoutOnlyRevokesTheCallingSessionOtherSessionsOfTheSameUserStayLive() {
		String email = "logout-isolation@example.com";
		String sessionACookie = refreshCookieFrom(registerNewUser(email));
		String sessionBCookie = refreshCookieFrom(
				restTemplate.postForEntity("/v1/auth/login", new AuthRequest(email, "password123"), AuthResponse.class));

		logoutWith(sessionACookie);

		ResponseEntity<AuthResponse> sessionARefresh = refreshWith(sessionACookie);
		assertThat(sessionARefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

		ResponseEntity<AuthResponse> sessionBRefresh = refreshWith(sessionBCookie);
		assertThat(sessionBRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
	}

	@Test
	void aSecondLogoutOnAnAlreadyLoggedOutSessionIsStillNoContent() {
		String cookie = refreshCookieFrom(registerNewUser("logout-idempotent@example.com"));

		logoutWith(cookie);
		ResponseEntity<Void> secondLogout = logoutWith(cookie);

		assertThat(secondLogout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutWithAnAbsentCookieStillReturnsNoContent() {
		ResponseEntity<Void> response = logoutWith(null);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutWithAnUnknownTokenStillReturnsNoContent() {
		ResponseEntity<Void> response = logoutWith("mneme_refresh=never-issued-token");

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
	}

	@Test
	void logoutWithAnAlreadyConsumedTokenStillRevokesTheLineageAndReturnsNoContent() {
		String firstCookie = refreshCookieFrom(registerNewUser("logout-consumed@example.com"));
		ResponseEntity<AuthResponse> rotated = refreshWith(firstCookie);
		String secondCookie = refreshCookieFrom(rotated);

		// firstCookie's token is now consumed (rotation already replaced it).
		ResponseEntity<Void> logoutOnConsumedToken = logoutWith(firstCookie);
		assertThat(logoutOnConsumedToken.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// Logout on the consumed token still kills the whole lineage, including the live generation.
		ResponseEntity<AuthResponse> refreshAfter = refreshWith(secondCookie);
		assertThat(refreshAfter.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void endToEndRegisterThenLogoutThenRefreshFails() {
		String cookie = refreshCookieFrom(registerNewUser("logout-e2e@example.com"));

		ResponseEntity<Void> logoutResponse = logoutWith(cookie);
		assertThat(logoutResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		ResponseEntity<AuthResponse> refreshResponse = refreshWith(cookie);
		assertThat(refreshResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
