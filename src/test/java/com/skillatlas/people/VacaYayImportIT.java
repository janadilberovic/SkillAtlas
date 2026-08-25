package com.skillatlas.people;

import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.support.AbstractNeo4jIT;
import com.skillatlas.vacayay.VacaYayClient;
import com.skillatlas.vacayay.dto.VacaYayEmployee;
import com.skillatlas.vacayay.exception.VacaYayUnavailableException;

/**
 * E3 · what the import promises: it dedups by email, it never resurrects a soft-deleted person, it
 * never lets the old system hand out an ADMIN role, and pressing it twice changes nothing.
 *
 * <p>The old system is a mock, so the whole suite runs without a .NET process anywhere.
 */
class VacaYayImportIT extends AbstractNeo4jIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleRepository repository;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;
    @Autowired
    ObjectMapper json;

    @MockitoBean
    VacaYayClient vacaYay;

    String suffix;
    String adminId;
    String memberId;
    String adminToken;
    String memberToken;

    @BeforeEach
    void seed() {
        suffix = UUID.randomUUID().toString().substring(0, 8);
        Person admin = peopleService.create(new PersonCreateRequest(
                "admin-" + suffix + "@test.com", "Password123!", "Site", "Admin", "Admin", Role.ADMIN));
        Person member = peopleService.create(new PersonCreateRequest(
                "member-" + suffix + "@test.com", "Password123!", "Mia", "Member", "Engineer", Role.MEMBER));
        adminId = admin.getId();
        memberId = member.getId();
        adminToken = jwtService.issue(adminId, Role.ADMIN);
        memberToken = jwtService.issue(memberId, Role.MEMBER);
    }

    // The suite runs against a real, possibly shared database — leave nothing behind.
    @AfterEach
    void cleanup() {
        neo4jClient.query("MATCH (p:Person) WHERE p.id IN $ids OR p.email ENDS WITH $suffix DETACH DELETE p")
                .bindAll(Map.of("ids", List.of(adminId, memberId), "suffix", suffix + "@old.test"))
                .run();
    }

    private String email(String local) {
        return local + "-" + suffix + "@old.test";
    }

    private VacaYayEmployee employee(int id, String firstName, String lastName, String mail) {
        return new VacaYayEmployee(id, firstName, lastName, mail, "Employee", "Engineering",
                "Developer", true);
    }

    private JsonNode importIds(int... ids) throws Exception {
        String body = "{\"ids\":[%s]}".formatted(
                String.join(",", java.util.Arrays.stream(ids).mapToObj(String::valueOf).toList()));
        String response = mvc.perform(post("/api/v1/people/import-vacayay")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private long countPeopleWithEmail(String mail) {
        return neo4jClient.query("MATCH (p:Person {email: $email}) RETURN count(p) AS total")
                .bindAll(Map.of("email", mail))
                .fetchAs(Long.class)
                .mappedBy((types, record) -> record.get("total").asLong())
                .one().orElse(0L);
    }

    private long mappingQueueTotal() throws Exception {
        String response = mvc.perform(get("/api/v1/dashboard")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("mappingQueue").path("total").asLong();
    }

    @Test
    void import_asAdmin_createsThePeopleItReports() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina")),
                employee(2, "Amar", "Selimovic", email("amar"))));

        JsonNode result = importIds(1, 2);

        Assertions.assertThat(result.path("imported").asInt()).isEqualTo(2);
        Assertions.assertThat(result.path("skipped").asInt()).isZero();
        Assertions.assertThat(repository.findByEmailAndDeletedFalse(email("nina"))).isPresent();
        Assertions.assertThat(repository.findByEmailAndDeletedFalse(email("amar"))).isPresent();
    }

    @Test
    void import_pressedTwice_importsNothingAndDuplicatesNobody() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina"))));

        importIds(1);
        JsonNode second = importIds(1);

        Assertions.assertThat(second.path("imported").asInt()).isZero();
        Assertions.assertThat(second.path("skipped").asInt()).isEqualTo(1);
        Assertions.assertThat(countPeopleWithEmail(email("nina"))).isEqualTo(1);
    }

    @Test
    void import_doesNotOverwriteAPositionAnAdminFixedByHand() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina"))));
        importIds(1);
        String personId = repository.findByEmailAndDeletedFalse(email("nina")).orElseThrow().getId();
        neo4jClient.query("MATCH (p:Person {id: $id}) SET p.position = 'Tech Lead'")
                .bindAll(Map.of("id", personId)).run();

        importIds(1);

        Assertions.assertThat(repository.findByEmailAndDeletedFalse(email("nina")).orElseThrow()
                .getPosition()).isEqualTo("Tech Lead");
    }

    @Test
    void import_leavesASoftDeletedPersonDeleted() throws Exception {
        Person deleted = peopleService.create(new PersonCreateRequest(
                email("lejla"), "Password123!", "Lejla", "Begic", "QA", Role.MEMBER));
        neo4jClient.query("MATCH (p:Person {id: $id}) SET p.isDeleted = true, p.deletedAt = datetime()")
                .bindAll(Map.of("id", deleted.getId())).run();
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Lejla", "Begic", email("lejla"))));

        JsonNode result = importIds(1);

        Assertions.assertThat(result.path("imported").asInt()).isZero();
        Assertions.assertThat(result.path("skipped").asInt()).isEqualTo(1);
        Assertions.assertThat(countPeopleWithEmail(email("lejla"))).isEqualTo(1);
        Assertions.assertThat(repository.findByIdAndDeletedFalse(deleted.getId())).isEmpty();
    }

    @Test
    void import_treatsADifferentlyCasedEmailAsTheSamePerson() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina"))));
        importIds(1);
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina").toUpperCase(java.util.Locale.ROOT))));

        JsonNode result = importIds(1);

        Assertions.assertThat(result.path("imported").asInt()).isZero();
        Assertions.assertThat(result.path("skipped").asInt()).isEqualTo(1);
        Assertions.assertThat(countPeopleWithEmail(email("nina"))).isEqualTo(1);
    }

    @Test
    void import_neverGrantsAdminEvenWhenTheOldSystemSaysHr() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(new VacaYayEmployee(
                1, "Hana", "Kovac", email("hana"), "HR", "People", "HR Manager", true)));

        JsonNode result = importIds(1);

        Assertions.assertThat(result.path("people").get(0).path("role").asText()).isEqualTo("MEMBER");
        Assertions.assertThat(repository.findByEmailAndDeletedFalse(email("hana")).orElseThrow()
                .getRole()).isEqualTo(Role.MEMBER);
    }

    @Test
    void import_countsAMalformedRowAsInvalidAndCreatesNothing() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                new VacaYayEmployee(1, "Nina", "Hodzic", "not-an-email", "Employee", null, null, true)));

        JsonNode result = importIds(1);

        Assertions.assertThat(result.path("invalid").asInt()).isEqualTo(1);
        Assertions.assertThat(result.path("imported").asInt()).isZero();
        Assertions.assertThat(countPeopleWithEmail("not-an-email")).isZero();
    }

    @Test
    void import_countsAnIdTheOldSystemNoLongerReturnsAsNotFound() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina"))));

        JsonNode result = importIds(1, 999);

        Assertions.assertThat(result.path("imported").asInt()).isEqualTo(1);
        Assertions.assertThat(result.path("notFound").asInt()).isEqualTo(1);
    }

    @Test
    void import_storesCypherInALastNameLiterally() throws Exception {
        String attack = "React'}) DETACH DELETE (n) //";
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", attack, email("nina"))));
        long nodesBefore = countAllNodes();

        importIds(1);

        Assertions.assertThat(countAllNodes()).isEqualTo(nodesBefore + 1);
        Assertions.assertThat(repository.findByEmailAndDeletedFalse(email("nina")).orElseThrow()
                .getLastName()).isEqualTo(attack);
    }

    @Test
    void importedPerson_cannotLogInUntilSomeoneGivesThemAPassword() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina"))));
        importIds(1);

        mvc.perform(post("/api/v1/auth/login")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"anything-at-all\"}".formatted(email("nina"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void import_landsInTheDashboardMappingQueue() throws Exception {
        long before = mappingQueueTotal();
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina")),
                employee(2, "Amar", "Selimovic", email("amar"))));

        importIds(1, 2);

        Assertions.assertThat(mappingQueueTotal()).isEqualTo(before + 2);
    }

    @Test
    void roster_flagsWhoIsAlreadyInAndWhoCannotBeImported() throws Exception {
        when(vacaYay.fetchEmployees()).thenReturn(List.of(
                employee(1, "Nina", "Hodzic", email("nina")),
                new VacaYayEmployee(2, "Broken", "Row", "nope", "Employee", null, null, true)));
        importIds(1);

        mvc.perform(get("/api/v1/people/vacayay-roster?page=0&size=10")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].lastName").value("Hodzic"))
                .andExpect(jsonPath("$.content[0].alreadyImported").value(true))
                .andExpect(jsonPath("$.content[0].issue").doesNotExist())
                .andExpect(jsonPath("$.content[1].alreadyImported").value(false))
                .andExpect(jsonPath("$.content[1].issue").value(org.hamcrest.Matchers.containsString("email")));
    }

    @Test
    void import_isAdminOnly() throws Exception {
        mvc.perform(post("/api/v1/people/import-vacayay")
                .header("Authorization", "Bearer " + memberToken)
                .contentType(APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/people/import-vacayay")
                .contentType(APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/people/vacayay-roster")
                .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void oldSystemDown_answers502RatherThan500() throws Exception {
        when(vacaYay.fetchEmployees())
                .thenThrow(new VacaYayUnavailableException("VacaYAY did not respond."));

        mvc.perform(post("/api/v1/people/import-vacayay")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content("{\"ids\":[1]}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("VacaYAY did not respond."));
    }

    @Test
    void import_withNoIds_isRejectedByValidation() throws Exception {
        mvc.perform(post("/api/v1/people/import-vacayay")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(APPLICATION_JSON).content("{\"ids\":[]}"))
                .andExpect(status().isBadRequest());
    }

    private long countAllNodes() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((types, record) -> record.get("total").asLong())
                .one().orElse(0L);
    }
}
