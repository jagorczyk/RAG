package com.rag.rag;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
		"rag.ingest.async-enabled=false",
		"app.rate-limit.enabled=false",
		"app.identity-cache.enabled=false",
		"spring.autoconfigure.exclude="
				+ "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration,"
				+ "org.springframework.boot.amqp.autoconfigure.health.RabbitHealthContributorAutoConfiguration,"
				+ "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
				+ "org.springframework.boot.data.redis.autoconfigure.health.DataRedisHealthContributorAutoConfiguration"
})
class RagApplicationTests {

	@Test
	void contextLoads() {
	}
}
