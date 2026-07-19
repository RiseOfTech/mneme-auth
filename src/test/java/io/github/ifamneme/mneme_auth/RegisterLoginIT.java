package io.github.ifamneme.mneme_auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import io.github.ifamneme.mneme_auth.domain.Email;
import io.github.ifamneme.mneme_auth.domain.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class RegisterLoginIT extends PostgresIntegrationTestBase {

	private record AuthRequest(String email, String password) {}

	private record AuthResponse(String accessToken) {}

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	@Test
	void registerThenLoginRoundTripsOverHttp() {
		ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("roundtrip@example.com", "password123"), AuthResponse.class);

		assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
		assertThat(registerResponse.getBody().accessToken()).isNotBlank();
		assertThat(userRepository.existsByEmail(new Email("roundtrip@example.com"))).isTrue();

		ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
				"/v1/auth/login", new AuthRequest("roundtrip@example.com", "password123"), AuthResponse.class);

		assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(loginResponse.getBody().accessToken()).isNotBlank();
	}

	@Test
	void registeringTheSameNormalisedEmailTwiceReturns409AndCreatesNoSecondRow() {
		restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("dup-http@example.com", "password123"), AuthResponse.class);

		ResponseEntity<String> secondResponse = restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("Dup-Http@Example.com", "password456"), String.class);

		assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(secondResponse.getHeaders().getContentType())
				.isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
		assertThat(secondResponse.getBody()).contains("\"email_taken\"");
	}

	@Test
	void unknownEmailAndWrongPasswordProduceAnIdentical401OverHttp() {
		restTemplate.postForEntity(
				"/v1/auth/register", new AuthRequest("known-http@example.com", "correct-pw"), AuthResponse.class);

		ResponseEntity<String> unknownEmailResponse = restTemplate.postForEntity(
				"/v1/auth/login", new AuthRequest("nobody-http@example.com", "whatever"), String.class);
		ResponseEntity<String> wrongPasswordResponse = restTemplate.postForEntity(
				"/v1/auth/login", new AuthRequest("known-http@example.com", "wrong-pw"), String.class);

		assertThat(unknownEmailResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
		assertThat(unknownEmailResponse.getStatusCode()).isEqualTo(wrongPasswordResponse.getStatusCode());
		assertThat(unknownEmailResponse.getBody()).isEqualTo(wrongPasswordResponse.getBody());
	}
}
