import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FinderApi, PeopleApi, ProjectApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { Person, Project, ProjectMember } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-project-detail',
  standalone: true,
  imports: [FormsModule, RouterLink, SkeletonComponent],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
})
export class ProjectDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly api = inject(ProjectApi);
  private readonly finder = inject(FinderApi);
  private readonly peopleApi = inject(PeopleApi);
  readonly auth = inject(AuthService);

  readonly project = signal<Project | null>(null);
  readonly loading = signal(true);
  readonly coverage = signal<{ skill: string; count: number }[]>([]);
  readonly assignOpen = signal(false);
  private readonly people = signal<Person[]>([]);

  assignPersonId = '';
  assignRole = '';

  readonly coveredCount = computed(() => this.coverage().filter((c) => c.count >= 2).length);
  readonly weakest = computed(() => {
    const cov = this.coverage();
    if (!cov.length) return null;
    return cov.reduce((min, c) => (c.count < min.count ? c : min)).skill;
  });
  readonly assignable = computed(() => {
    const memberIds = new Set((this.project()?.members ?? []).map((m) => m.personId));
    return this.people().filter((p) => !memberIds.has(p.id));
  });

  constructor() {
    this.route.paramMap.subscribe((pm) => this.load(pm.get('id') ?? ''));
  }

  private load(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (p) => {
        this.project.set(p);
        this.loading.set(false);
        this.loadCoverage(p);
      },
      error: () => {
        this.project.set(null);
        this.loading.set(false);
      },
    });
  }

  // Coverage per technology reuses the finder (people who KNOW it at level ≥ 3).
  private loadCoverage(p: Project): void {
    if (!p.skills.length) {
      this.coverage.set([]);
      return;
    }
    forkJoin(p.skills.map((s) => this.finder.search(s.name))).subscribe((results) => {
      this.coverage.set(p.skills.map((s, i) => ({ skill: s.name, count: results[i].matches.length })));
    });
  }

  openAssign(): void {
    this.assignOpen.set(true);
    if (!this.people().length) this.peopleApi.list({ size: 100 }).subscribe((r) => this.people.set(r.content));
  }

  assign(p: Project): void {
    if (!this.assignPersonId || !this.assignRole.trim()) return;
    const today = new Date().toISOString().slice(0, 10);
    this.api.assignMember(p.id, this.assignPersonId, { role: this.assignRole.trim(), from: today, to: null }).subscribe(() => {
      this.assignPersonId = '';
      this.assignRole = '';
      this.assignOpen.set(false);
      this.load(p.id);
    });
  }

  remove(p: Project, m: ProjectMember): void {
    if (!confirm(`Remove ${m.name} from ${p.name}? (mock)`)) return;
    this.api.removeMember(p.id, m.personId).subscribe(() => this.load(p.id));
  }

  toggleArchive(p: Project): void {
    this.api.setActive(p.id, !p.active).subscribe((updated) => this.project.set(updated));
  }

  activeMembers(p: Project): ProjectMember[] {
    return (p.members ?? []).filter((m) => !m.left);
  }

  period(from?: string | null, to?: string | null): string {
    const fmt = (d?: string | null) => (d ? new Date(d).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : null);
    const f = fmt(from);
    return `${f ?? '—'} — ${to ? fmt(to) : 'now'}`;
  }
}
