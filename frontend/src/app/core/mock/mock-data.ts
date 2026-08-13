/**
 * In-memory seed for the mocked data layer. Numbers/names mirror the Nocturne design doc
 * so the screens render like the mockups. When the real API lands this whole file is deleted
 * and the Mock* services are replaced by Http* services — nothing else references it.
 */
import {
  DashboardData,
  GraphData,
  KnownSkill,
  MentorCandidate,
  Person,
  Project,
  ProjectMember,
  Role,
  Skill,
  SkillCategory,
  Team,
} from '../models/models';

// --- Skills ---------------------------------------------------------------

interface SkillSeed {
  id: string;
  name: string;
  category: SkillCategory;
  color: string;
  knownBy: number;
  wantedBy: number;
}

const SKILL_SEEDS: SkillSeed[] = [
  { id: 'sk-react', name: 'React', category: 'FRAMEWORK', color: '#9184d9', knownBy: 64, wantedBy: 21 },
  { id: 'sk-neo4j', name: 'Neo4j', category: 'DATABASE', color: '#b5afe8', knownBy: 5, wantedBy: 34 },
  { id: 'sk-docker', name: 'Docker', category: 'TOOL', color: '#75798c', knownBy: 48, wantedBy: 12 },
  { id: 'sk-ts', name: 'TypeScript', category: 'LANGUAGE', color: '#9184d9', knownBy: 71, wantedBy: 9 },
  { id: 'sk-cypher', name: 'Cypher tuning', category: 'DATABASE', color: '#5c5783', knownBy: 1, wantedBy: 7 },
  { id: 'sk-k8s', name: 'Kubernetes', category: 'TOOL', color: '#75798c', knownBy: 1, wantedBy: 28 },
  { id: 'sk-go', name: 'Go', category: 'LANGUAGE', color: '#9184d9', knownBy: 22, wantedBy: 14 },
  { id: 'sk-csharp', name: 'C#', category: 'LANGUAGE', color: '#9184d9', knownBy: 30, wantedBy: 6 },
  { id: 'sk-pg', name: 'PostgreSQL', category: 'DATABASE', color: '#b5afe8', knownBy: 40, wantedBy: 11 },
  { id: 'sk-playwright', name: 'Playwright', category: 'TOOL', color: '#75798c', knownBy: 18, wantedBy: 8 },
  { id: 'sk-terraform', name: 'Terraform', category: 'TOOL', color: '#75798c', knownBy: 1, wantedBy: 15 },
  { id: 'sk-es', name: 'Elasticsearch', category: 'DATABASE', color: '#b5afe8', knownBy: 1, wantedBy: 9 },
];

export const SKILLS: Skill[] = SKILL_SEEDS.map((s) => ({
  id: s.id,
  name: s.name,
  category: s.category,
  color: s.color,
  knownBy: s.knownBy,
  wantedBy: s.wantedBy,
}));

const skillById = new Map(SKILLS.map((s) => [s.id, s]));
export function skill(id: string): Skill {
  const s = skillById.get(id);
  if (!s) throw new Error(`unknown skill ${id}`);
  return s;
}
function knows(id: string, level: number, since?: number): KnownSkill {
  return { skill: skill(id), level, since: since ?? null };
}

// --- Teams ----------------------------------------------------------------

export const TEAMS: Team[] = [
  { id: 't-backend', name: 'Backend', memberCount: 58 },
  { id: 't-frontend', name: 'Frontend', memberCount: 44 },
  { id: 't-platform', name: 'Platform', memberCount: 27 },
  { id: 't-devops', name: 'DevOps', memberCount: 19 },
  { id: 't-data', name: 'Data', memberCount: 33 },
  { id: 't-fullstack', name: 'Full-Stack', memberCount: 33 },
];

// --- People ---------------------------------------------------------------

interface PersonSeed {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  team: string;
  position: string;
  role: Role;
  active?: boolean;
  knows: KnownSkill[];
  wants?: string[];
  mentorsCount?: number;
}

