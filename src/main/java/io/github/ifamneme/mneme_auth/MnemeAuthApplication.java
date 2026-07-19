package io.github.ifamneme.mneme_auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MnemeAuthApplication {

	public static void main(String[] args) {
		SpringApplication.run(MnemeAuthApplication.class, args);
	}

}
