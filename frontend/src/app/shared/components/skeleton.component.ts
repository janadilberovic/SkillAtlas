import { Component, Input } from '@angular/core';

/** Still-layout loading rows — no spinner jump (matches the design's loading state). */
@Component({
  selector: 'sa-skeleton',
  standalone: true,
  template: `
    @for (row of rowsArray; track $index) {
      <div class="row">
        <div class="bar" style="width:150px"></div>
        <div class="bar faint" style="width:90px"></div>
        <div class="spacer"></div>
        <div class="bar faint" style="width:40px"></div>
      </div>
    }
  `,
  styles: [
    `
      :host {
        display: flex;
        flex-direction: column;
        gap: 12px;
      }
      .row {
        display: flex;
        align-items: center;
        gap: 12px;
      }
      .bar {
        height: 12px;
        border-radius: 6px;
        background: var(--surface);
        animation: pulse 1.4s ease-in-out infinite;
      }
      .bar.faint {
        background: #1f2130;
      }
      .spacer {
        flex: 1;
      }
      @keyframes pulse {
        0%,
        100% {
          opacity: 1;
        }
        50% {
          opacity: 0.55;
        }
      }
    `,
  ],
})
export class SkeletonComponent {
  @Input() rows = 3;
  get rowsArray(): number[] {
    return Array.from({ length: this.rows });
  }
}
