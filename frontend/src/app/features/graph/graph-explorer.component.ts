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
  template: `
    <div class="grid">
      <!-- Left filters -->
      <div class="side left">
        <span class="col-head">Node types</span>
        <div class="types">
          @for (t of nodeTypes; track t.kind) {
            <label class="type" [class.off]="!visible()[t.kind]">
              <input type="checkbox" [checked]="visible()[t.kind]" (change)="toggle(t.kind)" />
              <span class="dot" [style.background]="t.color"></span>{{ t.label }}
              <span class="count dim">{{ t.count }}</span>
            </label>
          }
        </div>

        <div class="divider"></div>
        <span class="col-head">Relations</span>
        <div class="rels">
          <span class="tag tag-outline">KNOWS</span>
          <span class="tag tag-outline">WORKED_ON</span>
          <span class="tag tag-mute">USES</span>
          <span class="tag tag-mute">MENTORS</span>
        </div>

        <div class="divider"></div>
        <div class="group">
          <label class="label">Team</label>
          <select class="field">
            <option>All teams</option>
            <option>Backend</option>
          </select>
        </div>
        <div class="group">
          <label class="label">Depth · {{ depth() }} hops</label>
          <input type="range" min="1" max="4" [value]="depth()" (input)="depth.set(+$any($event.target).value)" />
        </div>
        <div class="group">
          <label class="label">Node limit · {{ limit() }}</label>
          <input type="range" min="50" max="500" step="50" [value]="limit()" (input)="limit.set(+$any($event.target).value)" />
          <span class="dim tiny">Server caps the subgraph — the whole graph is never sent.</span>
        </div>
      </div>

      <!-- Canvas -->
      <div class="canvas">
        @if (graph()) {
          <svg viewBox="0 0 820 880" (mouseleave)="hovered.set(null)">
            <g stroke="#3f424d" stroke-width="1">
              @for (e of edgeSegments(); track $index) {
                <line [attr.x1]="e.x1" [attr.y1]="e.y1" [attr.x2]="e.x2" [attr.y2]="e.y2" />
              }
            </g>
            @for (s of litSegments(); track $index) {
              <line [attr.x1]="s.x1" [attr.y1]="s.y1" [attr.x2]="s.x2" [attr.y2]="s.y2" stroke="#9184d9" stroke-width="2.5" stroke-linecap="round" />
            }
            @for (n of shownNodes(); track n.id) {
              @if (lit().has(n.id)) {
                <circle [attr.cx]="n.x" [attr.cy]="n.y" [attr.r]="n.r + 4" fill="none" stroke="#d2cefd" stroke-width="1.5" />
              }
              <circle
                [attr.cx]="n.x"
                [attr.cy]="n.y"
                [attr.r]="n.r"
                [attr.fill]="fill(n)"
                class="node"
                [class.sel]="selected()?.id === n.id"
                (mouseenter)="hovered.set(n.id)"
                (click)="selected.set(n)"
              />
              <text [attr.x]="n.x" [attr.y]="n.kind === 'PERSON' ? n.y + 5 : n.y - n.r - 8" text-anchor="middle" font-size="13" [attr.fill]="n.kind === 'PERSON' ? '#161826' : '#cfd3e5'" class="lbl">{{ n.label }}</text>
            }
          </svg>

          @if (hoveredNode(); as h) {
            <div class="tip" [style.left.%]="tipLeft()" [style.top.%]="tipTop()">
              <span class="eyebrow">{{ h.kind }}</span>
              <span class="tipname">{{ h.label }}</span>
              <span class="muted small">{{ h.meta }}</span>
              <span class="tippath dim small">{{ h.path }}</span>
            </div>
          }

          <div class="hud tl">
            <span class="dim small">Search in graph</span>
            <span class="bar"></span>
            <span class="small">{{ graph()!.rootLabel }}</span>
          </div>
          <div class="hud br">
            <button class="zoom">+</button>
            <button class="zoom">−</button>
            <button class="zoom wide">Fit</button>
          </div>
          <div class="status dim small">{{ graph()!.totalNodes }} nodes · {{ graph()!.totalRelations }} relations · {{ depth() }} hops from {{ graph()!.rootLabel }}</div>
        }
      </div>

      <!-- Selected node panel -->
      <div class="side right">
        @if (selected(); as s) {
          <span class="eyebrow">Selected node</span>
          <div class="selhead">
            <span class="chip">{{ s.label }}</span>
            <div class="selid">
              <span class="selname">{{ selectedTitle(s) }}</span>
              <span class="dim small">{{ s.kind | lowercase }} · {{ s.meta }}</span>
            </div>
          </div>

          @if (selectedKnows().length) {
            <div class="block">
              <span class="col-head">Knows</span>
              <div class="tags">
                @for (k of selectedKnows(); track k.skill.id) {
                  <span class="tag">{{ k.skill.name }} {{ k.level }}</span>
                }
              </div>
            </div>
          }

          @if (selectedProjects().length) {
            <div class="block">
              <span class="col-head">Worked on</span>
              @for (pr of selectedProjects(); track pr.projectId) {
                <span class="muted small">{{ pr.name }} — {{ pr.role }}</span>
              }
            </div>
          }

          <div class="block">
            <span class="col-head">Path</span>
            <span class="muted small">{{ s.path }}</span>
          </div>

          <div class="divider"></div>
          <div class="selactions">
            @if (selectedPersonId(); as pid) {
              <a class="btn btn-block center" [routerLink]="['/people', pid]">Open profile</a>
            }
            <button class="btn btn-ghost btn-block">Expand neighbours</button>
          </div>
        } @else {
          <span class="dim small">Hover to inspect, click a node to select it.</span>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .grid {
        display: grid;
        grid-template-columns: 230px 1fr 330px;
        height: calc(100vh - 61px);
      }
      .side {
        display: flex;
        flex-direction: column;
        gap: 14px;
        padding: 20px 16px;
        min-width: 0;
      }
      .left {
        border-right: 1px solid var(--border);
      }
      .right {
        border-left: 1px solid var(--border);
      }
      .types {
        display: flex;
        flex-direction: column;
        gap: 9px;
        font-size: 14px;
      }
      .type {
        display: flex;
        align-items: center;
        gap: 9px;
        cursor: pointer;
      }
      .type.off {
        opacity: 0.45;
      }
      .type input {
        accent-color: var(--accent);
      }
      .dot {
        width: 11px;
        height: 11px;
        border-radius: 50%;
      }
      .count {
        margin-left: auto;
        font-size: 12px;
      }
      .rels {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .group {
        display: flex;
        flex-direction: column;
        gap: 5px;
      }
      .group input[type='range'] {
        accent-color: var(--accent);
        width: 100%;
      }
      .tiny {
        font-size: 11px;
      }
      .canvas {
        position: relative;
        min-width: 0;
        background: radial-gradient(120% 90% at 50% 40%, #1b1e2f 0%, #161826 70%);
      }
      .canvas svg {
        width: 100%;
        height: 100%;
        display: block;
      }
      .node {
        cursor: pointer;
      }
      .node.sel {
        stroke: #d2cefd;
        stroke-width: 2;
      }
      .lbl {
        pointer-events: none;
        font-family: var(--font);
      }
      .tip {
        position: absolute;
        width: 260px;
        display: flex;
        flex-direction: column;
        gap: 3px;
        padding: 12px 14px;
        border-radius: var(--radius);
        background: var(--surface);
        box-shadow: 0 0 0 1px var(--text-muted), 0 10px 28px rgba(0, 0, 0, 0.55);
        pointer-events: none;
      }
      .tipname {
        font-size: 16px;
        font-weight: 500;
      }
      .tippath {
        margin-top: 4px;
        padding-top: 6px;
        border-top: 1px solid var(--border);
      }
      .small {
        font-size: 12px;
      }
      .hud {
        position: absolute;
        display: flex;
        align-items: center;
        gap: 8px;
        padding: 7px 11px;
        border-radius: var(--radius);
        background: var(--surface);
        box-shadow: 0 0 0 1px var(--border-strong);
      }
      .hud.tl {
        left: 16px;
        top: 16px;
      }
      .hud.br {
        right: 16px;
        bottom: 16px;
        background: transparent;
        box-shadow: none;
        gap: 6px;
      }
      .bar {
        width: 1px;
        height: 14px;
        background: var(--accent);
      }
      .zoom {
        height: 32px;
        min-width: 32px;
        padding: 0 10px;
        border-radius: var(--radius);
        border: 1px solid var(--accent);
        background: transparent;
        color: var(--accent-text);
        font-size: 15px;
      }
      .zoom:hover {
        background: rgba(145, 132, 217, 0.12);
      }
      .status {
        position: absolute;
        left: 16px;
        bottom: 16px;
      }
      .selhead {
        display: flex;
        align-items: center;
        gap: 11px;
      }
      .chip {
        width: 42px;
        height: 42px;
        border-radius: var(--radius);
        border: 1px solid var(--accent);
        color: var(--accent-text);
        font-size: 14px;
        display: grid;
        place-items: center;
        flex: none;
      }
      .selid {
        display: flex;
        flex-direction: column;
        line-height: 1.3;
      }
      .selname {
        font-size: 17px;
        font-weight: 500;
      }
      .block {
        display: flex;
        flex-direction: column;
        gap: 6px;
      }
      .tags {
        display: flex;
        flex-wrap: wrap;
        gap: 6px;
      }
      .selactions {
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .center {
        text-align: center;
      }
      @media (max-width: 1040px) {
        .grid {
          grid-template-columns: 1fr;
          height: auto;
        }
        .canvas {
          height: 60vh;
        }
        .left,
        .right {
          border: none;
          border-bottom: 1px solid var(--border);
        }
      }
    `,
  ],
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
