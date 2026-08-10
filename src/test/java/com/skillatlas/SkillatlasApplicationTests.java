package com.skillatlas;

import org.junit.jupiter.api.Test;

import com.skillatlas.support.AbstractNeo4jIT;

// Boots the full application context against a real Neo4j (Testcontainers).
// This also exercises SchemaInitializer (constraints) and DevSeeder on a clean database.
class SkillatlasApplicationTests extends AbstractNeo4jIT {

	@Test
	void contextLoads() {
	}

}
