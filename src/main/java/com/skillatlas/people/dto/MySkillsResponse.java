package com.skillatlas.people.dto;

import java.util.Comparator;
import java.util.List;

import com.skillatlas.people.domain.Person;

// A person's self-declared knowledge: skills they KNOW (with level) and skills they WANT_TO_LEARN.
public record MySkillsResponse(
        List<KnownSkill> skills,
        List<WishedSkill> wishes
) {
    public record KnownSkill(String skillId, String name, int level) {
    }

    public record WishedSkill(String skillId, String name) {
    }

    public static MySkillsResponse from(Person p) {
        List<KnownSkill> skills = p.getKnows().stream()
                .map(k -> new KnownSkill(k.getSkill().getId(), k.getSkill().getName(), k.getLevel()))
                .sorted(Comparator.comparing(KnownSkill::name))
                .toList();
        List<WishedSkill> wishes = p.getWantsToLearn().stream()
                .map(w -> new WishedSkill(w.getSkill().getId(), w.getSkill().getName()))
                .sorted(Comparator.comparing(WishedSkill::name))
                .toList();
        return new MySkillsResponse(skills, wishes);
    }
}
