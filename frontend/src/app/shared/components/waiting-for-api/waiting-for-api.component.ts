import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';

// Placeholder for features whose backend isn't wired yet (Finder, Graph, Dashboard).
// Shows "waiting for real data from the API" instead of mock data.
@Component({
  selector: 'sa-waiting-for-api',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <div class="card-flat panel">
        <span class="eyebrow">{{ label }}</span>
        <span class="title">Waiting for the API</span>
        <p class="muted">
          This screen is designed and ready, but its backend endpoint isn't built yet. It will show real
          data as soon as the API is available — no mock data is used here.
        </p>
        <a class="btn" routerLink="/projects">Back to projects</a>
      </div>
    </div>
  `,
  styles: [
    `
      .wrap {
        display: grid;
        place-items: center;
        min-height: 60vh;
        padding: 2rem;
      }
      .panel {
        max-width: 34rem;
        display: flex;
        flex-direction: column;
        gap: 0.75rem;
        align-items: flex-start;
        padding: 1.75rem;
      }
      .title {
        font-size: 1.35rem;
        font-weight: 600;
      }
    `,
  ],
})
export class WaitingForApiComponent {
  private readonly route = inject(ActivatedRoute);
  label = (this.route.snapshot.queryParamMap.get('screen') ?? 'Not yet available').toUpperCase();
}
