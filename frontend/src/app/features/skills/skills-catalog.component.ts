import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ProjectApi, SkillApi } from '../../core/api/api';
import { Project, Skill, SkillCategory } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

const CATEGORIES: SkillCategory[] = ['FRAMEWORK', 'LANGUAGE', 'TOOL', 'DATABASE'];
const PALETTE = ['#9184d9', '#b5afe8', '#75798c', '#5c5783'];

@Component({
  selector: 'sa-skills-catalog',
  standalone: true,
  imports: [FormsModule, SkeletonComponent],
  templateUrl: './skills-catalog.component.html',
  styleUrl: './skills-catalog.component.css',
})
export class SkillsCatalogComponent {
  private readonly api = inject(SkillApi);
  private readonly projectApi = inject(ProjectApi);
  readonly categories = CATEGORIES;
  readonly palette = PALETTE;

  readonly skills = signal<Skill[]>([]);
  readonly loading = signal(true);
  readonly focus = signal<Skill | null>(null);
  readonly addError = signal('');
  private readonly projects = signal<Project[]>([]);

  newName = '';
  newCategory: SkillCategory = 'FRAMEWORK';
  newColor = PALETTE[0];

  readonly mostWanted = computed(() =>
    [...this.skills()].sort((a, b) => (b.wantedBy ?? 0) - (a.wantedBy ?? 0)).slice(0, 4),
  );
  readonly maxWanted = computed(() => Math.max(1, ...this.skills().map((s) => s.wantedBy ?? 0)));

  constructor() {
    this.reload();
    this.projectApi.list().subscribe((p) => this.projects.set(p));
  }

  private reload(): void {
    this.loading.set(true);
    this.api.list().subscribe((s) => {
      this.skills.set(s);
      this.loading.set(false);
      // Default the impact card to the most at-risk skill (fewest people know it).
      if (!this.focus() && s.length) {
        this.focus.set([...s].sort((a, b) => (a.knownBy ?? 0) - (b.knownBy ?? 0))[0]);
      }
    });
  }

  add(): void {
    const name = this.newName.trim();
    this.addError.set('');
    if (!name) return;
    if (this.skills().some((s) => s.name.toLowerCase() === name.toLowerCase())) {
      // Unique name is enforced server-side too (Skill.name has a unique constraint).
      this.addError.set('A skill with that name already exists.');
      return;
    }
    this.api.create({ name, category: this.newCategory, color: this.newColor }).subscribe(() => {
      this.newName = '';
      this.reload();
    });
  }

  remove(s: Skill): void {
    if (!confirm(`Delete ${s.name}? Removes ${(s.knownBy ?? 0) + (s.wantedBy ?? 0)} relations. (mock)`)) return;
    this.api.remove(s.id).subscribe(() => {
      if (this.focus()?.id === s.id) this.focus.set(null);
      this.reload();
    });
  }

  projectsUsing(s: Skill): string[] {
    return this.projects()
      .filter((p) => p.skills.some((x) => x.id === s.id || x.name === s.name))
      .map((p) => p.name);
  }
}
