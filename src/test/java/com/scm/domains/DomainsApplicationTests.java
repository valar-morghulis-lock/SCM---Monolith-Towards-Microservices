package com.scm.domains;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scm.config.TestDbConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class DomainsApplicationTests {
	@MockitoBean
	private KafkaTemplate<String, String> kafkaTemplate;




	@Test
	void contextLoads() {
	}

}
