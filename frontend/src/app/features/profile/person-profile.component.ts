import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { MentoringApi, PeopleApi, PeopleSkillsApi, SkillApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { LearningPath, PersonProfile, Skill } from '../../core/models/models';
import { MentorMatchingComponent } from '../mentoring/mentor-matching.component';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { LevelBarComponent } from '../../shared/components/level-bar/level-bar.component';
import { SelectComponent } from '../../shared/components/select/select.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-person-profile',
  standalone: true,
  imports: [
    FormsModule,
    RouterLink,
    AvatarComponent,
    LevelBarComponent,
    MentorMatchingComponent,
    SelectComponent,
    SkeletonComponent,
  ],
  templateUrl: './person-profile.component.html',
  styleUrl: './person-profile.component.css',
})
export class PersonProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly peopleApi = inject(PeopleApi);
  private readonly peopleSkillsApi = inject(PeopleSkillsApi);
  private readonly skillApi = inject(SkillApi);
  private readonly mentoringApi = inject(MentoringApi);
  readonly auth = inject(AuthService);

  readonly person = signal<PersonProfile | null>(null);
  readonly loading = signal(true);
  readonly catalog = signal<Skill[]>([]);
  readonly addOpen = signal(false);
  readonly addError = signal('');

  /** The wish an admin is picking a mentor for (E6.1), and the wish being routed (E6.2). */
  readonly mentorSkill = signal<string | null>(null);
  readonly mentorConfirmed = signal<string | null>(null);
  readonly pathSkill = signal<string | null>(null);
  readonly path = signal<LearningPath | null>(null);
  readonly pathLoading = signal(false);
  readonly pathError = signal('');

  newSkillId = '';
  newLevel = 3;
  newWishId = '';

  readonly skillOptions = computed(() => this.catalog().map((s) => ({ value: s.id, label: s.name })));
  readonly isOwn = computed(() => this.person()?.id === this.auth.user()?.id);
  // The server allows owner-or-admin on the path and admin-only on matching; the buttons follow
  // the same rule so nobody is offered a 403.
  readonly canSeePath = computed(() => this.isOwn() || this.auth.isAdmin());

  // An owner's edit reloads the profile rather than patching these from the write's response: a
  // new skill also changes the neighbourhood beside them.
  readonly known = computed(() => this.person()?.skills ?? []);
  readonly wishes = computed(() => this.person()?.wishes ?? []);

  readonly projects = computed(() => this.person()?.projects ?? []);
  readonly mentees = computed(() => this.person()?.mentoring?.mentees ?? []);
  readonly mentors = computed(() => this.person()?.mentoring?.mentors ?? []);
  readonly teams = computed(() => this.person()?.teams ?? []);
  readonly neighbours = computed(() => this.person()?.neighbourhood?.nodes.slice(1) ?? []);
  readonly neighbourCount = computed(() => this.person()?.neighbourhood?.edges.length ?? 0);
  readonly truncated = computed(() => this.person()?.neighbourhood?.truncated ?? false);

  constructor() {
    this.skillApi.list().subscribe((s) => this.catalog.set(s));
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id') ?? this.auth.user()?.id ?? '';
      this.loading.set(true);
      this.load(id);
    });
  }

  private load(id: string): void {
    this.peopleApi.profile(id).subscribe({
      next: (p) => {
        this.person.set(p);
        this.loading.set(false);
      },
      error: () => {
        this.person.set(null);
        this.loading.set(false);
      },
    });
  }

  initials(p: PersonProfile): string {
    return (p.firstName[0] + p.lastName[0]).toUpperCase();
  }

  period(from: string | null | undefined, to: string | null | undefined): string {
    if (!from && !to) return '';
    return `${from ?? '…'} → ${to ?? 'now'}`;
  }

  stepLevel(delta: number): void {
    this.newLevel = Math.min(5, Math.max(1, this.newLevel + delta));
  }

  addSkill(): void {
    this.addError.set('');
    const level = Number(this.newLevel);
    if (!this.newSkillId) {
      this.addError.set('Pick a skill first.');
      return;
    }
    if (!Number.isInteger(level) || level < 1 || level > 5) {
      this.addError.set('Level must be a whole number between 1 and 5.');
      return;
    }
    const id = this.person()!.id;
    this.peopleSkillsApi.setSkill(id, this.newSkillId, level).subscribe({
      next: () => {
        this.newSkillId = '';
        this.newLevel = 3;
        this.addOpen.set(false);
        this.load(id);
      },
      error: () => this.addError.set('Could not save the skill.'),
    });
  }

  removeSkill(skillId: string): void {
    const id = this.person()!.id;
    this.peopleSkillsApi.removeSkill(id, skillId).subscribe(() => this.load(id));
  }

  addWish(): void {
    this.addError.set('');
    if (!this.newWishId) return;
    const id = this.person()!.id;
    this.peopleSkillsApi.addWish(id, this.newWishId).subscribe({
      next: () => {
        this.newWishId = '';
        this.load(id);
      },
      error: () => this.addError.set("Can't add that as a wish (already known at level 5?)."),
    });
  }

  removeWish(skillId: string): void {
    const id = this.person()!.id;
    this.peopleSkillsApi.removeWish(id, skillId).subscribe(() => this.load(id));
  }

  openMentorMatching(skillName: string): void {
    this.mentorConfirmed.set(null);
    this.mentorSkill.set(skillName);
  }

  onMentorClosed(confirmed: boolean): void {
    const skill = this.mentorSkill();
    this.mentorSkill.set(null);
    if (!confirmed || !skill) return;
    this.mentorConfirmed.set(skill);
    // A confirmed mentorship shows up on this very profile, so reload rather than patch.
    this.load(this.person()!.id);
  }

  showPath(skillName: string): void {
    if (this.pathSkill() === skillName) {
      this.pathSkill.set(null);
      return;
    }
    this.pathSkill.set(skillName);
    this.path.set(null);
    this.pathError.set('');
    this.pathLoading.set(true);
    this.mentoringApi.learningPath(this.person()!.id, skillName).subscribe({
      next: (p) => {
        this.path.set(p);
        this.pathLoading.set(false);
      },
      error: () => {
        this.pathError.set('Could not work out a path to that skill.');
        this.pathLoading.set(false);
      },
    });
  }

  /** An edge is drawn in whichever direction it was stored, so the arrow has to say which. */
  stepLabel(index: number): string {
    const p = this.path();
    if (!p || !p.edges[index] || !p.nodes[index]) return '';
    const edge = p.edges[index];
    return edge.source === p.nodes[index].id ? `${edge.type} →` : `← ${edge.type}`;
  }
}
