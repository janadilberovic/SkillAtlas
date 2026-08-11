package com.skillatlas.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for integration tests that run against a real Neo4j.
 *
 * <p>Reads the same {@code NEO4J_*} env vars as {@code application.yml}, so the tests hit whatever
 * Neo4j you already run locally — a Neo4j Desktop instance or the {@code docker compose} one, no
 * code change either way. Just start the database and set the password:
 *
 * <pre>
 *   $env:NEO4J_PASSWORD = "..."      # Neo4j Desktop: start the DBMS, use its password
 *   ./mvnw.cmd verify                # or: NEO4J_PASSWORD=... docker compose up -d
 * </pre>
 *
 * <p>(Testcontainers was the original plan, but docker-java can't talk to Docker Desktop 29.x over
 * the Windows named pipe on this machine. On a Linux CI a Testcontainers variant can be
 * reintroduced.)
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractNeo4jIT {

    @DynamicPropertySource
    static void neo4jProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.neo4j.uri", () -> env("NEO4J_URI", "bolt://localhost:7687"));
        registry.add("spring.neo4j.authentication.username", () -> env("NEO4J_USERNAME", "neo4j"));
        registry.add("spring.neo4j.authentication.password", () -> env("NEO4J_PASSWORD", "testpassword"));
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
