package com.skillatlas;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.skillatlas.people.MentorshipsRepository;
import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleService;
import com.skillatlas.people.PeopleSkillsRepository;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.projects.ProjectsRepository;
import com.skillatlas.projects.ProjectsService;
import com.skillatlas.projects.domain.Project;
import com.skillatlas.projects.dto.ProjectCreateRequest;
import com.skillatlas.projects.dto.ProjectMemberRequest;
import com.skillatlas.projects.dto.ProjectUpdateRequest;
import com.skillatlas.skills.SkillsRepository;
import com.skillatlas.skills.SkillsService;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.dto.SkillCreateRequest;
import com.skillatlas.skills.enums.SkillCategory;
import com.skillatlas.teams.TeamsRepository;
import com.skillatlas.teams.TeamsService;
import com.skillatlas.teams.domain.Team;
import com.skillatlas.teams.dto.TeamCreateRequest;

/**
 * TEMPORARY: throwaway seed for local dev. Delete once the VacaYAY import (E3) can fill the graph.
 *
 * <p>Gives you a login-testable admin ({@code admin@skillatlas.dev} / {@code Password123!}) plus a
 * company big enough to exercise the expert finder: six teams, ~30 skills, ~40 people with KNOWS
 * levels spread over 1–5, and two soft-deleted people who must never show up in a result.
 *
 * <p>Idempotent: every node is created only when it is missing, so restarting the app neither
 * duplicates nor overwrites anything you changed through the UI. Disable with
 * {@code skillatlas.seed.enabled=false} (the integration tests do, so they get a quiet database).
 */
