package com.scm.domains;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.scm.domains", "com.scm.config"})
@EnableScheduling
public class DomainsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DomainsApplication.class, args);
	}

}
