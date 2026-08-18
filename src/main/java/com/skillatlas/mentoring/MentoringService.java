package com.skillatlas.mentoring;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.skillatlas.graph.dto.GraphNode;
import com.skillatlas.graph.enums.GraphNodeKind;
import com.skillatlas.mentoring.dto.LearningPathResponse;
import com.skillatlas.mentoring.dto.MentorCandidate;
import com.skillatlas.mentoring.dto.MentorCandidatesResponse;
import com.skillatlas.mentoring.dto.MentorshipRequest;
import com.skillatlas.mentoring.dto.MentorshipResponse;
import com.skillatlas.mentoring.dto.SkillRef;
import com.skillatlas.mentoring.exception.InvalidMentorshipException;
import com.skillatlas.mentoring.exception.MentorshipNotFoundException;
import com.skillatlas.people.PeopleRepository;
import com.skillatlas.people.PeopleSkillsRepository;
import com.skillatlas.people.domain.Person;
import com.skillatlas.people.exception.PersonNotFoundException;
import com.skillatlas.skills.SkillsRepository;
import com.skillatlas.skills.domain.Skill;
import com.skillatlas.skills.exception.SkillNotFoundException;

@Service
public class MentoringService {

    /** 4.3: a candidate has to know the skill at 3 or better to be offered as a mentor. */
    public static final int MIN_MENTOR_LEVEL = 3;
    static final int DEFAULT_CANDIDATE_LIMIT = 10;
    static final int MAX_CANDIDATE_LIMIT = 50;
    /** Past a handful of hops a "path" stops being something a person can act on. */
    static final int MAX_PATH_HOPS = 6;

    private final MentoringRepository repository;
    private final MentorshipsRepository mentorships;
    private final PeopleRepository people;
    private final PeopleSkillsRepository peopleSkills;
    private final SkillsRepository skills;

    public MentoringService(MentoringRepository repository, MentorshipsRepository mentorships,
            PeopleRepository people, PeopleSkillsRepository peopleSkills, SkillsRepository skills) {
        this.repository = repository;
        this.mentorships = mentorships;
        this.people = people;
        this.peopleSkills = peopleSkills;
        this.skills = skills;
    }

    @Transactional(readOnly = true)
    public MentorCandidatesResponse mentorCandidates(String menteeId, String skillName, Integer limit) {
        Person mentee = requirePerson(menteeId);
        SkillRef skill = requireSkillByName(skillName);
        List<MentorCandidate> candidates = repository.mentorCandidates(
                mentee.getId(), skill.id(), MIN_MENTOR_LEVEL, clamp(limit));
        return new MentorCandidatesResponse(mentee.getId(), fullName(mentee), skill,
                MIN_MENTOR_LEVEL, candidates);
    }

    /**
     * 4.4. Both ends have to exist — a missing person or skill is a 404 — but two existing nodes
     * with nothing between them are an ordinary "no path yet", which the screen states rather than
     * treating as an error.
     */
    @Transactional(readOnly = true)
    public LearningPathResponse learningPath(String personId, String skillName) {
        Person person = requirePerson(personId);
        SkillRef skill = requireSkillByName(skillName);

        return repository.learningPath(person.getId(), skill, MAX_PATH_HOPS)
                .map(path -> withNearestMentor(path, skill))
                .orElseGet(() -> LearningPathResponse.notFound(person.getId(), skill));
    }

    @Transactional
    public MentorshipResponse confirm(MentorshipRequest request) {
        if (request.mentorId().equals(request.menteeId())) {
            throw new InvalidMentorshipException("A person cannot mentor themselves");
        }
        Person mentor = requirePerson(request.mentorId());
        Person mentee = requirePerson(request.menteeId());
        Skill skill = skills.findById(request.skillId())
                .orElseThrow(() -> new SkillNotFoundException(request.skillId()));

        // The level rule is re-checked here, not just when the candidates were listed: the list is
        // a suggestion the client may have kept around, and the write is where the rule binds.
        Integer level = peopleSkills.knownLevel(mentor.getId(), skill.getId());
        if (level == null || level < MIN_MENTOR_LEVEL) {
            throw new InvalidMentorshipException(fullName(mentor) + " does not know " + skill.getName()
                    + " at level " + MIN_MENTOR_LEVEL + " or higher");
        }

        LocalDate since = LocalDate.now();
        mentorships.upsertMentorship(mentor.getId(), mentee.getId(), skill.getId(), since);
        return new MentorshipResponse(mentor.getId(), fullName(mentor), mentee.getId(),
                fullName(mentee), new SkillRef(skill.getId(), skill.getName()), since);
    }

    @Transactional
    public void remove(String mentorId, String menteeId, String skillId) {
        if (!mentorships.existsMentorship(mentorId, menteeId, skillId)) {
            throw new MentorshipNotFoundException(mentorId, menteeId, skillId);
        }
        mentorships.deleteMentorship(mentorId, menteeId, skillId);
    }

    // "Nearest" is the first mentor met walking away from the learner, so the walk order decides
    // it — which is why the mentor lookup returns a map and the ordering stays here.
    private LearningPathResponse withNearestMentor(LearningPathResponse path, SkillRef skill) {
        List<String> peers = path.nodes().stream()
                .filter(n -> n.kind() == GraphNodeKind.PERSON)
                .map(GraphNode::id)
                .filter(id -> !id.equals(path.personId()))
                .toList();
        if (peers.isEmpty()) {
            return path;
        }
        Map<String, LearningPathResponse.NearestMentor> mentors =
                repository.mentorsAmong(peers, skill.id(), MIN_MENTOR_LEVEL);
        LearningPathResponse.NearestMentor nearest = peers.stream()
                .map(mentors::get)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        return new LearningPathResponse(path.personId(), path.skill(), true, path.steps(),
                path.ownLevel(), path.nodes(), path.edges(), nearest);
    }

    private Person requirePerson(String id) {
        return people.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new PersonNotFoundException(id));
    }

    private SkillRef requireSkillByName(String name) {
        String normalized = name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
        return repository.findSkillByName(normalized)
                .orElseThrow(() -> new SkillNotFoundException(name));
    }

    private static int clamp(Integer limit) {
        int value = limit == null ? DEFAULT_CANDIDATE_LIMIT : limit;
        return Math.min(Math.max(value, 1), MAX_CANDIDATE_LIMIT);
    }

    private static String fullName(Person p) {
        return p.getFirstName() + " " + p.getLastName();
    }
}
