import { Component, Input } from '@angular/core';

@Component({
  selector: 'sa-level-bar',
  standalone: true,
  templateUrl: './level-bar.component.html',
  styleUrl: './level-bar.component.css',
})
export class LevelBarComponent {
  @Input() level = 0;
  @Input() width = 80;
  clamp(v: number): number {
    return Math.max(0, Math.min(5, v));
  }
}
