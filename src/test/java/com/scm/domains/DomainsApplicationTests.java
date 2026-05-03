package com.scm.domains;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class DomainsApplicationTests {
	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;

	@MockitoBean
	private ObjectMapper objectMapper; // Satisfies the relay dependency


	@Test
	void contextLoads() {
	}

}
