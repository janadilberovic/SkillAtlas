import { Component, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { FinderApi, ProjectApi, SkillApi } from '../../core/api/api';
import { AuthService } from '../../core/auth/auth.service';
import { Project, ProjectMember, Skill } from '../../core/models/models';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';
import { SkeletonComponent } from '../../shared/components/skeleton/skeleton.component';
import { ProjectStaffingComponent } from './project-staffing.component';

/**
 * The subgraph's user space, in CSS px. `.mini` caps its rendered width at the same number, so the
 * drawing never scales UP past 1:1 — a fixed box stretched across a wide monitor would blow 12px
 * labels up to twenty-four. Below the cap it scales down gently with the column.
 */
const CANVAS_W = 620;
/** Room each side needs for a label; what is left over is the ring's diameter. */
const LABEL_ROOM = 150;
/** Degrees each side fans over. Both arcs run top to bottom, like the roster table above. */
const ARC = 62;
/** Past this the arcs stop being readable, so the rest is reported rather than drawn. */
const MAX_ROWS = 8;
const ROSTER_SIZE = 8;

interface MiniNode {
  id: string;
  label: string;
  meta: string;
  x: number;
  y: number;
  r: number;
  /** A person who left the company: still on the project, no longer someone to ask. */
  gone: boolean;
}

/** A KNOWS edge between two nodes on the ring, bundled through its centre. */
interface KnowsEdge {
  personId: string;
  skillId: string;
  level: number;
  d: string;
  weight: number;
}

interface Subgraph {
  height: number;
  width: number;
  cx: number;
  cy: number;
  radius: number;
  people: MiniNode[];
  skills: MiniNode[];
  knows: KnowsEdge[];
  hiddenPeople: number;
  hiddenSkills: number;
}

@Component({
  selector: 'sa-project-detail',
  standalone: true,
  imports: [RouterLink, SelectComponent, SkeletonComponent, ProjectStaffingComponent],
  templateUrl: './project-detail.component.html',
  styleUrl: './project-detail.component.css',
})
export class ProjectDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly api = inject(ProjectApi);
  private readonly finder = inject(FinderApi);
  private readonly skillApi = inject(SkillApi);
  readonly auth = inject(AuthService);


  readonly project = signal<Project | null>(null);
  readonly loading = signal(true);
  readonly coverage = signal<{ skill: string; count: number }[]>([]);
  readonly staffingOpen = signal(false);
  readonly catalog = signal<Skill[]>([]);
  readonly adding = signal(false);
  readonly savingSkills = signal(false);
  readonly skillError = signal('');
  readonly hovered = signal<string | null>(null);
  /** Technologies ticked for a combined expert search. Ids, because names are not unique input. */
  readonly picked = signal<ReadonlySet<string>>(new Set());
  readonly rosterPage = signal(0);

  readonly coveredCount = computed(() => this.coverage().filter((c) => c.count >= 2).length);
  /** Technologies exactly one person deep, company-wide. Null until the coverage calls land. */
  readonly atRisk = computed(() => (this.coverage().length ? this.coverage().filter((c) => c.count <= 1).length : null));
  readonly weakest = computed(() => {
    const cov = this.coverage();
    if (!cov.length) return null;
    return cov.reduce((min, c) => (c.count < min.count ? c : min)).skill;
  });

  /**
   * One row per technology, carrying the coverage the rail used to hold in a second list. `count`
   * is null until the finder answers, so the row draws without waiting for it.
   */
  readonly stackRows = computed(() => {
    const counts = new Map(this.coverage().map((c) => [c.skill, c.count]));
    return (this.project()?.skills ?? []).map((skill) => ({ skill, count: counts.get(skill.name) ?? null }));
  });

  /**
   * The finder's `?q=`. Level 3 is the bar the row the checkbox sits on is counting, so the
   * result matches the number that was on screen when it was ticked; the finder's query bar
   * shows the parsed "≥ 3" chips on arrival, so the threshold is never a secret.
   */
  readonly pickedQuery = computed(() => {
    const ids = this.picked();
    return (this.project()?.skills ?? [])
      .filter((s) => ids.has(s.id))
      .map((s) => `${s.name}>=3`)
      .join(' + ');
  });

  /**
   * The roster pages in the client, over the list `GET /projects/{id}` already returned whole.
   * Everything else on this screen needs the WHOLE roster anyway — the ring's first eight, the
   * staffing modal's exclusion list, the soft-delete note — so paging it server-side would mean
   * shipping those three the long way round for a list bounded by the size of a project team.
   * If rosters ever outgrow one response, the move is the dashboard's: first page inline,
   * `GET /projects/{id}/members?page=` for the rest.
   */
  readonly rosterRows = computed(() => {
    const all = this.project()?.members ?? [];
    const start = this.rosterPage() * ROSTER_SIZE;
    return all.slice(start, start + ROSTER_SIZE);
  });

  readonly rosterPages = computed(() => {
    const total = (this.project()?.members ?? []).length;
    return Array.from({ length: Math.max(1, Math.ceil(total / ROSTER_SIZE)) }, (_, i) => i);
  });

  readonly rosterRange = computed(() => {
    const total = (this.project()?.members ?? []).length;
    if (!total) return 'Nobody assigned';
    const start = this.rosterPage() * ROSTER_SIZE + 1;
    return `Showing ${start}–${start + this.rosterRows().length - 1} of ${total}`;
  });

  /** Only a roster that actually carries someone soft-deleted needs the rule explained. */
  readonly hasLeavers = computed(() => (this.project()?.members ?? []).some((m) => m.left));
  /** Everyone with a WORKED_ON edge already, closed periods included — the staffing modal hides them. */
  readonly memberIds = computed(() => (this.project()?.members ?? []).map((m) => m.personId));

  /** The catalog minus what the project already USES. */
  readonly addable = computed<SelectOption[]>(() => {
    const used = new Set((this.project()?.skills ?? []).map((s) => s.id));
    return this.catalog()
      .filter((s) => !used.has(s.id))
      .map((s) => ({ value: s.id, label: s.name }));
  });

  /**
   * A ring: people fan down the left arc, the technologies they could know down the right, and
   * every KNOWS edge is a chord bundled through the middle. The project is the ring itself rather
   * than a node in it — a hub in the centre is exactly what stopped a person and a technology from
   * ever touching directly, and WORKED_ON / USES are already spelled out by the roster and the
   * stack panel above.
   */
  readonly subgraph = computed<Subgraph | null>(() => {
    const p = this.project();
    if (!p) return null;

    const members = (p.members ?? []).slice(0, MAX_ROWS);
    const shown = p.skills.slice(0, MAX_ROWS);
    const shownIds = new Set(shown.map((s) => s.id));
    // Only KNOWS edges with both ends on the ring count — towards the ordering, the radii, or the
    // caption. An edge to a technology that was cut by MAX_ROWS has nowhere to land.
    const edges = members.flatMap((m, i) =>
      m.knows.filter((k) => shownIds.has(k.skillId)).map((k) => ({ row: i, ...k })),
    );

    const degree = new Map<string, number>();
    edges.forEach((e) => {
      degree.set(e.skillId, (degree.get(e.skillId) ?? 0) + 1);
      degree.set(members[e.row].personId, (degree.get(members[e.row].personId) ?? 0) + 1);
    });

    // Barycentre pass: a technology sits opposite the average position of the people who know it,
    // so its chords stay short. Ones nobody on the roster knows have no barycentre and sink to the
    // bottom of the arc — which is the honest place for them.
    const skills = [...shown].sort((a, b) => barycentre(a.id) - barycentre(b.id) || a.name.localeCompare(b.name));

    const rows = Math.max(members.length, skills.length, 1);
    const width = CANVAS_W;
    // Label room wins over the preferred radius: a ring that runs its names off the panel is worse
    // than a smaller ring.
    const radius = Math.max(70, Math.min(108 + rows * 4, (width - LABEL_ROOM * 2) / 2));
    const height = radius * 2 + 56;
    const cx = width / 2;
    const cy = height / 2;
    // 180° is the left of the ring, 0° the right; both arcs are walked from top to bottom.
    const place = (i: number, count: number, centre: number, x: number, y: number) => {
      const a = ((count === 1 ? centre : centre - ARC + (ARC * 2 * i) / (count - 1)) * Math.PI) / 180;
      return { x: x + radius * Math.cos(a), y: y + radius * Math.sin(a) };
    };

    const people = members.map((m, i) => ({
      id: m.personId,
      label: m.name,
      meta: m.role,
      ...place(members.length - 1 - i, members.length, 180, cx, cy),
      r: 7 + Math.min(degree.get(m.personId) ?? 0, 6),
      gone: m.left,
    }));
    const skillNodes = skills.map((s, i) => ({
      id: s.id,
      label: s.name,
      meta: s.category,
      ...place(i, skills.length, 0, cx, cy),
      r: 6 + Math.min(degree.get(s.id) ?? 0, 6),
      gone: false,
    }));

    const byId = new Map(skillNodes.map((s) => [s.id, s]));
    const knows: KnowsEdge[] = edges.map((e) => {
      const from = people[e.row];
      const to = byId.get(e.skillId)!;
      return {
        personId: members[e.row].personId,
        skillId: e.skillId,
        level: e.level,
        // Control point at the centre: the chords bundle instead of scattering, which is what
        // turns a dense relation into a weave rather than a hairball.
        d: `M ${round(from.x)} ${round(from.y)} Q ${round(cx)} ${round(cy)} ${round(to.x)} ${round(to.y)}`,
        weight: e.level >= 5 ? 2.8 : e.level >= 4 ? 2 : 1.4,
      };
    });

    return {
      height,
      width,
      cx,
      cy,
      radius,
      people,
      skills: skillNodes,
      knows,
      hiddenPeople: (p.members ?? []).length - members.length,
      hiddenSkills: p.skills.length - shown.length,
    };

    function barycentre(skillId: string): number {
      const rowsKnowing = edges.filter((e) => e.skillId === skillId).map((e) => e.row);
      if (!rowsKnowing.length) return Number.POSITIVE_INFINITY;
      return rowsKnowing.reduce((sum, r) => sum + r, 0) / rowsKnowing.length;
    }
  });

  /** The hovered node's own KNOWS edges — the rest recede rather than disappear. */
  readonly litKnows = computed(() => {
    const id = this.hovered();
    if (!id) return [];
    return (this.subgraph()?.knows ?? []).filter((k) => k.personId === id || k.skillId === id);
  });

  readonly litNodes = computed(() => {
    const id = this.hovered();
    if (!id) return new Set<string>();
    const lit = new Set<string>([id]);
    this.litKnows().forEach((k) => {
      lit.add(k.personId);
      lit.add(k.skillId);
    });
    return lit;
  });

  /** How much of its own stack the roster covers, said in the picture's own terms. */
  readonly knowsSummary = computed(() => {
    const g = this.subgraph();
    if (!g) return '';
    if (!g.people.length) return 'No WORKED_ON edge yet — there is nobody on the ring.';
    if (!g.knows.length) return 'Nobody on the roster knows a technology this project uses.';
    const people = new Set(g.knows.map((k) => k.personId)).size;
    return `${g.knows.length} KNOWS edges: ${people} of ${g.people.length} on the roster know part of this stack. Hover a node to isolate its own.`;
  });

  constructor() {
    this.route.paramMap.subscribe((pm) => {
      this.rosterPage.set(0);
      this.load(pm.get('id') ?? '');
    });
    this.skillApi.list().subscribe({
      next: (skills) => this.catalog.set(skills),
      error: () => this.catalog.set([]),
    });
  }

  private load(id: string): void {
    this.loading.set(true);
    this.api.get(id).subscribe({
      next: (p) => {
        this.project.set(p);
        this.clearPicks();
        // Removing the last row of the last page leaves it empty — step back rather than show a
        // pager pointing past the end.
        const last = Math.max(0, Math.ceil((p.members?.length ?? 0) / ROSTER_SIZE) - 1);
        this.rosterPage.update((n) => Math.min(n, last));
        this.loading.set(false);
        this.loadCoverage(p);
      },
      error: () => {
        this.project.set(null);
        this.loading.set(false);
      },
    });
  }

  // Coverage per technology reuses the finder (people who KNOW it at level ≥ 3).
  private loadCoverage(p: Project): void {
    if (!p.skills.length) {
      this.coverage.set([]);
      return;
    }
    forkJoin(
      p.skills.map((s) => this.finder.search([{ name: s.name, minLevel: 3 }])),
    ).subscribe((results) => {
      this.coverage.set(p.skills.map((s, i) => ({ skill: s.name, count: results[i].matches.length })));
    });
  }

  togglePick(id: string): void {
    this.picked.update((ids) => {
      const next = new Set(ids);
      next.has(id) ? next.delete(id) : next.add(id);
      return next;
    });
  }

  clearPicks(): void {
    this.picked.set(new Set());
  }

  goToRosterPage(n: number): void {
    this.rosterPage.set(n);
  }

  onStaffingClosed(assigned: boolean): void {
    this.staffingOpen.set(false);
    const id = this.project()?.id;
    if (assigned && id) this.load(id);
  }

  addSkill(skillId: string): void {
    const p = this.project();
    if (!p || !skillId) return;
    this.writeSkills(p, [...p.skills.map((s) => s.id), skillId]);
  }

  removeSkill(s: Skill): void {
    const p = this.project();
    if (!p) return;
    this.writeSkills(p, p.skills.filter((x) => x.id !== s.id).map((x) => x.id));
  }

  private writeSkills(p: Project, skillIds: string[]): void {
    this.savingSkills.set(true);
    this.skillError.set('');
    this.api.setSkills(p.id, skillIds).subscribe({
      next: (updated) => {
        this.project.set(updated);
        // Whatever was removed cannot stay in the selection.
        const live = new Set(updated.skills.map((s) => s.id));
        this.picked.update((ids) => new Set([...ids].filter((id) => live.has(id))));
        this.savingSkills.set(false);
        this.adding.set(false);
        this.loadCoverage(updated);
      },
      error: (err) => {
        this.savingSkills.set(false);
        this.skillError.set(err?.error?.error ?? 'Could not change the technologies on this project.');
      },
    });
  }

  isLit(k: KnowsEdge): boolean {
    const id = this.hovered();
    return id === k.personId || id === k.skillId;
  }

  openPerson(n: MiniNode): void {
    // A soft-deleted person has no profile to open — that read filters them out.
    if (!n.gone) this.router.navigate(['/people', n.id]);
  }

  remove(p: Project, m: ProjectMember): void {
    if (!confirm(`Remove ${m.name} from ${p.name}?`)) return;
    this.api.removeMember(p.id, m.personId).subscribe(() => this.load(p.id));
  }

  toggleArchive(p: Project): void {
    this.api.setActive(p.id, !p.active).subscribe((updated) => this.project.set(updated));
  }

  monthYear(date?: string | null): string {
    return date ? new Date(date).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : '—';
  }

  period(from?: string | null, to?: string | null): string {
    const fmt = (d?: string | null) => (d ? new Date(d).toLocaleDateString('en-US', { month: 'short', year: 'numeric' }) : null);
    const f = fmt(from);
    return `${f ?? '—'} — ${to ? fmt(to) : 'now'}`;
  }
}

function round(n: number): number {
  return Math.round(n * 10) / 10;
}
