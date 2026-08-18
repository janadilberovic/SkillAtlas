import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter, withInMemoryScrolling } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import {
  AuthApi,
  DashboardApi,
  FinderApi,
  GraphApi,
  MentoringApi,
  PeopleApi,
  PeopleSkillsApi,
  ProjectApi,
  SkillApi,
  TeamApi,
} from './core/api/api';
import {
  HttpAuthApi,
  HttpDashboardApi,
  HttpFinderApi,
  HttpGraphApi,
  HttpMentoringApi,
  HttpPeopleApi,
  HttpPeopleSkillsApi,
  HttpProjectApi,
  HttpSkillApi,
  HttpTeamApi,
} from './core/api/http-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([authInterceptor])),

    // --- API seam (live) -------------------------------------------------
    // Every token now has a real backend behind it; nothing renders the
    // "waiting for the API" screen any more.
    { provide: AuthApi, useClass: HttpAuthApi },
    { provide: FinderApi, useClass: HttpFinderApi },
    { provide: GraphApi, useClass: HttpGraphApi },
    { provide: PeopleApi, useClass: HttpPeopleApi },
    { provide: PeopleSkillsApi, useClass: HttpPeopleSkillsApi },
    { provide: SkillApi, useClass: HttpSkillApi },
    { provide: ProjectApi, useClass: HttpProjectApi },
    { provide: TeamApi, useClass: HttpTeamApi },
    { provide: MentoringApi, useClass: HttpMentoringApi },
    { provide: DashboardApi, useClass: HttpDashboardApi },
  ],
};
