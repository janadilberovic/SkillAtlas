import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApi } from '../../core/api/api';
import { DashboardData } from '../../core/models/models';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { MentorMatchingComponent } from '../mentoring/mentor-matching.component';

@Component({
  selector: 'sa-dashboard',
  standalone: true,
  imports: [RouterLink, SkeletonComponent, MentorMatchingComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css',
})
export class DashboardComponent {
  private readonly api = inject(DashboardApi);
  readonly data = signal<DashboardData | null>(null);
  readonly loading = signal(true);
  readonly mentorSkill = signal<string | null>(null);
  readonly mentorConfirmed = signal<string | null>(null);

  constructor() {
    this.api.overview().subscribe((d) => {
      this.data.set(d);
      this.loading.set(false);
    });
  }

  openMentor(skill: string): void {
    this.mentorConfirmed.set(null);
    this.mentorSkill.set(skill);
  }

  onMentorClosed(confirmed: boolean): void {
    const skill = this.mentorSkill();
    this.mentorSkill.set(null);
    if (confirmed && skill) this.mentorConfirmed.set(skill);
  }
}
