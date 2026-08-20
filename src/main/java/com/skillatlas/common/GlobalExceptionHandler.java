package com.skillatlas.common;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.skillatlas.auth.exception.InvalidCredentialsException;
import com.skillatlas.finder.exception.InvalidSkillLevelException;
import com.skillatlas.finder.exception.NoSkillsSelectedException;
import com.skillatlas.graph.exception.InvalidNodeTypeException;
import com.skillatlas.mentoring.exception.InvalidMentorshipException;
import com.skillatlas.mentoring.exception.MentorshipNotFoundException;
import com.skillatlas.people.exception.EmailAlreadyExistsException;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.people.exception.SelfDeleteNotAllowedException;
import com.skillatlas.people.exception.SkillAlreadyMasteredException;
import com.skillatlas.projects.exception.ProjectNotFoundException;
import com.skillatlas.skills.exception.SkillNameAlreadyExistsException;
import com.skillatlas.skills.exception.SkillNotFoundException;
import com.skillatlas.teams.exception.TeamNameAlreadyExistsException;
import com.skillatlas.teams.exception.TeamNotFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> onInvalidCredentials(InvalidCredentialsException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> onAccessDenied(AccessDeniedException ex) {
        return error(HttpStatus.FORBIDDEN, "Forbidden");
    }

    @ExceptionHandler(PersonNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(PersonNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> onEmailExists(EmailAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SelfDeleteNotAllowedException.class)
    public ResponseEntity<Map<String, Object>> onSelfDelete(SelfDeleteNotAllowedException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SkillAlreadyMasteredException.class)
    public ResponseEntity<Map<String, Object>> onSkillAlreadyMastered(SkillAlreadyMasteredException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(SkillNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onSkillNotFound(SkillNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(SkillNameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> onSkillNameExists(SkillNameAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(ProjectNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onProjectNotFound(ProjectNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TeamNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onTeamNotFound(TeamNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(TeamNameAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> onTeamNameExists(TeamNameAlreadyExistsException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(NoSkillsSelectedException.class)
    public ResponseEntity<Map<String, Object>> onNoSkillsSelected(NoSkillsSelectedException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidSkillLevelException.class)
    public ResponseEntity<Map<String, Object>> onInvalidSkillLevel(InvalidSkillLevelException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidNodeTypeException.class)
    public ResponseEntity<Map<String, Object>> onInvalidNodeType(InvalidNodeTypeException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(InvalidMentorshipException.class)
    public ResponseEntity<Map<String, Object>> onInvalidMentorship(InvalidMentorshipException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(MentorshipNotFoundException.class)
    public ResponseEntity<Map<String, Object>> onMentorshipNotFound(MentorshipNotFoundException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> onValidation(MethodArgumentNotValidException ex) {
        Map<String, Object> body = baseBody(HttpStatus.BAD_REQUEST, "Validation failed");
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(fe -> fields.put(fe.getField(), fe.getDefaultMessage()));
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(baseBody(status, message));
    }

    private Map<String, Object> baseBody(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        return body;
    }
}
