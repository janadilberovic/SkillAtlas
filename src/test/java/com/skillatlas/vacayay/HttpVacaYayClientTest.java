package com.skillatlas.vacayay;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.skillatlas.vacayay.dto.VacaYayEmployee;
import com.skillatlas.vacayay.exception.VacaYayUnavailableException;

/**
 * The wire, without a .NET process: login, token reuse, paging and the one retry.
 *
 * <p>The JSON bodies are VacaYAY's own shapes — {@code PagedResult<EmployeeDto>} and
 * {@code AuthResponse} from {@code VacaYAY.Business/DTOs}, serialised the way ASP.NET does it
 * (camelCase, enums as names). Change them only against a real response, not from memory.
 */
class HttpVacaYayClientTest {

    private static final String BASE = "http://vacayay.test";

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private HttpVacaYayClient client(int pageSize, int maxPages) {
        return new HttpVacaYayClient(builder.build(), "hr@old.test", "s3cret", pageSize, maxPages);
    }

    private void expectLogin(String token) {
        server.expect(requestTo(BASE + "/api/auth/login"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.email").value("hr@old.test"))
                .andExpect(jsonPath("$.password").value("s3cret"))
                .andRespond(withSuccess("""
                        {"accessToken":"%s","expiresAtUtc":"2999-01-01T00:00:00Z",
                         "mustChangePassword":false,
                         "user":{"id":1,"firstName":"Hr","lastName":"Bot","email":"hr@old.test",
                                 "role":"HR","daysOff":0}}
                        """.formatted(token), MediaType.APPLICATION_JSON));
    }

    private static String employeePage(int page, int pageSize, int totalCount, int... ids) {
        String items = IntStream.of(ids)
                .mapToObj(id -> """
                        {"id":%d,"firstName":"Nina","lastName":"Hodzic","email":"nina%d@old.test",
                         "role":"Employee","department":"Engineering","jobTitle":"Developer",
                         "hireDate":"2021-03-01T00:00:00","employmentStartDate":null,
                         "employmentEndDate":null,"daysOff":20,"profileImageUrl":null,
                         "isActive":true}
                        """.formatted(id, id))
                .collect(Collectors.joining(","));
        return """
                {"items":[%s],"page":%d,"pageSize":%d,"totalCount":%d,"totalPages":%d}
                """.formatted(items, page, pageSize, totalCount,
                (totalCount + pageSize - 1) / pageSize);
    }

    private void expectEmployees(int page, int pageSize, String token, String body) {
        server.expect(requestTo(BASE + "/api/employees?page=" + page + "&pageSize=" + pageSize))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer " + token))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    @Test
    void twoFetches_logInOnce_becauseTheTokenIsCached() {
        expectLogin("tok-1");
        expectEmployees(1, 100, "tok-1", employeePage(1, 100, 1, 7));
        expectEmployees(1, 100, "tok-1", employeePage(1, 100, 1, 7));

        HttpVacaYayClient client = client(100, 20);
        client.fetchEmployees();
        List<VacaYayEmployee> second = client.fetchEmployees();

        Assertions.assertThat(second).singleElement()
                .satisfies(e -> Assertions.assertThat(e.email()).isEqualTo("nina7@old.test"));
        server.verify();
    }

    @Test
    void expiredToken_logsInAgainAndRetriesTheSamePageOnce() {
        expectLogin("stale");
        server.expect(requestTo(BASE + "/api/employees?page=1&pageSize=100"))
                .andRespond(withUnauthorizedRequest());
        expectLogin("fresh");
        expectEmployees(1, 100, "fresh", employeePage(1, 100, 1, 3));

        Assertions.assertThat(client(100, 20).fetchEmployees()).hasSize(1);
        server.verify();
    }

    @Test
    void pagesAreFollowedUntilTotalCountIsReached() {
        expectLogin("tok");
        expectEmployees(1, 2, "tok", employeePage(1, 2, 5, 1, 2));
        expectEmployees(2, 2, "tok", employeePage(2, 2, 5, 3, 4));
        expectEmployees(3, 2, "tok", employeePage(3, 2, 5, 5));

        Assertions.assertThat(client(2, 20).fetchEmployees()).hasSize(5);
        server.verify();
    }

    @Test
    void maxPages_stopsTheLoopInsteadOfFollowingForever() {
        expectLogin("tok");
        expectEmployees(1, 2, "tok", employeePage(1, 2, 1000, 1, 2));
        expectEmployees(2, 2, "tok", employeePage(2, 2, 1000, 3, 4));

        Assertions.assertThat(client(2, 2).fetchEmployees()).hasSize(4);
        server.verify();
    }

    @Test
    void serviceAccountStillOnItsTempPassword_failsWithAReadableMessage() {
        // VacaYAY answers 200 with a null token and mustChangePassword=true (AuthService.LoginAsync).
        server.expect(requestTo(BASE + "/api/auth/login"))
                .andRespond(withSuccess("""
                        {"accessToken":null,"expiresAtUtc":null,"mustChangePassword":true,
                         "user":{"id":1,"firstName":"Hr","lastName":"Bot","email":"hr@old.test",
                                 "role":"HR","daysOff":0}}
                        """, MediaType.APPLICATION_JSON));

        Assertions.assertThatThrownBy(() -> client(100, 20).fetchEmployees())
                .isInstanceOf(VacaYayUnavailableException.class)
                .hasMessageContaining("permanent password");
    }

    @Test
    void missingServiceAccount_failsBeforeTouchingTheNetwork() {
        HttpVacaYayClient unconfigured = new HttpVacaYayClient(builder.build(), "", "", 100, 20);

        Assertions.assertThatThrownBy(unconfigured::fetchEmployees)
                .isInstanceOf(VacaYayUnavailableException.class)
                .hasMessageContaining("VACAYAY_USER");
        server.verify();
    }
}