const PEOPLE_SEEDS: PersonSeed[] = [
  {
    id: 'p-mila',
    email: 'admin@skillatlas.dev',
    firstName: 'Mila',
    lastName: 'Radovanović',
    team: 'Backend',
    position: 'Senior Engineer',
    role: 'ADMIN',
    knows: [knows('sk-neo4j', 5, 2022), knows('sk-docker', 4, 2023), knows('sk-react', 3, 2024), knows('sk-csharp', 3, 2021)],
    wants: ['sk-k8s'],
    mentorsCount: 1,
  },
  {
    id: 'p-petar',
    email: 'petar.j@firma.rs',
    firstName: 'Petar',
    lastName: 'Jovanović',
    team: 'Frontend',
    position: 'Tech Lead',
    role: 'MEMBER',
    knows: [knows('sk-react', 5, 2019), knows('sk-ts', 5, 2020), knows('sk-neo4j', 3, 2024)],
    mentorsCount: 3,
  },
  {
    id: 'p-sara',
    email: 'sara.ilic@firma.rs',
    firstName: 'Sara',
    lastName: 'Ilić',
    team: 'Platform',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [knows('sk-ts', 4, 2022), knows('sk-react', 3, 2024), knows('sk-docker', 2), knows('sk-pg', 3, 2025)],
    wants: ['sk-neo4j', 'sk-k8s'],
    mentorsCount: 1,
  },
  {
    id: 'p-ana',
    email: 'ana.m@firma.rs',
    firstName: 'Ana',
    lastName: 'Marić',
    team: 'Full-Stack',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [knows('sk-react', 4, 2021), knows('sk-neo4j', 3, 2024), knows('sk-ts', 4, 2021)],
  },
  {
    id: 'p-luka',
    email: 'luka.dj@firma.rs',
    firstName: 'Luka',
    lastName: 'Đurić',
    team: 'Backend',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [knows('sk-neo4j', 4, 2023), knows('sk-react', 3, 2024), knows('sk-go', 3, 2022)],
  },
  {
    id: 'p-nikola',
    email: 'nikola.p@firma.rs',
    firstName: 'Nikola',
    lastName: 'Perić',
    team: 'Backend',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [knows('sk-neo4j', 4, 2023), knows('sk-es', 3, 2024), knows('sk-go', 4, 2021)],
  },
  {
    id: 'p-milan',
    email: 'milan.k@firma.rs',
    firstName: 'Milan',
    lastName: 'Kostić',
    team: 'Data',
    position: 'Senior Engineer',
    role: 'MEMBER',
    knows: [knows('sk-neo4j', 5, 2020), knows('sk-cypher', 4, 2021), knows('sk-pg', 5, 2019)],
    mentorsCount: 4,
  },
  {
    id: 'p-jelena',
    email: 'jelena.v@firma.rs',
    firstName: 'Jelena',
    lastName: 'Vuković',
    team: 'DevOps',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [knows('sk-terraform', 4, 2022), knows('sk-docker', 5, 2020), knows('sk-k8s', 3, 2023)],
  },
  {
    id: 'p-ivan',
    email: 'ivan.t@firma.rs',
    firstName: 'Ivan',
    lastName: 'Tadić',
    team: 'Backend',
    position: 'Engineer',
    role: 'MEMBER',
    knows: [], // imported from VacaYAY, no skills mapped yet
  },
];

interface ProjectSeed {
  id: string;
  name: string;
  description: string;
  startDate: string;
  endDate: string | null;
  active: boolean;
  uses: string[];
  members: { personId: string; role: string; from: string; to: string | null; left?: boolean }[];
}

const PROJECT_SEEDS: ProjectSeed[] = [
  {
    id: 'pr-atlas',
    name: 'Atlas',
    description: 'Internal knowledge graph',
    startDate: '2025-03-01',
    endDate: null,
    active: true,
    uses: ['sk-react', 'sk-ts', 'sk-neo4j', 'sk-cypher'],
    members: [
      { personId: 'p-mila', role: 'Backend dev', from: '2025-03-01', to: null },
      { personId: 'p-sara', role: 'Frontend dev', from: '2025-06-01', to: null },
      { personId: 'p-petar', role: 'Tech lead', from: '2025-03-01', to: null },
      { personId: 'p-marko', role: 'QA', from: '2025-03-01', to: '2025-12-01', left: true },
    ],
  },
  {
    id: 'pr-vega',
    name: 'Vega',
    description: 'QA automation platform',
    startDate: '2023-01-01',
    endDate: '2024-12-01',
    active: false,
    uses: ['sk-neo4j', 'sk-docker', 'sk-playwright'],
    members: [
      { personId: 'p-mila', role: 'Tech lead', from: '2023-01-01', to: '2024-12-01' },
      { personId: 'p-sara', role: 'QA automation', from: '2024-01-01', to: '2025-01-01' },
    ],
  },
  {
    id: 'pr-orion',
    name: 'Orion',
    description: 'Infra provisioning',
    startDate: '2024-05-01',
    endDate: null,
    active: true,
    uses: ['sk-terraform', 'sk-k8s', 'sk-docker'],
    members: [{ personId: 'p-jelena', role: 'DevOps', from: '2024-05-01', to: null }],
  },
  {
    id: 'pr-insight',
    name: 'Insight',
    description: 'Analytics warehouse',
    startDate: '2023-09-01',
    endDate: null,
    active: true,
    uses: ['sk-pg', 'sk-neo4j'],
    members: [{ personId: 'p-milan', role: 'Data engineer', from: '2023-09-01', to: null }],
  },
];

