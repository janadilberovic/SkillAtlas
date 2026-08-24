package com.skillatlas.people;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.azure.storage.blob.BlobContainerClient;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;
import com.skillatlas.security.JwtService;
import com.skillatlas.support.AbstractNeo4jIT;

/**
 * Avatar upload against a real Azurite, for the same reason the other ITs use a real Neo4j: a stub
 * would answer "yes, the container is private" and "yes, the signature works" no matter what the
 * code does. Needs {@code docker compose up -d} (blob endpoint on :10000).
 */
class PersonAvatarIT extends AbstractNeo4jIT {

    private static final byte[] PNG = png();

    @Autowired
    MockMvc mvc;
    @Autowired
    PeopleService peopleService;
    @Autowired
    PeopleRepository peopleRepository;
    @Autowired
    JwtService jwtService;
    @Autowired
    Neo4jClient neo4jClient;
    @Autowired
    BlobContainerClient avatarContainer;

    String adaId;
    String bobId;
    String adaToken;

    @BeforeEach
    void seed() {
        // Production creates it on first upload; the tests that assert "nothing was stored" need
        // it to exist before that ever happens.
        avatarContainer.createIfNotExists();
        String u = UUID.randomUUID().toString().substring(0, 8);
        Person ada = peopleService.create(new PersonCreateRequest(
                "ada-" + u + "@test.com", "Password123!", "Ada", "Lovelace", "Engineer", Role.MEMBER));
        Person bob = peopleService.create(new PersonCreateRequest(
                "bob-" + u + "@test.com", "Password123!", "Bob", "Byte", "Engineer", Role.MEMBER));
        adaId = ada.getId();
        bobId = bob.getId();
        adaToken = jwtService.issue(adaId, Role.MEMBER);
    }

    // Real database, and now a real blob store: leave neither behind.
    @AfterEach
    void cleanup() {
        List.of(adaId, bobId).forEach(id ->
                avatarContainer.listBlobsByHierarchy(id + "/")
                        .forEach(b -> avatarContainer.getBlobClient(b.getName()).deleteIfExists()));
        neo4jClient.query("MATCH (n) WHERE n.id IN $ids DETACH DELETE n")
                .bindAll(Map.of("ids", List.of(adaId, bobId)))
                .run();
    }

