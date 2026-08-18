import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../core/api/api';
import { DashboardData } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';

@Component({
  selector: 'sa-dashboard',
  standalone: true,
  imports: [RouterLink, SkeletonComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApi);
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(true);
  readonly failed = signal(false);

  // The server sends counts; the hint under each one is the reading of them this screen is for.
  readonly tiles = computed(() => {
    const d = this.data();
    if (!d) return [];
    return [
      { label: 'Active people', value: d.metrics.people, hint: `${d.mappingQueue.total} with no skills mapped`, accent: d.mappingQueue.total > 0 },
      { label: 'Skills in catalog', value: d.metrics.skills, hint: `${d.busFactor.length} hang on one person`, accent: d.busFactor.length > 0 },
      { label: 'Projects', value: d.metrics.projects, hint: `${d.skillGap.length} team/skill gaps`, accent: d.skillGap.length > 0 },
      { label: 'Mentorships', value: d.metrics.mentorships, hint: 'Confirmed by an admin', accent: false },
    ];
  });

  constructor() {
    this.api.overview().subscribe({
      next: (d) => {
        this.data.set(d);
        this.loading.set(false);
      },
      error: () => {
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }
}
