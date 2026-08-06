package com.skillatlas.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillatlas.auth.dto.LoginRequest;
import com.skillatlas.auth.dto.LoginResponse;
import com.skillatlas.auth.dto.MeResponse;
import com.skillatlas.auth.exception.InvalidCredentialsException;
import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleService;
import com.skillatlas.people.domain.Person;
import com.skillatlas.security.JwtService;
import com.skillatlas.security.SecurityUtil;

import jakarta.validation.Valid;

import org.springframework.security.crypto.password.PasswordEncoder;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final PeopleRepository peopleRepository;
    private final PeopleService peopleService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthController(PeopleRepository peopleRepository, PeopleService peopleService,
            PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.peopleRepository = peopleRepository;
        this.peopleService = peopleService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        Person person = peopleRepository.findByEmailAndDeletedFalse(request.email())
                .filter(Person::isActive)
                .filter(p -> passwordEncoder.matches(request.password(), p.getPasswordHash()))
                .orElseThrow(InvalidCredentialsException::new);
        return LoginResponse.bearer(jwtService.issue(person.getId(), person.getRole()), person.getRole());
    }

    @GetMapping("/me")
    public MeResponse me() {
        return MeResponse.from(peopleService.getById(SecurityUtil.currentUserId()));
    }
}
