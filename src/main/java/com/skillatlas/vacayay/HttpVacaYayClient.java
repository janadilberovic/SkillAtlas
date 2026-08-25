package com.skillatlas.vacayay;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.skillatlas.vacayay.dto.VacaYayEmployee;
import com.skillatlas.vacayay.exception.VacaYayUnavailableException;

/**
 * The only file that knows VacaYAY is a .NET service on a URL. It logs in as a service account,
 * keeps the token until it is nearly expired, and pages through {@code GET /api/employees} — which
 * is {@code HrOnly} on the other side, so the token is not optional.
 */
@Component
public class HttpVacaYayClient implements VacaYayClient {

    private static final Logger log = LoggerFactory.getLogger(HttpVacaYayClient.class);

    /** Re-login this long before the stated expiry, so no request starts on a token that dies mid-flight. */
    private static final Duration EXPIRY_MARGIN = Duration.ofMinutes(1);

    private final RestClient http;
    private final String email;
    private final String password;
    private final int pageSize;
    private final int maxPages;

    private volatile String token;
    private volatile Instant tokenExpiresAt = Instant.EPOCH;

    public HttpVacaYayClient(RestClient vacaYayRestClient,
            @Value("${vacayay.email}") String email,
            @Value("${vacayay.password}") String password,
            @Value("${vacayay.page-size}") int pageSize,
            @Value("${vacayay.max-pages}") int maxPages) {
        this.http = vacaYayRestClient;
        this.email = email;
        this.password = password;
        this.pageSize = pageSize;
        this.maxPages = maxPages;
    }

    @Override
    public List<VacaYayEmployee> fetchEmployees() {
        if (email.isBlank() || password.isBlank()) {
            throw new VacaYayUnavailableException(
                    "VacaYAY service account is not configured (VACAYAY_USER / VACAYAY_PASSWORD).");
        }
        List<VacaYayEmployee> all = new ArrayList<>();
        for (int page = 1; page <= maxPages; page++) {
            EmployeePage response = employeePage(page);
            all.addAll(response.items());
            if (all.size() >= response.totalCount() || response.items().isEmpty()) {
                return all;
            }
        }
        // Not an exception: a truncated roster still imports, and the number the screen shows is
        // the number that was actually read.
        log.warn("VacaYAY returned more than {} pages of {}; stopping at {} employees.",
                maxPages, pageSize, all.size());
        return all;
    }

    /** One page, with exactly one re-login retry: a token can expire between two pages. */
    private EmployeePage employeePage(int page) {
        try {
            return getEmployees(page, accessToken());
        } catch (HttpClientErrorException.Unauthorized e) {
            token = null;
            return getEmployees(page, accessToken());
        } catch (ResourceAccessException e) {
            throw new VacaYayUnavailableException("VacaYAY did not respond. Is the old system running?", e);
        } catch (RestClientException e) {
            throw new VacaYayUnavailableException("VacaYAY refused the employee list: " + e.getMessage(), e);
        }
    }

    private EmployeePage getEmployees(int page, String bearer) {
        return http.get()
                .uri(uri -> uri.path("/api/employees")
                        .queryParam("page", page)
                        .queryParam("pageSize", pageSize)
                        .build())
                .header("Authorization", "Bearer " + bearer)
                .retrieve()
                .body(EmployeePage.class);
    }

    private String accessToken() {
        String cached = token;
        if (cached != null && Instant.now().isBefore(tokenExpiresAt.minus(EXPIRY_MARGIN))) {
            return cached;
        }
        return login();
    }

    private synchronized String login() {
        LoginResponse response;
        try {
            response = http.post()
                    .uri("/api/auth/login")
                    .body(new LoginRequest(email, password))
                    .retrieve()
                    .body(LoginResponse.class);
        } catch (ResourceAccessException e) {
            throw new VacaYayUnavailableException("VacaYAY did not respond. Is the old system running?", e);
        } catch (RestClientException e) {
            throw new VacaYayUnavailableException("VacaYAY rejected the service account: " + e.getMessage(), e);
        }
        if (response == null || response.accessToken() == null) {
            // VacaYAY answers 200 with a null token and mustChangePassword=true while the account
            // still holds the temporary password HR issued it.
            throw new VacaYayUnavailableException(
                    "VacaYAY issued no token for the service account. Give it a permanent password first.");
        }
        token = response.accessToken();
        tokenExpiresAt = response.expiresAtUtc() != null
                ? response.expiresAtUtc()
                : Instant.now().plus(Duration.ofHours(1));
        return token;
    }

    private record LoginRequest(String email, String password) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LoginResponse(String accessToken, Instant expiresAtUtc) {
    }

    /** VacaYAY's {@code PagedResult<EmployeeDto>}. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record EmployeePage(List<VacaYayEmployee> items, int page, int pageSize, int totalCount) {
    }
}
