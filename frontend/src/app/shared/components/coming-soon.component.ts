import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'sa-coming-soon',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <div class="card-flat panel">
        <span class="eyebrow">{{ label }}</span>
        <span class="title">Second-pass screen</span>
        <p class="muted">
          This screen (Dashboard, Skills catalog, Project detail or Account) is part of the design but
          scheduled for the next implementation pass. The core flow — finder, people, profile and graph — is live.
        </p>
        <a class="btn" routerLink="/finder">Back to expert finder</a>
      </div>
    </div>
  `,
  styles: [
    `
      .wrap {
        display: grid;
        place-items: center;
        padding: 60px 22px;
      }
      .panel {
        max-width: 460px;
        display: flex;
        flex-direction: column;
        gap: 12px;
        align-items: flex-start;
      }
      .title {
        font-size: 22px;
        font-weight: 500;
      }
      .btn {
        text-decoration: none;
      }
    `,
  ],
})
export class ComingSoonComponent {
  private readonly route = inject(ActivatedRoute);
  label = (this.route.snapshot.queryParamMap.get('screen') ?? 'Coming soon').toUpperCase();
}
