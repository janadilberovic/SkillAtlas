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
  ProjectApi,
  SkillApi,
  TeamApi,
} from './core/api/api';
import {
  MockAuthApi,
  MockDashboardApi,
  MockFinderApi,
  MockGraphApi,
  MockMentoringApi,
  MockPeopleApi,
  MockProjectApi,
  MockSkillApi,
  MockTeamApi,
} from './core/api/mock-api';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes, withInMemoryScrolling({ scrollPositionRestoration: 'enabled' })),
    provideHttpClient(withInterceptors([authInterceptor])),

    // --- API seam --------------------------------------------------------
    // Bind each abstract API to its mock. To go live, replace the mock class with an
    // Http* implementation here; no component changes required.
    { provide: AuthApi, useClass: MockAuthApi },
    { provide: PeopleApi, useClass: MockPeopleApi },
    { provide: SkillApi, useClass: MockSkillApi },
    { provide: ProjectApi, useClass: MockProjectApi },
    { provide: TeamApi, useClass: MockTeamApi },
    { provide: FinderApi, useClass: MockFinderApi },
    { provide: GraphApi, useClass: MockGraphApi },
    { provide: MentoringApi, useClass: MockMentoringApi },
    { provide: DashboardApi, useClass: MockDashboardApi },
  ],
};
