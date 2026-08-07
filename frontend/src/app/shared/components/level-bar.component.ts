import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-level-bar',
  standalone: true,
  template: `
    <div class="track" [style.width.px]="width">
      <div class="fill" [style.width.%]="(clamp(level) / 5) * 100"></div>
    </div>
  `,
  styles: [
    `
      .track {
        height: 5px;
        border-radius: 3px;
        background: var(--border-strong);
      }
      .fill {
        height: 5px;
        border-radius: 3px;
        background: var(--accent);
      }
    `,
  ],
})
export class LevelBarComponent {
  @Input() level = 0;
  @Input() width = 80;
  clamp(v: number): number {
    return Math.max(0, Math.min(5, v));
  }
}
