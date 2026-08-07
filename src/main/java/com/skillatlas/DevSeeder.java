package com.skillatlas;

import java.time.LocalDate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.data.neo4j.core.Neo4jTemplate;
import org.springframework.stereotype.Component;

import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleService;
import com.skillatlas.people.domain.Knows;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.enums.SkillCategory;

// TEMPORARY: throwaway seed for local dev. Delete once real endpoints/tests write data.
// Ensures a login-testable admin exists (admin@skillatlas.dev / Password123!).
@Component
public class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private final Neo4jTemplate template;
    private final PeopleRepository people;
    private final PeopleService peopleService;

    public DevSeeder(Neo4jTemplate template, PeopleRepository people, PeopleService peopleService) {
        this.template = template;
        this.people = people;
        this.peopleService = peopleService;
    }

    @Override
    public void run(String... args) {
        if (!people.existsByEmailAndDeletedFalse("admin@skillatlas.dev")) {
            peopleService.create(new PersonCreateRequest(
                    "admin@skillatlas.dev", "Password123!", "Site", "Admin", "Admin", null, Role.ADMIN));
            log.info("DevSeeder: created admin@skillatlas.dev / Password123!");
        }

        if (template.count(Person.class) <= 1) {
            Skill neo4j = new Skill();
            neo4j.setName("Neo4j");
            neo4j.setCategory(SkillCategory.DATABASE);
            neo4j.setColor("#4581C3");

            Knows knows = new Knows();
            knows.setLevel(4);
            knows.setSince(LocalDate.of(2024, 1, 1));
            knows.setSkill(neo4j);

            Person ada = new Person();
            ada.setEmail("ada@skillatlas.dev");
            ada.setFirstName("Ada");
            ada.setLastName("Lovelace");
            ada.setPosition("Backend Engineer");
            ada.setRole(Role.MEMBER);
            ada.setActive(true);
            ada.setPasswordHash("{noop}seed-not-a-real-hash");
            ada.getKnows().add(knows);

            template.save(ada);
            log.info("DevSeeder: created Ada Lovelace -[KNOWS level 4]-> Neo4j");
        }
    }
}
