import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ProjectApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { Project } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-projects-list',
  standalone: true,
  imports: [RouterLink, SkeletonComponent],
  templateUrl: './projects-list.component.html',
  styleUrl: './projects-list.component.css',
})
export class ProjectsListComponent {
  private readonly api = inject(ProjectApi);
  readonly auth = inject(AuthService);
  readonly projects = signal<Project[]>([]);
  readonly loading = signal(true);

  constructor() {
    this.api.list().subscribe((p) => {
      this.projects.set(p);
      this.loading.set(false);
    });
  }

  period(p: Project): string {
    const y = (d: string | null) => (d ? new Date(d).getFullYear() : null);
    return `${y(p.startDate) ?? '—'} — ${p.endDate ? y(p.endDate) : 'now'}`;
  }
}
