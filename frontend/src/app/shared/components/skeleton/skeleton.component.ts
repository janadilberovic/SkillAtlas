import { Component, Input } from '@angular/core';

/** Still-layout loading rows — no spinner jump (matches the design's loading state). */
@Component({
  selector: 'sa-skeleton',
  standalone: true,
  templateUrl: './skeleton.component.html',
  styleUrl: './skeleton.component.css',
})
export class SkeletonComponent {
  @Input() rows = 3;
  get rowsArray(): number[] {
    return Array.from({ length: this.rows });
  }
}