// left-the-company person kept only for WORKED_ON history (soft-deleted, never in search).
const SOFT_DELETED: Record<string, { name: string }> = {
  'p-marko': { name: 'Marko Živković' },
};

function personProjects(personId: string) {
  return PROJECT_SEEDS.filter((pr) => pr.members.some((m) => m.personId === personId && !m.left)).map((pr) => {
    const m = pr.members.find((mm) => mm.personId === personId)!;
    return {
      projectId: pr.id,
      name: pr.name,
      role: m.role,
      from: m.from,
      to: m.to,
      active: pr.active,
      uses: pr.uses.map((id) => skill(id).name),
    };
  });
}

export const PEOPLE: Person[] = PEOPLE_SEEDS.map((s) => {
  const sortedKnows = [...s.knows].sort((a, b) => b.level - a.level);
  return {
    id: s.id,
    email: s.email,
    firstName: s.firstName,
    lastName: s.lastName,
    position: s.position,
    role: s.role,
    active: s.active ?? true,
    team: s.team,
    teams: [s.team],
    knows: sortedKnows,
    topSkills: sortedKnows
      .slice(0, 3)
      .map((k) => ({ skillId: k.skill.id, name: k.skill.name, level: k.level })),
    wantsToLearn: (s.wants ?? []).map((id) => skill(id)),
    projects: personProjects(s.id),
    mentorsCount: s.mentorsCount ?? 0,
    mentorships: buildMentorships(s.id),
  };
});

const peopleById = new Map(PEOPLE.map((p) => [p.id, p]));
export function person(id: string): Person | undefined {
  return peopleById.get(id);
}
export function personName(id: string): string {
  const p = peopleById.get(id);
  if (p) return `${p.firstName} ${p.lastName}`;
  return SOFT_DELETED[id]?.name ?? id;
}

// A couple of hand-placed mentorships so the profile right-rail has content.
function buildMentorships(personId: string) {
  const rels: import('../models/models').Mentorship[] = [];
  if (personId === 'p-sara') {
    rels.push({ personName: 'Mila R.', skill: 'Neo4j', since: 2025, direction: 'MENTORED_BY' });
    rels.push({ personName: 'Nikola P.', skill: 'Playwright', since: 2026, direction: 'MENTORS' });
  }
  if (personId === 'p-mila') {
    rels.push({ personName: 'Sara Ilić', skill: 'Neo4j', since: 2025, direction: 'MENTORS' });
  }
  return rels;
}

export const PROJECTS: Project[] = PROJECT_SEEDS.map((s) => ({
  id: s.id,
  name: s.name,
  description: s.description,
  startDate: s.startDate,
  endDate: s.endDate,
  active: s.active,
  skills: s.uses.map((id) => skill(id)),
  members: s.members.map((m) => ({
    personId: m.personId,
    name: personName(m.personId),
    role: m.role,
    from: m.from,
    to: m.to,
    left: m.left ?? false,
  })),
}));

// --- Login accounts (mock) ------------------------------------------------
// Password is the same for both to keep the demo simple; matches the seeded backend admin.
export const MOCK_CREDENTIALS: Record<string, string> = {
  'admin@skillatlas.dev': 'Password123!',
  'sara.ilic@firma.rs': 'Password123!',
};

// --- Graph subgraph (2b) --------------------------------------------------
// Ported 1:1 from the design doc's GRAPH_NODES so the hover-highlight behaviour matches.
export const GRAPH: GraphData = {
  rootLabel: 'Mila R.',
  hops: 2,
  totalNodes: 287,
  totalRelations: 612,
  nodes: [
    { id: 'mila', kind: 'PERSON', label: 'MR', meta: 'Backend · Senior Engineer', path: 'Vega (Tech lead) → Neo4j 5, Docker 4 · Atlas → React 3', x: 410, y: 430, r: 34, edges: ['vega', 'atlas', 'neo4j', 'docker', 'react'] },
    { id: 'neo4j', kind: 'SKILL', label: 'Neo4j', meta: 'database · known by 5', path: 'Mila KNOWS 5 · used by Vega, Atlas', x: 250, y: 270, r: 23, edges: ['mila', 'vega', 'atlas'] },
    { id: 'react', kind: 'SKILL', label: 'React', meta: 'framework · known by 64', path: 'Mila KNOWS 3 · used by Atlas', x: 590, y: 290, r: 23, edges: ['mila', 'atlas'] },
    { id: 'docker', kind: 'SKILL', label: 'Docker', meta: 'tool · known by 48', path: 'Mila KNOWS 4 since 2023 · used by Vega', x: 410, y: 200, r: 21, edges: ['mila', 'vega'] },
    { id: 'atlas', kind: 'PROJECT', label: 'Atlas', meta: 'active · 9 people', path: 'Mila as Backend dev, 2025 — now · uses Neo4j, React', x: 600, y: 590, r: 22, edges: ['mila', 'neo4j', 'react'] },
    { id: 'vega', kind: 'PROJECT', label: 'Vega', meta: 'archived · 6 people', path: 'Mila as Tech lead, 2023 — 2024 · uses Neo4j, Docker', x: 230, y: 600, r: 22, edges: ['mila', 'neo4j', 'docker'] },
  ],
  edges: [
    { source: 'mila', target: 'neo4j', type: 'KNOWS' },
    { source: 'mila', target: 'docker', type: 'KNOWS' },
    { source: 'mila', target: 'react', type: 'KNOWS' },
    { source: 'mila', target: 'atlas', type: 'WORKED_ON' },
    { source: 'mila', target: 'vega', type: 'WORKED_ON' },
    { source: 'atlas', target: 'neo4j', type: 'USES' },
    { source: 'atlas', target: 'react', type: 'USES' },
    { source: 'vega', target: 'neo4j', type: 'USES' },
    { source: 'vega', target: 'docker', type: 'USES' },
  ],
};

