import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'sa-forbidden',
  standalone: true,
  imports: [RouterLink],
  template: `
    <div class="wrap">
      <div class="card-flat panel">
        <span class="eyebrow eyebrow-mute">403 · wrong role</span>
        <span class="title">This page is for admins</span>
        <p class="muted">
          You're signed in as a Member. Routes are role-guarded in the client, but the API refuses
          regardless — the client is never trusted.
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
        max-width: 440px;
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
export class ForbiddenComponent {}
