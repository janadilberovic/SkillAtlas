import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { PeopleApi, PeopleSkillsApi, SkillApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { MySkills, Person, Skill } from '../../core/models/models';
import { AvatarComponent } from '../../shared/components/avatar/avatar.component';
import { LevelBarComponent } from '../../shared/components/level-bar/level-bar.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-person-profile',
  standalone: true,
  imports: [FormsModule, AvatarComponent, LevelBarComponent, SkeletonComponent],
  templateUrl: './person-profile.component.html',
  styleUrl: './person-profile.component.css',
})
export class PersonProfileComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly peopleApi = inject(PeopleApi);
  private readonly peopleSkillsApi = inject(PeopleSkillsApi);
  private readonly skillApi = inject(SkillApi);
  readonly auth = inject(AuthService);

  readonly person = signal<Person | null>(null);
  readonly loading = signal(true);
  readonly catalog = signal<Skill[]>([]);
  readonly mySkills = signal<MySkills>({ skills: [], wishes: [] });
  readonly addOpen = signal(false);
  readonly addError = signal('');

  newSkillId = '';
  newLevel = 3;
  newWishId = '';

  readonly isOwn = computed(() => this.person()?.id === this.auth.user()?.id);
  readonly known = computed(() => this.mySkills().skills);
  readonly wishes = computed(() => this.mySkills().wishes);

  constructor() {
    this.skillApi.list().subscribe((s) => this.catalog.set(s));
    this.route.paramMap.subscribe((pm) => {
      const id = pm.get('id') ?? this.auth.user()?.id ?? '';
      this.loading.set(true);
      this.mySkills.set({ skills: [], wishes: [] });
      this.peopleApi.get(id).subscribe({
        next: (p) => {
          this.person.set(p);
          this.loading.set(false);
          this.loadSkills(p.id);
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

  private loadSkills(id: string): void {
    this.peopleSkillsApi.mine(id).subscribe((ms) => this.mySkills.set(ms));
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
      next: (ms) => {
        this.mySkills.set(ms);
        this.newSkillId = '';
        this.newLevel = 3;
        this.addOpen.set(false);
      },
      error: () => this.addError.set('Could not save the skill.'),
    });
  }

  removeSkill(skillId: string): void {
    const id = this.person()!.id;
    this.peopleSkillsApi.removeSkill(id, skillId).subscribe((ms) => this.mySkills.set(ms));
  }

  addWish(): void {
    this.addError.set('');
    if (!this.newWishId) return;
    const id = this.person()!.id;
    this.peopleSkillsApi.addWish(id, this.newWishId).subscribe({
      next: (ms) => {
        this.mySkills.set(ms);
        this.newWishId = '';
      },
      error: () => this.addError.set("Can't add that as a wish (already known at level 5?)."),
    });
  }

  removeWish(skillId: string): void {
    const id = this.person()!.id;
    this.peopleSkillsApi.removeWish(id, skillId).subscribe((ms) => this.mySkills.set(ms));
  }
}
