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

import io.github.ifamneme.mneme_auth.domain.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RefreshSessionsIT extends PostgresIntegrationTestBase {

	private record AuthRequest(String email, String password) {}

	private record AuthResponse(String accessToken) {}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	private String refreshCookieFrom(ResponseEntity<AuthResponse> response) {
		String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).isNotNull();
		// "mneme_refresh=<value>; ..." -> the raw cookie pair to send back on the next request.
		return setCookie.split(";", 2)[0];
	}

	@Test
	void registerAndLoginBothSetARefreshCookieWithABackingSessionRow() {
		ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("cookie-owner@example.com", "password123"), AuthResponse.class);

		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(registerResponse.getBody().accessToken()).isNotBlank();
		String setCookie = registerResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
		assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Lax")
				.contains("Path=/v1/auth/refresh");

		ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
				"/v1/auth/login", new AuthRequest("cookie-owner@example.com", "password123"), AuthResponse.class);
		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(loginResponse.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly");
	}

	@Test
	void refreshRotatesTheTokenWithSlidingExpiryThenReplayOfTheConsumedTokenKillsTheLineage() {
		ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("rotate-me@example.com", "password123"), AuthResponse.class);
		String firstCookie = refreshCookieFrom(loginResponse);

		HttpHeaders headersWithFirstCookie = new HttpHeaders();
		headersWithFirstCookie.add(HttpHeaders.COOKIE, firstCookie);
		ResponseEntity<AuthResponse> firstRefresh = restTemplate.exchange(
				"/v1/auth/refresh",
				HttpMethod.POST,
				new HttpEntity<>(headersWithFirstCookie),
				AuthResponse.class);

		assertThat(firstRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(firstRefresh.getBody().accessToken()).isNotBlank();
		String secondCookie = refreshCookieFrom(firstRefresh);
		assertThat(secondCookie).isNotEqualTo(firstCookie);

		HttpHeaders headersWithSecondCookie = new HttpHeaders();
		headersWithSecondCookie.add(HttpHeaders.COOKIE, secondCookie);
		ResponseEntity<AuthResponse> secondRefresh = restTemplate.exchange(
				"/v1/auth/refresh",
				HttpMethod.POST,
				new HttpEntity<>(headersWithSecondCookie),
				AuthResponse.class);
		assertThat(secondRefresh.getStatusCode()).isEqualTo(HttpStatus.OK);
		String thirdCookie = refreshCookieFrom(secondRefresh);
		assertThat(thirdCookie).isNotEqualTo(secondCookie);

		// Replay of the original (now-consumed) cookie -> 401, and the lineage is dead:
		// even the most recent (third) generation no longer refreshes.
		ResponseEntity<String> replayResponse = restTemplate.exchange(
				"/v1/auth/refresh",
				HttpMethod.POST,
				new HttpEntity<>(headersWithFirstCookie),
				String.class);
		assertThat(replayResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(replayResponse.getBody()).contains("invalid_refresh");

		HttpHeaders headersWithThirdCookie = new HttpHeaders();
		headersWithThirdCookie.add(HttpHeaders.COOKIE, thirdCookie);
		ResponseEntity<String> deadLineageResponse = restTemplate.exchange(
				"/v1/auth/refresh",
				HttpMethod.POST,
				new HttpEntity<>(headersWithThirdCookie),
				String.class);
		assertThat(deadLineageResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void refreshWithNoCookieIsRejected() {
		ResponseEntity<String> response = restTemplate.postForEntity("/v1/auth/refresh", null, String.class);

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(response.getBody()).contains("invalid_refresh");
	}
}
