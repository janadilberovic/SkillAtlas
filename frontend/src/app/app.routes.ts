import { Routes } from '@angular/router';
import { authGuard, adminGuard } from './core/auth/guards';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./features/auth/sign-in.component').then((m) => m.SignInComponent),
  },
  {
    path: '',
    loadComponent: () => import('./shared/components/app-shell/app-shell.component').then((m) => m.AppShellComponent),
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'projects' },
      {
        path: 'finder',
        loadComponent: () =>
          import('./features/finder/expert-finder.component').then((m) => m.ExpertFinderComponent),
      },
      {
        // Backend not built yet (E5.1).
        path: 'graph',
        loadComponent: () =>
          import('./shared/components/waiting-for-api/waiting-for-api.component').then((m) => m.WaitingForApiComponent),
      },
      {
        path: 'people',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/people/people-list.component').then((m) => m.PeopleListComponent),
      },
      {
        path: 'dashboard',
        canActivate: [adminGuard],
        // Backend not built yet (E6.3).
        loadComponent: () =>
          import('./shared/components/waiting-for-api/waiting-for-api.component').then((m) => m.WaitingForApiComponent),
      },
      {
        path: 'skills',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/skills/skills-catalog.component').then((m) => m.SkillsCatalogComponent),
      },
      {
        path: 'projects',
        loadComponent: () => import('./features/projects/projects-list.component').then((m) => m.ProjectsListComponent),
      },
      {
        path: 'projects/:id',
        loadComponent: () => import('./features/projects/project-detail.component').then((m) => m.ProjectDetailComponent),
      },
      {
        path: 'people/:id',
        loadComponent: () => import('./features/profile/person-profile.component').then((m) => m.PersonProfileComponent),
      },
      {
        path: 'me',
        loadComponent: () => import('./features/profile/person-profile.component').then((m) => m.PersonProfileComponent),
      },
      {
        path: 'forbidden',
        loadComponent: () => import('./shared/components/forbidden/forbidden.component').then((m) => m.ForbiddenComponent),
      },
      {
        // Screens deferred to the second pass (Dashboard, Skills, Projects, Account).
        path: 'coming-soon',
        loadComponent: () => import('./shared/components/coming-soon/coming-soon.component').then((m) => m.ComingSoonComponent),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