// --- Dashboard (2d) -------------------------------------------------------
export const DASHBOARD: DashboardData = {
  stats: [
    { label: 'Active people', value: '214', hint: '+18 imported this week' },
    { label: 'Skills in catalog', value: '86', hint: '1 340 KNOWS relations' },
    { label: 'Projects', value: '31', hint: '24 active · 7 archived' },
    { label: 'Mentorships', value: '27', hint: '4 waiting for confirmation', hintAccent: true },
  ],
  skillGap: [
    { team: 'Backend', skill: 'Cypher tuning', projects: ['Atlas', 'Vega'], knows: 1 },
    { team: 'Frontend', skill: 'Accessibility', projects: ['Portal'], knows: 0 },
    { team: 'DevOps', skill: 'Kubernetes', projects: ['Atlas', 'Orion'], knows: 1 },
    { team: 'Platform', skill: 'Terraform', projects: ['Orion'], knows: 1 },
    { team: 'Data', skill: 'dbt', projects: ['Insight'], knows: 0 },
  ],
  busFactor: [
    { skill: 'Cypher tuning', person: 'Milan Kostić' },
    { skill: 'Terraform', person: 'Jelena Vuković' },
    { skill: 'Elasticsearch', person: 'Nikola Perić' },
  ],
  mappingQueue: { total: 18, names: ['Ivan T.', 'Maja S.', 'Dino B.', 'Lea K.'] },
};

// --- Mentor candidates (2g) ----------------------------------------------
// Ranks people who KNOW the skill at level ≥ 3, excluding the mentee and self.
// Score rewards higher level, penalises current mentoring load (fewer active ranks up).
export function mentorCandidatesFor(menteeId: string, skillName: string): MentorCandidate[] {
  return PEOPLE.filter((p) => p.active && p.id !== menteeId)
    .map((p) => {
      const k = (p.knows ?? []).find((x) => x.skill.name.toLowerCase() === skillName.toLowerCase());
      if (!k || k.level < 3) return null;
      const load = p.mentorsCount ?? 0;
      const score = Math.round((k.level * 2 - load * 0.8) * 10) / 10;
      return { person: p, skill: skillName, level: k.level, activeMentorships: load, score };
    })
    .filter((c): c is MentorCandidate => c !== null)
    .sort((a, b) => b.score - a.score);
}

// --- Mock mutations (skills + project staffing) --------------------------
let skillSeq = 100;
export function addSkillMock(input: { name: string; category: SkillCategory; color: string }): Skill {
  const s: Skill = { id: `sk-new-${skillSeq++}`, name: input.name, category: input.category, color: input.color, knownBy: 0, wantedBy: 0 };
  SKILLS.unshift(s);
  skillById.set(s.id, s);
  return s;
}
export function removeSkillMock(id: string): void {
  const i = SKILLS.findIndex((s) => s.id === id);
  if (i >= 0) SKILLS.splice(i, 1);
  skillById.delete(id);
}

export function assignMemberMock(projectId: string, personId: string, input: { role: string; from?: string | null; to?: string | null }): void {
  const pr = PROJECTS.find((p) => p.id === projectId);
  if (!pr) return;
  const members: ProjectMember[] = pr.members ?? (pr.members = []);
  if (members.some((m) => m.personId === personId)) return;
  members.push({ personId, name: personName(personId), role: input.role, from: input.from ?? null, to: input.to ?? null, left: false });
}
export function removeMemberMock(projectId: string, personId: string): void {
  const pr = PROJECTS.find((p) => p.id === projectId);
  if (pr?.members) pr.members = pr.members.filter((m) => m.personId !== personId);
}
export function setProjectActiveMock(projectId: string, active: boolean): Project | undefined {
  const pr = PROJECTS.find((p) => p.id === projectId);
  if (pr) pr.active = active;
  return pr;
}
