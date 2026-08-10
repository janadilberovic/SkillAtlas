import { Component, computed, inject, signal } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { GraphApi } from '../../core/api/api';
import { GraphData, GraphNode, GraphNodeKind } from '../../core/models/models';
import { person } from '../../core/mock/mock-data';

interface Segment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
}

@Component({
  selector: 'sa-graph-explorer',
  standalone: true,
  imports: [FormsModule, RouterLink, LowerCasePipe],
  templateUrl: './graph-explorer.component.html',
  styleUrl: './graph-explorer.component.css',
})
export class GraphExplorerComponent {
  private readonly graphApi = inject(GraphApi);

  readonly graph = signal<GraphData | null>(null);
  readonly hovered = signal<string | null>(null);
  readonly selected = signal<GraphNode | null>(null);
  readonly depth = signal(2);
  readonly limit = signal(300);
  readonly visible = signal<Record<GraphNodeKind, boolean>>({ PERSON: true, SKILL: true, PROJECT: true, TEAM: true });

  readonly nodeTypes: { kind: GraphNodeKind; label: string; color: string; count: number }[] = [
    { kind: 'PERSON', label: 'People', color: 'var(--node-person)', count: 214 },
    { kind: 'SKILL', label: 'Skills', color: 'var(--node-skill)', count: 86 },
    { kind: 'PROJECT', label: 'Projects', color: 'var(--node-project)', count: 31 },
    { kind: 'TEAM', label: 'Teams', color: 'transparent', count: 6 },
  ];

  private byId = new Map<string, GraphNode>();

  constructor() {
    this.graphApi.explore({ hops: this.depth(), limit: this.limit() }).subscribe((g) => {
      this.graph.set(g);
      this.byId = new Map(g.nodes.map((n) => [n.id, n]));
      this.selected.set(g.nodes[0] ?? null);
    });
  }

  toggle(kind: GraphNodeKind): void {
    this.visible.update((v) => ({ ...v, [kind]: !v[kind] }));
  }

  readonly shownNodes = computed(() => (this.graph()?.nodes ?? []).filter((n) => this.visible()[n.kind]));

  // The node the pulsing rings orbit — the root person, when its type is visible.
  readonly rootNode = computed(() => {
    const g = this.graph();
    if (!g) return null;
    const root = g.nodes.find((n) => n.kind === 'PERSON') ?? g.nodes[0] ?? null;
    return root && this.visible()[root.kind] ? root : null;
  });

  readonly edgeSegments = computed<Segment[]>(() => {
    const g = this.graph();
    if (!g) return [];
    return g.edges
      .map((e) => ({ a: this.byId.get(e.source), b: this.byId.get(e.target) }))
      .filter((p) => p.a && p.b && this.visible()[p.a!.kind] && this.visible()[p.b!.kind])
      .map((p) => ({ x1: p.a!.x, y1: p.a!.y, x2: p.b!.x, y2: p.b!.y }));
  });

  readonly hoveredNode = computed(() => (this.hovered() ? this.byId.get(this.hovered()!) ?? null : null));

  // Port of the design's hover logic: light a node's edges plus the 2nd hop through its projects.
  readonly lit = computed(() => {
    const n = this.hoveredNode();
    const set = new Set<string>();
    if (!n) return set;
    set.add(n.id);
    for (const k of n.edges) {
      const m = this.byId.get(k);
      if (!m) continue;
      set.add(k);
      if (m.kind === 'PROJECT') {
        for (const k2 of m.edges) if (k2 !== n.id) set.add(k2);
      }
    }
    return set;
  });

  readonly litSegments = computed<Segment[]>(() => {
    const n = this.hoveredNode();
    if (!n) return [];
    const segs: Segment[] = [];
    for (const k of n.edges) {
      const m = this.byId.get(k);
      if (!m) continue;
      segs.push({ x1: n.x, y1: n.y, x2: m.x, y2: m.y });
      if (m.kind === 'PROJECT') {
        for (const k2 of m.edges) {
          if (k2 === n.id) continue;
          const o = this.byId.get(k2);
          if (o) segs.push({ x1: m.x, y1: m.y, x2: o.x, y2: o.y });
        }
      }
    }
    return segs;
  });

  readonly tipLeft = computed(() => {
    const n = this.hoveredNode();
    return n ? Math.min(Math.max(((n.x + 34) / 820) * 100, 2), 62) : 0;
  });
  readonly tipTop = computed(() => {
    const n = this.hoveredNode();
    return n ? Math.min(Math.max(((n.y - 40) / 880) * 100, 2), 82) : 0;
  });

  fill(n: GraphNode): string {
    switch (n.kind) {
      case 'PERSON':
        return 'var(--node-person)';
      case 'SKILL':
        return 'var(--node-skill)';
      case 'PROJECT':
        return 'var(--node-project)';
      default:
        return 'var(--node-faint)';
    }
  }

  // The graph's person node maps to the seeded person p-mila for richer panel data.
  private linkedPerson() {
    return this.selected()?.id === 'mila' ? person('p-mila') : undefined;
  }
  selectedPersonId(): string | null {
    return this.linkedPerson()?.id ?? null;
  }
  selectedTitle(n: GraphNode): string {
    const p = this.linkedPerson();
    return p ? `${p.firstName} ${p.lastName}` : n.label;
  }
  selectedKnows() {
    return this.linkedPerson()?.knows ?? [];
  }
  selectedProjects() {
    return this.linkedPerson()?.projects ?? [];
  }
}