@Component
@ConditionalOnProperty(name = "skillatlas.seed.enabled", havingValue = "true", matchIfMissing = true)
public class DevSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSeeder.class);

    private static final String DEMO_PASSWORD = "Password123!";

    private final PeopleRepository people;
    private final PeopleService peopleService;
    private final PeopleSkillsRepository peopleSkills;
    private final MentorshipsRepository mentorships;
    private final SkillsRepository skills;
    private final SkillsService skillsService;
    private final TeamsRepository teams;
    private final TeamsService teamsService;
    private final ProjectsRepository projects;
    private final ProjectsService projectsService;

    public DevSeeder(PeopleRepository people, PeopleService peopleService,
            PeopleSkillsRepository peopleSkills, MentorshipsRepository mentorships,
            SkillsRepository skills, SkillsService skillsService,
            TeamsRepository teams, TeamsService teamsService,
            ProjectsRepository projects, ProjectsService projectsService) {
        this.people = people;
        this.peopleService = peopleService;
        this.peopleSkills = peopleSkills;
        this.mentorships = mentorships;
        this.skills = skills;
        this.skillsService = skillsService;
        this.teams = teams;
        this.teamsService = teamsService;
        this.projects = projects;
        this.projectsService = projectsService;
    }

    @Override
    public void run(String... args) {
        if (!people.existsByEmail("admin@skillatlas.dev")) {
            peopleService.create(new PersonCreateRequest(
                    "admin@skillatlas.dev", DEMO_PASSWORD, "Site", "Admin", "Admin", null, Role.ADMIN));
            log.info("DevSeeder: created admin@skillatlas.dev / {}", DEMO_PASSWORD);
        }

        Map<String, String> teamIds = ensureTeams();
        Map<String, String> skillIds = ensureSkills();
        int created = ensurePeople(teamIds, skillIds);
        ensureProjects(skillIds);
        ensureMentorships(skillIds);

        if (created > 0) {
            log.info("DevSeeder: created {} demo people (password {}), {} skills, {} teams",
                    created, DEMO_PASSWORD, skillIds.size(), teamIds.size());
        }
    }

    private Map<String, String> ensureTeams() {
        Map<String, String> ids = new HashMap<>();
        for (String name : TEAMS) {
            String id = teams.findByName(name)
                    .map(Team::getId)
                    .orElseGet(() -> teamsService.create(new TeamCreateRequest(name)).getId());
            ids.put(name, id);
        }
        return ids;
    }

    /** Keyed by lowercased name, because that is how {@link DemoPerson#skills()} spells them. */
    private Map<String, String> ensureSkills() {
        Map<String, String> ids = new HashMap<>();
        for (DemoSkill demo : SKILLS) {
            String id = skills.findByName(demo.name())
                    .map(Skill::getId)
                    .orElseGet(() -> skillsService
                            .create(new SkillCreateRequest(demo.name(), demo.category(), demo.color()))
                            .getId());
            ids.put(demo.name().toLowerCase(Locale.ROOT), id);
        }
        return ids;
    }

    private int ensurePeople(Map<String, String> teamIds, Map<String, String> skillIds) {
        int created = 0;
        for (DemoPerson demo : PEOPLE) {
            // Skips people who already exist, including soft-deleted ones — Person.email is unique
            // in the database, so a second insert would fail the constraint rather than duplicate.
            if (people.existsByEmail(demo.email())) {
                continue;
            }
            Person person = peopleService.create(new PersonCreateRequest(
                    demo.email(), DEMO_PASSWORD, demo.firstName(), demo.lastName(),
                    demo.position(), null, Role.MEMBER));
            teamsService.addMember(teamIds.get(demo.team()), person.getId());
            for (String entry : demo.skills().split(",")) {
                String[] parts = entry.split(":");
                String skillId = skillIds.get(parts[0].trim().toLowerCase(Locale.ROOT));
                peopleSkills.upsertKnows(person.getId(), skillId, Integer.parseInt(parts[1].trim()),
                        LocalDate.of(2024, 1, 1));
            }
            if (demo.softDeleted()) {
                peopleService.softDelete(person.getId());
            }
            created++;
        }
        return created;
    }

    /**
     * Projects and their members (WORKED_ON). Re-running is safe: the project is created only when
     * a project of that name is missing, and {@code assignMember} MERGEs the relationship.
     */
    private void ensureProjects(Map<String, String> skillIds) {
        for (DemoProject demo : PROJECTS) {
            String projectId = projects.findByName(demo.name())
                    .map(Project::getId)
                    .orElseGet(() -> createProject(demo, skillIds));
            for (String entry : demo.members().split(",")) {
                String[] parts = entry.split(":");
                people.findByEmailAndDeletedFalse(parts[0].trim() + "@skillatlas.dev")
                        .ifPresent(person -> projectsService.assignMember(projectId, person.getId(),
                                new ProjectMemberRequest(parts[1].trim(), demo.startDate(), demo.endDate())));
            }
        }
    }

    private String createProject(DemoProject demo, Map<String, String> skillIds) {
        Set<String> uses = new LinkedHashSet<>();
        for (String name : demo.skills().split(",")) {
            uses.add(skillIds.get(name.trim().toLowerCase(Locale.ROOT)));
        }
        Project project = projectsService.create(new ProjectCreateRequest(
                demo.name(), demo.description(), demo.startDate(), demo.endDate(), uses));
        if (!demo.active()) {
            // create() always starts a project active; a finished one needs the follow-up update.
            projectsService.update(project.getId(), new ProjectUpdateRequest(
                    demo.name(), demo.description(), demo.startDate(), demo.endDate(), false, uses));
        }
        return project.getId();
    }

    /**
     * MENTORS edges, so the profile has mentoring to show in both directions before E6.1 builds the
     * admin flow that creates them for real.
     */
    private void ensureMentorships(Map<String, String> skillIds) {
        for (DemoMentorship demo : MENTORSHIPS) {
            String skillId = skillIds.get(demo.skill().toLowerCase(Locale.ROOT));
            people.findByEmailAndDeletedFalse(demo.mentor() + "@skillatlas.dev").ifPresent(mentor ->
                    people.findByEmailAndDeletedFalse(demo.mentee() + "@skillatlas.dev").ifPresent(mentee ->
                            mentorships.upsertMentorship(mentor.getId(), mentee.getId(), skillId,
                                    LocalDate.of(2024, 9, 1))));
        }
    }

    private record DemoSkill(String name, SkillCategory category, String color) {
    }

    /** {@code members} is an {@code email-local-part:role} list; {@code skills} names from {@link #SKILLS}. */
    private record DemoProject(String name, String description, LocalDate startDate, LocalDate endDate,
            boolean active, String skills, String members) {
    }

    /** Mentor and mentee by email local part; {@code skill} is a name from {@link #SKILLS}. */
    private record DemoMentorship(String mentor, String mentee, String skill) {
    }

    /** {@code skills} is a {@code Name:level} list; the names must exist in {@link #SKILLS}. */
    private record DemoPerson(String email, String firstName, String lastName, String position,
            String team, String skills, boolean softDeleted) {

        DemoPerson(String email, String firstName, String lastName, String position, String team,
                String skills) {
            this(email, firstName, lastName, position, team, skills, false);
        }
    }

    private static final List<String> TEAMS =
            List.of("Backend", "Frontend", "DevOps", "Data", "QA", "Mobile");

    private static final List<DemoSkill> SKILLS = List.of(
            new DemoSkill("Java", SkillCategory.LANGUAGE, "#E76F00"),
            new DemoSkill("TypeScript", SkillCategory.LANGUAGE, "#3178C6"),
            new DemoSkill("Python", SkillCategory.LANGUAGE, "#3776AB"),
            new DemoSkill("Kotlin", SkillCategory.LANGUAGE, "#7F52FF"),
            new DemoSkill("Go", SkillCategory.LANGUAGE, "#00ADD8"),
            new DemoSkill("C#", SkillCategory.LANGUAGE, "#68217A"),
            new DemoSkill("SQL", SkillCategory.LANGUAGE, "#CC2927"),
            new DemoSkill("Spring Boot", SkillCategory.FRAMEWORK, "#6DB33F"),
            new DemoSkill("React", SkillCategory.FRAMEWORK, "#61DAFB"),
            new DemoSkill("Angular", SkillCategory.FRAMEWORK, "#DD0031"),
            new DemoSkill("Next.js", SkillCategory.FRAMEWORK, "#8B8B8B"),
            new DemoSkill(".NET", SkillCategory.FRAMEWORK, "#512BD4"),
            new DemoSkill("Node.js", SkillCategory.FRAMEWORK, "#339933"),
            new DemoSkill("Django", SkillCategory.FRAMEWORK, "#092E20"),
            new DemoSkill("Neo4j", SkillCategory.DATABASE, "#4581C3"),
            new DemoSkill("PostgreSQL", SkillCategory.DATABASE, "#336791"),
            new DemoSkill("MongoDB", SkillCategory.DATABASE, "#47A248"),
            new DemoSkill("Redis", SkillCategory.DATABASE, "#DC382D"),
            new DemoSkill("Elasticsearch", SkillCategory.DATABASE, "#FED10A"),
            new DemoSkill("Docker", SkillCategory.TOOL, "#2496ED"),
            new DemoSkill("Kubernetes", SkillCategory.TOOL, "#326CE5"),
            new DemoSkill("Git", SkillCategory.TOOL, "#F05032"),
            new DemoSkill("Terraform", SkillCategory.TOOL, "#7B42BC"),
            new DemoSkill("GraphQL", SkillCategory.TOOL, "#E10098"),
            new DemoSkill("Cypher tuning", SkillCategory.TOOL, "#018BFF"),
            new DemoSkill("Kafka", SkillCategory.TOOL, "#231F20"),
            new DemoSkill("Playwright", SkillCategory.TOOL, "#2EAD33"),
            new DemoSkill("Figma", SkillCategory.TOOL, "#F24E1E"),
            new DemoSkill("CI/CD", SkillCategory.TOOL, "#4A90D9"));

    // Levels are deliberately uneven: a few skills sit on one pair of shoulders (Cypher tuning,
    // Terraform, Elasticsearch have exactly one person at level 4+) so the bus-factor readout has
    // something real to report, and Neo4j has people at 2, 3, 4 and 5 so a "≥ 4" query visibly
    // drops the weaker ones.
    private static final List<DemoPerson> PEOPLE = List.of(
            new DemoPerson("ada@skillatlas.dev", "Ada", "Lovelace", "Backend Engineer", "Backend",
                    "Neo4j:4,Java:5,Spring Boot:4,SQL:4"),
            new DemoPerson("milan.kostic@skillatlas.dev", "Milan", "Kostić", "Senior Backend Engineer",
                    "Backend", "Neo4j:5,Cypher tuning:5,Java:5,Spring Boot:5,Kafka:4"),
            new DemoPerson("jelena.matic@skillatlas.dev", "Jelena", "Matić", "Backend Engineer",
                    "Backend", "Java:4,Spring Boot:4,PostgreSQL:4,Docker:3"),
            new DemoPerson("nikola.savic@skillatlas.dev", "Nikola", "Savić", "Backend Engineer",
                    "Backend", "Java:3,Neo4j:2,SQL:3,Redis:3"),
            new DemoPerson("petar.ilic@skillatlas.dev", "Petar", "Ilić", "Tech Lead", "Backend",
                    "Java:5,Kotlin:4,Spring Boot:5,Kafka:5,PostgreSQL:4"),
            new DemoPerson("tamara.vukovic@skillatlas.dev", "Tamara", "Vuković", "Backend Engineer",
                    "Backend", "Kotlin:4,Java:3,Neo4j:3,GraphQL:3"),
            new DemoPerson("stefan.pavlovic@skillatlas.dev", "Stefan", "Pavlović", "Backend Engineer",
                    "Backend", "Go:4,Docker:4,Kubernetes:3,PostgreSQL:3"),
            new DemoPerson("marija.jovic@skillatlas.dev", "Marija", "Jović", "Junior Backend Engineer",
                    "Backend", "Java:2,SQL:2,Git:3"),
            new DemoPerson("dusan.radic@skillatlas.dev", "Dušan", "Radić", "Backend Engineer", "Backend",
                    "C#:4,.NET:4,SQL:4,Docker:3"),
            new DemoPerson("ivana.peric@skillatlas.dev", "Ivana", "Perić", "Backend Engineer", "Backend",
                    "Java:4,Neo4j:4,GraphQL:4,Spring Boot:3"),

            new DemoPerson("lena.markovic@skillatlas.dev", "Lena", "Marković", "Frontend Engineer",
                    "Frontend", "TypeScript:5,React:5,Next.js:4,GraphQL:3"),
            new DemoPerson("vuk.stanic@skillatlas.dev", "Vuk", "Stanić", "Senior Frontend Engineer",
                    "Frontend", "TypeScript:5,Angular:5,React:3,Figma:3"),
            new DemoPerson("anja.kovac@skillatlas.dev", "Anja", "Kovač", "Frontend Engineer", "Frontend",
                    "TypeScript:4,React:4,Figma:4"),
            new DemoPerson("filip.novak@skillatlas.dev", "Filip", "Novak", "Frontend Engineer",
                    "Frontend", "TypeScript:3,Angular:4,Node.js:3"),
            new DemoPerson("sara.begic@skillatlas.dev", "Sara", "Begić", "Junior Frontend Engineer",
                    "Frontend", "TypeScript:2,React:2,Git:3"),
            new DemoPerson("bojan.tosic@skillatlas.dev", "Bojan", "Tošić", "Frontend Engineer",
                    "Frontend", "TypeScript:4,Next.js:4,Node.js:4,React:4"),
            new DemoPerson("katarina.lukic@skillatlas.dev", "Katarina", "Lukić", "UX Engineer",
                    "Frontend", "Figma:5,TypeScript:3,React:3"),
            new DemoPerson("relja.mitrovic@skillatlas.dev", "Relja", "Mitrović", "Frontend Engineer",
                    "Frontend", "Angular:3,TypeScript:3,Neo4j:2"),

            new DemoPerson("goran.simic@skillatlas.dev", "Goran", "Simić", "DevOps Engineer", "DevOps",
                    "Docker:5,Kubernetes:5,Terraform:4,CI/CD:5"),
            new DemoPerson("maja.zoric@skillatlas.dev", "Maja", "Zorić", "DevOps Engineer", "DevOps",
                    "Docker:4,Kubernetes:4,CI/CD:4,Go:3"),
            new DemoPerson("aleksa.mladenovic@skillatlas.dev", "Aleksa", "Mladenović", "SRE", "DevOps",
                    "Kubernetes:4,Docker:4,Terraform:3,Redis:4"),
            new DemoPerson("teodora.grubic@skillatlas.dev", "Teodora", "Grubić", "DevOps Engineer",
                    "DevOps", "Docker:3,CI/CD:3,Python:3,Terraform:2"),
            new DemoPerson("igor.blagojevic@skillatlas.dev", "Igor", "Blagojević", "Platform Engineer",
                    "DevOps", "Kubernetes:3,Docker:4,Kafka:3,Go:4"),

            new DemoPerson("natasa.djuric@skillatlas.dev", "Nataša", "Đurić", "Data Engineer", "Data",
                    "Python:5,SQL:5,Kafka:4,PostgreSQL:4"),
            new DemoPerson("luka.arsic@skillatlas.dev", "Luka", "Arsić", "Data Engineer", "Data",
                    "Python:4,SQL:4,Elasticsearch:4,MongoDB:3"),
            new DemoPerson("milica.stevanovic@skillatlas.dev", "Milica", "Stevanović", "Data Scientist",
                    "Data", "Python:5,SQL:3,Django:3"),
            new DemoPerson("bojana.ristic@skillatlas.dev", "Bojana", "Ristić", "Data Engineer", "Data",
                    "Python:3,Neo4j:4,SQL:4,Cypher tuning:3"),
            new DemoPerson("zoran.babic@skillatlas.dev", "Zoran", "Babić", "Data Analyst", "Data",
                    "SQL:4,Python:2,Elasticsearch:2"),
            new DemoPerson("andrej.popovic@skillatlas.dev", "Andrej", "Popović", "Data Engineer", "Data",
                    "Python:4,Kafka:3,MongoDB:4,Redis:3"),

            new DemoPerson("jovana.cvetkovic@skillatlas.dev", "Jovana", "Cvetković", "QA Engineer", "QA",
                    "Playwright:5,TypeScript:4,CI/CD:3"),
            new DemoPerson("marko.despotovic@skillatlas.dev", "Marko", "Despotović", "QA Engineer", "QA",
                    "Playwright:4,Python:3,SQL:3"),
            new DemoPerson("tijana.krstic@skillatlas.dev", "Tijana", "Krstić", "QA Lead", "QA",
                    "Playwright:4,TypeScript:3,Docker:3,CI/CD:4"),
            new DemoPerson("slobodan.gajic@skillatlas.dev", "Slobodan", "Gajić", "QA Engineer", "QA",
                    "Playwright:3,Java:2,Git:4"),
            new DemoPerson("emina.hodzic@skillatlas.dev", "Emina", "Hodžić", "QA Engineer", "QA",
                    "Playwright:3,TypeScript:2"),

            new DemoPerson("david.antic@skillatlas.dev", "David", "Antić", "Mobile Engineer", "Mobile",
                    "Kotlin:5,Java:4,Git:4"),
            new DemoPerson("nevena.bogdanovic@skillatlas.dev", "Nevena", "Bogdanović", "Mobile Engineer",
                    "Mobile", "Kotlin:4,TypeScript:3,React:3"),
            new DemoPerson("uros.jankovic@skillatlas.dev", "Uroš", "Janković", "Mobile Engineer",
                    "Mobile", "Kotlin:3,Java:3,GraphQL:3"),
            new DemoPerson("lara.simovic@skillatlas.dev", "Lara", "Šimović", "Mobile Engineer", "Mobile",
                    "TypeScript:4,React:4,Figma:3"),

            // Soft-deleted on purpose: both would top their searches if the filter ever broke.
            new DemoPerson("dana.deletic@skillatlas.dev", "Dana", "Deletić", "Backend Engineer",
                    "Backend", "Neo4j:5,Java:5,Cypher tuning:5", true),
            new DemoPerson("vanja.arhivic@skillatlas.dev", "Vanja", "Arhivić", "Frontend Engineer",
                    "Frontend", "React:5,TypeScript:5", true));

    // One finished project among the active ones, and people who span teams, so a profile shows a
    // mix rather than one uniform block.
    private static final List<DemoProject> PROJECTS = List.of(
            new DemoProject("SkillAtlas", "Knowledge graph of the company.",
                    LocalDate.of(2025, 1, 13), null, true,
                    "Neo4j,Java,Spring Boot,Angular,Cypher tuning",
                    "milan.kostic:Tech Lead,ada:Backend Engineer,ivana.peric:Backend Engineer,"
                            + "vuk.stanic:Frontend Engineer,jovana.cvetkovic:QA Engineer"),
            new DemoProject("VacaYAY Migration", "Moving the leave system off .NET.",
                    LocalDate.of(2024, 9, 2), null, true,
                    "C#,.NET,PostgreSQL,Docker,Java",
                    "dusan.radic:Backend Engineer,jelena.matic:Backend Engineer,"
                            + "goran.simic:DevOps Engineer"),
            new DemoProject("Data Platform", "Streaming ingest and the reporting warehouse.",
                    LocalDate.of(2024, 4, 1), null, true,
                    "Python,Kafka,Elasticsearch,SQL,PostgreSQL",
                    "natasa.djuric:Data Engineer,luka.arsic:Data Engineer,"
                            + "andrej.popovic:Data Engineer,bojana.ristic:Data Engineer"),
            new DemoProject("Mobile Companion", "Shipped; kept for the archive.",
                    LocalDate.of(2023, 3, 6), LocalDate.of(2024, 6, 28), false,
                    "Kotlin,TypeScript,React,GraphQL",
                    "david.antic:Mobile Engineer,nevena.bogdanovic:Mobile Engineer,"
                            + "lara.simovic:Mobile Engineer,ada:Backend Engineer"));

    // Ada is on both ends on purpose: her profile is the one to open when checking that mentoring
    // renders in both directions.
    private static final List<DemoMentorship> MENTORSHIPS = List.of(
            new DemoMentorship("milan.kostic", "ada", "Cypher tuning"),
            new DemoMentorship("petar.ilic", "ada", "Kafka"),
            new DemoMentorship("ada", "marija.jovic", "Java"),
            new DemoMentorship("ada", "nikola.savic", "Neo4j"),
            new DemoMentorship("lena.markovic", "sara.begic", "React"),
            new DemoMentorship("goran.simic", "teodora.grubic", "Kubernetes"));
}