    @Test
    void upload_asSelf_storesTheKeyAndReturnsASignedUrl() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/people/{id}/avatar", adaId)
                .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.containsString("sig=")))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andReturn();

        String key = peopleRepository.findByIdAndDeletedFalse(adaId).orElseThrow()
                .getProfilePicture();
        // The key is server-built, never the uploaded filename.
        assertThat(key).startsWith(adaId + "/").endsWith(".png").doesNotContain("me.png");

        String url = json(result, "avatarUrl");
        assertThat(fetch(url).statusCode()).isEqualTo(200);
    }

    @Test
    void profileAndListCarryTheSignedUrl() throws Exception {
        uploadPng();

        mvc.perform(get("/api/v1/people/{id}", adaId).header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value(org.hamcrest.Matchers.containsString("sig=")));

        mvc.perform(get("/api/v1/people").param("search", "Lovelace")
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].avatarUrl")
                        .value(org.hamcrest.Matchers.containsString("sig=")));
    }

    // Without this the SAS is decoration: anyone holding the plain URL would see every avatar.
    @Test
    void blobIsNotPubliclyReadableWithoutTheSignature() throws Exception {
        String url = uploadPng();
        String withoutSas = url.substring(0, url.indexOf('?'));

        assertThat(fetch(withoutSas).statusCode()).isIn(403, 404);
    }

    @Test
    void upload_toAnotherPerson_isForbidden() throws Exception {
        mvc.perform(multipart("/api/v1/people/{id}/avatar", bobId)
                .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isForbidden());

        assertThat(peopleRepository.findByIdAndDeletedFalse(bobId).orElseThrow()
                .getProfilePicture()).isNull();
    }

    // §5: the id only ever reaches Cypher as a bound $id. Two payloads, because the canonical one
    // ends in "//" and so never matches the route at all (400) — the slash-free variant is the one
    // that actually reaches requireSelf and proves the ownership check fires first (403).
    @Test
    void upload_withAnInjectionPayloadAsId_deletesNothing() throws Exception {
        long before = nodeCount();

        for (String payload : List.of("React'}) DETACH DELETE (n) //", "React'}) DETACH DELETE (n)")) {
            mvc.perform(multipart("/api/v1/people/{id}/avatar", payload)
                    .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                    .header("Authorization", "Bearer " + adaToken))
                    .andExpect(status().is4xxClientError());
        }

        assertThat(nodeCount()).isEqualTo(before);
    }

    @Test
    void upload_withoutToken_isUnauthorized() throws Exception {
        mvc.perform(multipart("/api/v1/people/{id}/avatar", adaId)
                .file(new MockMultipartFile("file", "me.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    // The declared Content-Type is a claim by the client; the bytes are the evidence.
    @Test
    void upload_svgDeclaredAsPng_isRejectedAndStoresNothing() throws Exception {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);

        mvc.perform(multipart("/api/v1/people/{id}/avatar", adaId)
                .file(new MockMultipartFile("file", "evil.png", "image/png", svg))
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isBadRequest());

        assertThat(peopleRepository.findByIdAndDeletedFalse(adaId).orElseThrow()
                .getProfilePicture()).isNull();
        assertThat(avatarContainer.listBlobsByHierarchy(adaId + "/")).isEmpty();
    }

    @Test
    void upload_overTheSizeLimit_is413() throws Exception {
        byte[] big = new byte[(int) AvatarService.MAX_BYTES + 1];
        System.arraycopy(PNG, 0, big, 0, PNG.length);

        mvc.perform(multipart("/api/v1/people/{id}/avatar", adaId)
                .file(new MockMultipartFile("file", "big.png", "image/png", big))
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void secondUpload_replacesTheBlobInsteadOfPilingUp() throws Exception {
        uploadPng();
        uploadPng();

        assertThat(avatarContainer.listBlobsByHierarchy(adaId + "/")).hasSize(1);
    }

    @Test
    void delete_clearsTheFieldAndTheBlob() throws Exception {
        uploadPng();

        mvc.perform(delete("/api/v1/people/{id}/avatar", adaId)
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isNoContent());

        assertThat(peopleRepository.findByIdAndDeletedFalse(adaId).orElseThrow()
                .getProfilePicture()).isNull();
        assertThat(avatarContainer.listBlobsByHierarchy(adaId + "/")).isEmpty();
    }

    @Test
    void delete_onAnotherPerson_isForbidden() throws Exception {
        mvc.perform(delete("/api/v1/people/{id}/avatar", bobId)
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isForbidden());
    }

    private long nodeCount() {
        return neo4jClient.query("MATCH (n) RETURN count(n) AS total")
                .fetchAs(Long.class)
                .mappedBy((types, record) -> record.get("total").asLong())
                .one()
                .orElseThrow();
    }

    private String uploadPng() throws Exception {
        MvcResult result = mvc.perform(multipart("/api/v1/people/{id}/avatar", adaId)
                .file(new MockMultipartFile("file", "me.png", "image/png", PNG))
                .header("Authorization", "Bearer " + adaToken))
                .andExpect(status().isOk())
                .andReturn();
        return json(result, "avatarUrl");
    }

    private static String json(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(),
                "$." + field);
    }

    private static HttpResponse<Void> fetch(String url) throws IOException, InterruptedException {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(url)).GET().build(),
                HttpResponse.BodyHandlers.discarding());
    }

    // Smallest valid 1x1 PNG, so the magic bytes are real rather than hand-faked.
    private static byte[] png() {
        return java.util.Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}
