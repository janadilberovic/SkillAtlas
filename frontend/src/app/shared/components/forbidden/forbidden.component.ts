import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'sa-forbidden',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './forbidden.component.html',
  styleUrl: './forbidden.component.css',
})
export class ForbiddenComponent {}
