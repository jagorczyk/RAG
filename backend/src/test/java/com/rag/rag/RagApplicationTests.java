package com.rag.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"rag.ingest.async-enabled=false",
		"spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
})
class RagApplicationTests {

	@Test
	void contextLoads() {
	}
}
