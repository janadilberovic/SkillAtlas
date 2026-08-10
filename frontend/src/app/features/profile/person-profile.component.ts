import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PeopleApi, SkillApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { KnownSkill, Person, Skill } from '../../core/models/models';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { LevelBarComponent } from '../../shared/components/level-bar/level-bar.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { MentorMatchingComponent } from '../mentoring/mentor-matching.component';

@Component({
  selector: 'sa-person-profile',
  standalone: true,
  imports: [FormsModule, RouterLink, AvatarComponent, LevelBarComponent, SkeletonComponent, MentorMatchingComponent],
  templateUrl: './person-profile.component.html',
  styleUrl: './person-profile.component.css',
})
export class PersonProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly peopleApi = inject(PeopleApi);
  private readonly skillApi = inject(SkillApi);
  readonly auth = inject(AuthService);

  readonly person = signal<Person | null>(null);
  readonly loading = signal(true);
  readonly skills = signal<Skill[]>([]);
  readonly addOpen = signal(false);
  readonly addError = signal('');
  readonly suggestMentor = signal(false);
  readonly mentorConfirmed = signal(false);
  private readonly extraKnows = signal<KnownSkill[]>([]);

  newSkillId = '';
  newLevel = 3;

  readonly isOwn = computed(() => this.person()?.id === this.auth.user()?.id);
  readonly knows = computed(() => [...(this.person()?.knows ?? []), ...this.extraKnows()]);

  constructor() {
    this.skillApi.list().subscribe((s) => this.skills.set(s));
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id') ?? this.auth.user()?.id ?? '';
      this.loading.set(true);
      this.extraKnows.set([]);
      this.peopleApi.get(id).subscribe({
        next: (p) => {
          this.person.set(p);
          this.loading.set(false);
        },
        error: () => {
          this.person.set(null);
          this.loading.set(false);
        },
      });
    });
  }

  initials(p: Person): string {
    return (p.firstName[0] + p.lastName[0]).toUpperCase();
  }

  // The skill the mentee is looking for — their first wish, else a sensible default.
  mentorSkill(p: Person): string {
    return p.wantsToLearn?.[0]?.name ?? 'Neo4j';
  }

  onMentorClosed(confirmed: boolean): void {
    this.suggestMentor.set(false);
    if (confirmed) this.mentorConfirmed.set(true);
  }

  period(from?: string | null, to?: string | null): string {
    const y = (d?: string | null) => (d ? new Date(d).getFullYear() : null);
    const f = y(from);
    return `${f ?? '—'} — ${to ? y(to) : 'now'}`;
  }

  addSkill(): void {
    this.addError.set('');
    const level = Number(this.newLevel);
    if (!this.newSkillId) {
      this.addError.set('Pick a skill first.');
      return;
    }
    if (!Number.isInteger(level) || level < 1 || level > 5) {
      // Client-side for the feel, server-side for the truth — the API rejects it either way.
      this.addError.set('Level must be a whole number between 1 and 5.');
      return;
    }
    const skill = this.skills().find((s) => s.id === this.newSkillId);
    if (!skill) return;
    if (this.knows().some((k) => k.skill.id === skill.id)) {
      this.addError.set('You already list that skill.');
      return;
    }
    this.extraKnows.update((list) => [...list, { skill, level, since: new Date().getFullYear() }]);
    this.newSkillId = '';
    this.newLevel = 3;
    this.addOpen.set(false);
  }
}
