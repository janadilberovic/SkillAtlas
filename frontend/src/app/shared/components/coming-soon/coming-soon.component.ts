import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ActivatedRoute } from '@angular/router';

@Component({
  selector: 'sa-coming-soon',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './coming-soon.component.html',
  styleUrl: './coming-soon.component.css',
})
export class ComingSoonComponent {
  private readonly route = inject(ActivatedRoute);
  label = (this.route.snapshot.queryParamMap.get('screen') ?? 'Coming soon').toUpperCase();
}
