package com.skillatlas.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleService;
import com.skillatlas.people.dto.PersonCreateRequest;
import com.skillatlas.people.enums.Role;

/**
 * Opens the first door into a fresh production database, where DevSeeder is off: login needs an
 * existing person and creating a person needs an admin token, so without this there is no way in.
 *
 * <p>Creates nothing once any admin exists, which makes it a one-time bootstrap rather than a
 * standing back door. Unlike {@code DevSeeder} it never logs the password.
 */
@Component
@Order(1)
@ConditionalOnProperty(name = "skillatlas.admin.bootstrap-email")
public class AdminBootstrap implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final PeopleRepository people;
    private final PeopleService peopleService;
    private final String email;
    private final String password;

    public AdminBootstrap(PeopleRepository people, PeopleService peopleService,
            @Value("${skillatlas.admin.bootstrap-email}") String email,
            @Value("${skillatlas.admin.bootstrap-password:}") String password) {
        this.people = people;
        this.peopleService = peopleService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (people.existsByRoleAndDeletedFalse(Role.ADMIN)) {
            log.info("AdminBootstrap: an admin already exists, skipping");
            return;
        }
        // The service takes the DTO straight, so no @Valid runs — an 8-char floor that the REST path
        // enforces would otherwise be skipped exactly where the account matters most.
        if (password == null || password.length() < 8) {
            throw new IllegalStateException(
                    "skillatlas.admin.bootstrap-password must be set and at least 8 characters");
        }
        peopleService.create(new PersonCreateRequest(email, password, "Site", "Admin", "Admin", Role.ADMIN));
        log.info("AdminBootstrap: created the first admin ({})", email);
    }
}
