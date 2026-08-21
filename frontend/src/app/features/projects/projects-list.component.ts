import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProjectApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { Page, Project } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { ProjectCreateComponent } from './project-create.component';

@Component({
  selector: 'sa-projects-list',
  standalone: true,
  imports: [FormsModule, RouterLink, SkeletonComponent, ProjectCreateComponent],
  templateUrl: './projects-list.component.html',
  styleUrl: './projects-list.component.css',
})
export class ProjectsListComponent {
  private readonly api = inject(ProjectApi);
  private readonly size = 9;
  private searchDebounce?: ReturnType<typeof setTimeout>;

  readonly auth = inject(AuthService);
  search = '';

  readonly page = signal(0);
  readonly data = signal<Page<Project> | null>(null);
  readonly loading = signal(true);
  readonly error = signal('');
  readonly createOpen = signal(false);

  constructor() {
    this.load();
  }

  onSearch(): void {
    clearTimeout(this.searchDebounce);
    this.searchDebounce = setTimeout(() => {
      this.page.set(0);
      this.load();
    }, 250);
  }

  go(n: number): void {
    this.page.set(n);
    this.load();
  }

  onCreateClosed(created: boolean): void {
    this.createOpen.set(false);
    // A new project sorts by name into some page, so start over rather than guess which.
    if (created) this.go(0);
  }

  private load(): void {
    this.loading.set(true);
    this.error.set('');
    this.api.page({ search: this.search, page: this.page(), size: this.size }).subscribe({
      next: (res) => {
        this.data.set(res);
        this.loading.set(false);
      },
      error: () => {
        this.error.set('Could not load projects. Check the API and try again.');
        this.loading.set(false);
      },
    });
  }

  rangeLabel(): string {
    const d = this.data();
    if (!d || !d.content.length) return 'No projects';
    const start = this.page() * this.size + 1;
    return `Showing ${start}–${start + d.content.length - 1} of ${d.totalElements}`;
  }

  pageNumbers(): number[] {
    const d = this.data();
    if (!d) return [0];
    return Array.from({ length: d.totalPages }, (_, i) => i);
  }

  period(p: Project): string {
    const y = (d: string | null) => (d ? new Date(d).getFullYear() : null);
    return `${y(p.startDate) ?? '—'} — ${p.endDate ? y(p.endDate) : 'now'}`;
  }
}
