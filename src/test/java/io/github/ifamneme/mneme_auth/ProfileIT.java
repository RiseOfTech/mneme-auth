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
class ProfileIT extends PostgresIntegrationTestBase {

	private record AuthRequest(String email, String password) {}

	private record AuthResponse(String accessToken) {}

	private record ProfileResponse(String userId, String nickname) {}

	private record UpdateProfileRequest(String nickname) {}

	@Autowired
	private TestRestTemplate restTemplate;

	private String registerAndGetAccessToken(String email) {
		ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest(email, "password123"), AuthResponse.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		return response.getBody().accessToken();
	}

	private HttpEntity<Void> bearer(String accessToken) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(headers);
	}

	private <T> HttpEntity<T> bearer(String accessToken, T body) {
		HttpHeaders headers = new HttpHeaders();
		headers.setBearerAuth(accessToken);
		return new HttpEntity<>(body, headers);
	}

	@Test
	void loginThenGetThenPutThenGetRoundTripsTheNickname() {
		String accessToken = registerAndGetAccessToken("profile-roundtrip@example.com");

		ResponseEntity<ProfileResponse> initialGet = restTemplate.exchange(
				"/v1/profile", HttpMethod.GET, bearer(accessToken), ProfileResponse.class);
		assertThat(initialGet.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(initialGet.getBody().nickname()).isNull();

		ResponseEntity<ProfileResponse> putResponse = restTemplate.exchange(
				"/v1/profile",
				HttpMethod.PUT,
				bearer(accessToken, new UpdateProfileRequest("  Alice  ")),
				ProfileResponse.class);
		assertThat(putResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(putResponse.getBody().nickname()).isEqualTo("Alice");
		assertThat(putResponse.getBody().userId()).isEqualTo(initialGet.getBody().userId());

		ResponseEntity<ProfileResponse> secondGet = restTemplate.exchange(
				"/v1/profile", HttpMethod.GET, bearer(accessToken), ProfileResponse.class);
		assertThat(secondGet.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(secondGet.getBody().nickname()).isEqualTo("Alice");
	}

	@Test
	void twoDifferentUsersCanBothSetTheSameNickname() {
		String firstToken = registerAndGetAccessToken("profile-alice-1@example.com");
		String secondToken = registerAndGetAccessToken("profile-alice-2@example.com");

		ResponseEntity<ProfileResponse> firstPut = restTemplate.exchange(
				"/v1/profile", HttpMethod.PUT, bearer(firstToken, new UpdateProfileRequest("Alice")), ProfileResponse.class);
		ResponseEntity<ProfileResponse> secondPut = restTemplate.exchange(
				"/v1/profile", HttpMethod.PUT, bearer(secondToken, new UpdateProfileRequest("Alice")), ProfileResponse.class);

		assertThat(firstPut.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(secondPut.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(firstPut.getBody().nickname()).isEqualTo("Alice");
		assertThat(secondPut.getBody().nickname()).isEqualTo("Alice");
		assertThat(firstPut.getBody().userId()).isNotEqualTo(secondPut.getBody().userId());
	}

	@Test
	void putWithABlankNicknameReturns400AndLeavesTheStoredNicknameUnchanged() {
		String accessToken = registerAndGetAccessToken("profile-invalid@example.com");
		restTemplate.exchange(
				"/v1/profile", HttpMethod.PUT, bearer(accessToken, new UpdateProfileRequest("Alice")), ProfileResponse.class);

		ResponseEntity<String> badPut = restTemplate.exchange(
				"/v1/profile", HttpMethod.PUT, bearer(accessToken, new UpdateProfileRequest("   ")), String.class);
		assertThat(badPut.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
		assertThat(badPut.getBody()).contains("validation_failed");

		ResponseEntity<ProfileResponse> get = restTemplate.exchange(
				"/v1/profile", HttpMethod.GET, bearer(accessToken), ProfileResponse.class);
		assertThat(get.getBody().nickname()).isEqualTo("Alice");
	}

	@Test
	void getProfileWithoutATokenReturns401() {
		ResponseEntity<String> response = restTemplate.getForEntity("/v1/profile", String.class);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void publicEndpointsStillRespondWithoutAToken() {
		ResponseEntity<String> health = restTemplate.getForEntity("/actuator/health", String.class);
		assertThat(health.getStatusCode()).isEqualTo(HttpStatus.OK);

		ResponseEntity<AuthResponse> register = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("public-regression@example.com", "password123"), AuthResponse.class);
		assertThat(register.getStatusCode()).isEqualTo(HttpStatus.CREATED);

		ResponseEntity<String> login = restTemplate.postForEntity(
				"/v1/auth/login", new AuthRequest("nobody@example.com", "whatever"), String.class);
		assertThat(login.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(login.getBody()).contains("invalid_credentials");
		assertThat(login.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();

		ResponseEntity<String> refresh = restTemplate.postForEntity("/v1/auth/refresh", null, String.class);
		assertThat(refresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(refresh.getBody()).contains("invalid_refresh");
		assertThat(refresh.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();

		ResponseEntity<Void> logout = restTemplate.postForEntity("/v1/auth/logout", null, Void.class);
		assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

		// The actual regression this slice introduced: a GET on a POST-only /v1/auth/** route
		// throws an unhandled MVC exception (HttpRequestMethodNotSupportedException), which Boot
		// forwards internally to /error. Before the SecurityConfig fix, that forwarded dispatch
		// re-entered the SecurityFilterChain, didn't match the permitAll list, and came back as a
		// bare 401 instead of the real 405 -- meaning /v1/auth/** was only "public" for requests
		// that never threw. Driving this over real HTTP (full app context, not a WebMvcTest
		// slice) is what makes this assertion catch it: a sliced/mocked security setup never
		// exercises Boot's actual error-dispatch forwarding.
		ResponseEntity<String> methodNotAllowed = restTemplate.getForEntity("/v1/auth/register", String.class);
		assertThat(methodNotAllowed.getStatusCode()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED);
		assertThat(methodNotAllowed.getHeaders().get(HttpHeaders.WWW_AUTHENTICATE)).isNull();

		ResponseEntity<String> profileWithoutToken = restTemplate.getForEntity("/v1/profile", String.class);
		assertThat(profileWithoutToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
	}
}
