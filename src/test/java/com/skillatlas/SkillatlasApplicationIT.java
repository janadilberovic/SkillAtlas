package com.skillatlas;

import org.junit.jupiter.api.Test;

import com.skillatlas.support.AbstractNeo4jIT;

// Boots the full application context against a real Neo4j.
// This also exercises SchemaInitializer (constraints); DevSeeder is off in tests, see AbstractNeo4jIT.
// Named *IT so failsafe runs it under `verify` — it needs a database, and `mvnw test` must not.
class SkillatlasApplicationIT extends AbstractNeo4jIT {

	@Test
	void contextLoads() {
	}

}
