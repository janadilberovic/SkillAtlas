package com.skillatlas.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that run against a real Neo4j.
 *
 * Points Spring Data Neo4j at the local Neo4j started via {@code docker compose up -d}
 * (bolt://localhost:7687). Start it before running the suite:
 *
 * <pre>
 *   NEO4J_PASSWORD=testpassword docker compose up -d
 *   ./mvnw test
 * </pre>
 *
 * The password defaults to {@code testpassword} (Neo4j 5 requires >= 8 chars) and can be
 * overridden with the NEO4J_PASSWORD env var
 * (the same variable docker-compose.yml reads), so the DB and the tests always agree.
 *
 * (Testcontainers was the original plan but docker-java can't talk to Docker Desktop 29.x over
 * the Windows named pipe on this machine; compose + CLI works. On a Linux CI a Testcontainers
 * variant can be reintroduced.)
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractNeo4jIT {

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        String password = System.getenv().getOrDefault("NEO4J_PASSWORD", "testpassword");
        registry.add("spring.neo4j.uri", () -> "bolt://localhost:7687");
        registry.add("spring.neo4j.authentication.username", () -> "neo4j");
        registry.add("spring.neo4j.authentication.password", () -> password);
    }
}
