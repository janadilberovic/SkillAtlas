package com.skillatlas.common;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Component;

// Enforces uniqueness at the database level (spec §4: constraints on email and skill name).
// Runs before DevSeeder (@Order 0) so the constraints exist before any data is inserted.
// IF NOT EXISTS makes every startup idempotent.
@Component
@Order(0)
public class SchemaInitializer implements CommandLineRunner {

    private final Neo4jClient client;

    public SchemaInitializer(Neo4jClient client) {
        this.client = client;
    }

    @Override
    public void run(String... args) {
        // guard:allow cypher-location - startup DDL, not a data query
        client.query(
                "CREATE CONSTRAINT person_email_unique IF NOT EXISTS "
                        + "FOR (p:Person) REQUIRE p.email IS UNIQUE")
                .run();
        // guard:allow cypher-location - startup DDL, not a data query
        client.query(
                "CREATE CONSTRAINT skill_name_unique IF NOT EXISTS "
                        + "FOR (s:Skill) REQUIRE s.name IS UNIQUE")
                .run();
        // guard:allow cypher-location - startup DDL, not a data query
        client.query(
                "CREATE CONSTRAINT team_name_unique IF NOT EXISTS "
                        + "FOR (t:Team) REQUIRE t.name IS UNIQUE")
                .run();
    }
}
