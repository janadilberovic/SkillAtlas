import { Component, ElementRef, computed, inject, signal, viewChild } from '@angular/core';
import { LowerCasePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  SimulationLinkDatum,
  SimulationNodeDatum,
  forceCenter,
  forceCollide,
  forceLink,
  forceManyBody,
  forceSimulation,
  forceX,
  forceY,
} from 'd3-force';
import { GraphApi, GraphQuery, TeamApi } from '../../core/api/api';
import { GraphData, GraphEdgeType, GraphNode, GraphNodeKind } from '../../core/models/models';
import { SelectComponent, SelectOption } from '../../shared/components/select/select.component';

/** The user-space box the layout is solved in; the SVG scales it to whatever the pane is. */
const WIDTH = 1000;
const HEIGHT = 760;
/** Spacing of the tray that holds nodes with no edge in the current window. */
const TRAY_STEP = 46;
const TRAY_GAP = 40;

interface PlacedNode extends GraphNode, SimulationNodeDatum {
  x: number;
  y: number;
  r: number;
  degree: number;
}

interface Segment {
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  type: GraphEdgeType;
}

const KIND_LABEL: Record<GraphNodeKind, string> = {
  PERSON: 'People',
  SKILL: 'Skills',
  PROJECT: 'Projects',
  TEAM: 'Teams',
};

const KIND_COLOR: Record<GraphNodeKind, string> = {
  PERSON: 'var(--node-person)',
  SKILL: 'var(--node-skill)',
  PROJECT: 'var(--node-project)',
  TEAM: 'var(--node-team)',
};

const ALL_KINDS: GraphNodeKind[] = ['PERSON', 'SKILL', 'PROJECT', 'TEAM'];

@Component({
  selector: 'sa-graph-explorer',
  standalone: true,
  imports: [RouterLink, LowerCasePipe, SelectComponent],
  templateUrl: './graph-explorer.component.html',
  styleUrl: './graph-explorer.component.css',
})
export class GraphExplorerComponent {
  private readonly graphApi = inject(GraphApi);
  private readonly teamApi = inject(TeamApi);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  private readonly svg = viewChild<ElementRef<SVGSVGElement>>('canvas');

  readonly graph = signal<GraphData | null>(null);
  /** Kept outside `graph` so the legend still describes the company when nothing is drawn. */
  readonly totals = signal<Record<GraphNodeKind, number>>({
    PERSON: 0,
    SKILL: 0,
    PROJECT: 0,
    TEAM: 0,
  });
  readonly loading = signal(true);
  readonly failed = signal(false);
  readonly nodes = signal<PlacedNode[]>([]);
  readonly hovered = signal<string | null>(null);
  readonly selected = signal<PlacedNode | null>(null);

  readonly teams = signal<SelectOption[]>([]);
  readonly team = signal('');
  readonly limit = signal(150);
  /** One hop on arrival: the root's own edges are the answer to "show me this person". */
  readonly hops = signal(1);
  /** Set from `?rootId=` — the profile's "In graph" jump lands here. */
  readonly rootId = signal<string | null>(null);
  readonly visible = signal<Record<GraphNodeKind, boolean>>({
    PERSON: true,
    SKILL: true,
    PROJECT: true,
    TEAM: true,
  });

  /** Y of the divider above the unconnected tray; null when every node has an edge. */
  readonly trayTop = signal<number | null>(null);
  readonly looseCount = signal(0);

  readonly zoom = signal(1);
  readonly panX = signal(0);
  readonly panY = signal(0);
  private drag: { pointer: number; x: number; y: number; panX: number; panY: number } | null = null;
  /** A pan ends in a click event; without this the drag would also clear the pinned node. */
  private dragMoved = false;

  readonly width = WIDTH;
  readonly height = HEIGHT;

  constructor() {
    this.teamApi
      .list()
      .subscribe((teams) => this.teams.set(teams.map((t) => ({ value: t.name, label: t.name }))));
    this.route.queryParamMap.subscribe((params) => {
      this.rootId.set(params.get('rootId'));
      this.load();
    });
  }

  // --- data ---------------------------------------------------------------

  load(): void {
    const kinds = this.activeKinds();
    this.loading.set(true);
    this.failed.set(false);
    // No `types` on the wire means "all kinds" to the server, so an empty selection must never
    // become a request — every box off is an empty canvas, drawn without asking.
    if (!kinds.length) {
      this.graph.set(null);
      this.nodes.set([]);
      this.selected.set(null);
      this.loading.set(false);
      return;
    }
    const query: GraphQuery = {
      limit: this.limit(),
      types: kinds,
      team: this.team() || undefined,
      rootId: this.rootId() ?? undefined,
      hops: this.hops(),
    };
    this.graphApi.explore(query).subscribe({
      next: (data) => {
        this.graph.set(data);
        if (data.totals) this.totals.set(data.totals);
        this.nodes.set(this.layout(data));
        // The root survives the reload as the inspected node — focusing a person must not be the
        // gesture that empties the panel about them.
        const root = this.nodes().find((n) => n.id === this.rootId()) ?? null;
        this.selected.set(root);
        if (root) this.centerOn(root);
        else this.fit();
        this.loading.set(false);
      },
      error: () => {
        this.graph.set(null);
        this.nodes.set([]);
        this.failed.set(true);
        this.loading.set(false);
      },
    });
  }

  toggle(kind: GraphNodeKind): void {
    this.visible.update((v) => ({ ...v, [kind]: !v[kind] }));
    this.load();
  }

  onTeamChange(value: string): void {
    this.team.set(value);
    this.load();
  }

  onLimitChange(value: number): void {
    this.limit.set(value);
    this.load();
  }

  onHopsChange(value: number): void {
    this.hops.set(value);
    this.load();
  }

  focusOn(node: PlacedNode): void {
    this.router.navigate([], { queryParams: { rootId: node.id } });
  }

  clearFocus(): void {
    this.router.navigate([], { queryParams: {} });
  }

  private activeKinds(): GraphNodeKind[] {
    return ALL_KINDS.filter((k) => this.visible()[k]);
  }

  // --- layout -------------------------------------------------------------

  /**
   * d3-force, solved synchronously rather than animated: ticking on requestAnimationFrame would
   * re-run change detection every frame to arrive at the same picture.
   */
  private layout(data: GraphData): PlacedNode[] {
    const degree = new Map<string, number>();
    for (const e of data.edges) {
      degree.set(e.source, (degree.get(e.source) ?? 0) + 1);
      degree.set(e.target, (degree.get(e.target) ?? 0) + 1);
    }
    const nodes: PlacedNode[] = data.nodes.map((n) => ({
      ...n,
      degree: degree.get(n.id) ?? 0,
      r: radius(n.kind, degree.get(n.id) ?? 0),
      x: 0,
      y: 0,
    }));

    // Nodes with no edge *in this window* carry no structure, and leaving them in the simulation
    // is what produced the orbiting ring: repulsion pushes them off the cluster, the positional
    // force holds them at a radius, and the result eats the canvas the real graph needed. They
    // get a tidy tray instead.
    const linked = nodes.filter((n) => n.degree > 0);
    const loose = nodes.filter((n) => n.degree === 0);
    const trayHeight = loose.length ? trayRows(loose.length) * TRAY_STEP + TRAY_GAP : 0;
    const fieldHeight = HEIGHT - trayHeight;

    const byId = new Map(linked.map((n) => [n.id, n]));
    const links: SimulationLinkDatum<PlacedNode>[] = data.edges
      .filter((e) => byId.has(e.source) && byId.has(e.target))
      .map((e) => ({ source: e.source, target: e.target }));

    // Fixed, not merely pulled: a rooted view is only readable if the root sits in the same place
    // every time. forceCenter would then fight it every tick, so the positional forces hold the
    // rest instead.
    const root = byId.get(this.rootId() ?? '') ?? null;
    if (root) {
      root.fx = WIDTH / 2;
      root.fy = fieldHeight / 2;
    }

    const sim = forceSimulation(linked)
      .force(
        'link',
        forceLink<PlacedNode, SimulationLinkDatum<PlacedNode>>(links)
          .id((n) => n.id)
          .distance(110)
          .strength(0.25),
      )
      // distanceMax bounds the repulsion; without it the disconnected components sail apart,
      // since forceCenter only recentres the mean.
      .force('charge', forceManyBody().strength(-320).distanceMax(600))
      .force('x', forceX<PlacedNode>(WIDTH / 2).strength(root ? 0.08 : 0.04))
      .force('y', forceY<PlacedNode>(fieldHeight / 2).strength(root ? 0.08 : 0.04))
      // Extra iterations because one pass per tick leaves the dense middle overlapping.
      .force(
        'collide',
        forceCollide<PlacedNode>()
          .radius((n) => n.r + 12)
          .strength(1)
          .iterations(3),
      )
      .stop();
    if (!root) sim.force('center', forceCenter(WIDTH / 2, fieldHeight / 2));

    const ticks = Math.ceil(Math.log(sim.alphaMin()) / Math.log(1 - sim.alphaDecay()));
    for (let i = 0; i < ticks; i++) sim.tick();

    // The tray is placed outside the simulation, so nothing stops a drifting node from landing on
    // top of it. Keep the field in its own band.
    if (loose.length) {
      for (const n of linked) {
        n.y = Math.min(n.y, fieldHeight - n.r - 8);
      }
    }

    const perRow = Math.max(1, Math.floor(WIDTH / TRAY_STEP));
    loose.forEach((n, i) => {
      n.x = TRAY_STEP / 2 + (i % perRow) * TRAY_STEP;
      n.y = fieldHeight + TRAY_GAP + Math.floor(i / perRow) * TRAY_STEP;
    });
    this.trayTop.set(loose.length ? fieldHeight + TRAY_GAP / 2 : null);
    this.looseCount.set(loose.length);
    return nodes;
  }

  private byId(): Map<string, PlacedNode> {
    return new Map(this.nodes().map((n) => [n.id, n]));
  }

  readonly segments = computed<Segment[]>(() => {
    const index = this.byId();
    return (this.graph()?.edges ?? [])
      .map((e) => ({ a: index.get(e.source), b: index.get(e.target), type: e.type }))
      .filter((p): p is { a: PlacedNode; b: PlacedNode; type: GraphEdgeType } => !!p.a && !!p.b)
      .map((p) => ({ x1: p.a.x, y1: p.a.y, x2: p.b.x, y2: p.b.y, type: p.type }));
  });

  readonly hoveredNode = computed(() => {
    const id = this.hovered();
    return id ? (this.byId().get(id) ?? null) : null;
  });

  /** The panel follows the cursor when nothing is pinned, so reading the map needs no clicking. */
  readonly inspected = computed(() => this.selected() ?? this.hoveredNode());

  onNodeEnter(node: PlacedNode): void {
    this.hovered.set(node.id);
  }

  clearHover(): void {
    this.hovered.set(null);
  }

  /** A pin holds until another node is clicked; hover never takes it away. */
  select(node: PlacedNode, event: MouseEvent): void {
    event.stopPropagation();
    this.selected.set(node);
  }

  /** A click on empty canvas clears the pin, unless it was the end of a pan. */
  onCanvasClick(): void {
    if (this.dragMoved) {
      this.dragMoved = false;
      return;
    }
    this.selected.set(null);
  }

  /**
   * The inspected node and everything one edge away — what the rest of the map dims behind.
   *
   * <p>A pin wins over the cursor, so the focus survives moving the mouse across the canvas; a
   * click on the background is the way back to the undimmed map.
   */
  readonly lit = computed(() => {
    const node = this.inspected();
    const set = new Set<string>();
    if (!node) return set;
    set.add(node.id);
    for (const e of this.graph()?.edges ?? []) {
      if (e.source === node.id) set.add(e.target);
      if (e.target === node.id) set.add(e.source);
    }
    return set;
  });

  /** In a rooted view the second ring is context, not clutter, so it only recedes a little. */
  readonly softDim = computed(() => !!this.rootId() && this.selected()?.id === this.rootId());

  readonly litSegments = computed<Segment[]>(() => {
    const node = this.inspected();
    if (!node) return [];
    const index = this.byId();
    return (this.graph()?.edges ?? [])
      .filter((e) => e.source === node.id || e.target === node.id)
      .map((e) => ({ a: index.get(e.source), b: index.get(e.target), type: e.type }))
      .filter((p): p is { a: PlacedNode; b: PlacedNode; type: GraphEdgeType } => !!p.a && !!p.b)
      .map((p) => ({ x1: p.a.x, y1: p.a.y, x2: p.b.x, y2: p.b.y, type: p.type }));
  });

  /** Labels everywhere is noise past a few dozen nodes, so past that they follow attention. */
  readonly labelAll = computed(() => this.nodes().length <= 60);

  /**
   * The best-connected nodes stay labelled even in a crowded view — an unlabelled blob is not a
   * map, and these are the ones a reader orients by.
   */
  private readonly anchors = computed(() => {
    if (this.labelAll()) return new Set<string>();
    return new Set(
      [...this.nodes()]
        .sort((a, b) => b.degree - a.degree)
        .slice(0, 12)
        .map((n) => n.id),
    );
  });

  showLabel(n: PlacedNode): boolean {
    return (
      this.labelAll() ||
      this.anchors().has(n.id) ||
      n.id === this.rootId() ||
      n.id === this.hovered() ||
      n.id === this.selected()?.id ||
      this.lit().has(n.id)
    );
  }

  // --- selection panel ----------------------------------------------------

  readonly connections = computed<{ type: GraphEdgeType; nodes: PlacedNode[] }[]>(() => {
    const node = this.inspected();
    if (!node) return [];
    const index = this.byId();
    const grouped = new Map<GraphEdgeType, PlacedNode[]>();
    for (const e of this.graph()?.edges ?? []) {
      const otherId = e.source === node.id ? e.target : e.target === node.id ? e.source : null;
      if (!otherId) continue;
      const other = index.get(otherId);
      if (!other) continue;
      const list = grouped.get(e.type) ?? [];
      list.push(other);
      grouped.set(e.type, list);
    }
    return [...grouped.entries()].map(([type, nodes]) => ({ type, nodes }));
  });

  readonly typeRows = computed(() =>
    ALL_KINDS.map((kind) => ({
      kind,
      label: KIND_LABEL[kind],
      color: KIND_COLOR[kind],
      total: this.totals()[kind],
    })),
  );

  readonly shownCount = computed(() => this.graph()?.edges.length ?? 0);

  fill(n: PlacedNode): string {
    return KIND_COLOR[n.kind];
  }

  // --- zoom / pan ---------------------------------------------------------

  readonly transform = computed(
    () => `translate(${this.panX()},${this.panY()}) scale(${this.zoom()})`,
  );

  onWheel(event: WheelEvent): void {
    event.preventDefault();
    const point = this.toUserSpace(event);
    if (!point) return;
    const from = this.zoom();
    const to = clamp(from * (event.deltaY < 0 ? 1.15 : 1 / 1.15), 0.25, 4);
    // Keep whatever is under the cursor under the cursor.
    this.panX.set(point.x - (point.x - this.panX()) * (to / from));
    this.panY.set(point.y - (point.y - this.panY()) * (to / from));
    this.zoom.set(to);
  }

  onPointerDown(event: PointerEvent): void {
    const point = this.toUserSpace(event);
    if (!point) return;
    this.drag = {
      pointer: event.pointerId,
      x: point.x,
      y: point.y,
      panX: this.panX(),
      panY: this.panY(),
    };
    (event.target as Element).setPointerCapture?.(event.pointerId);
  }

  onPointerMove(event: PointerEvent): void {
    if (!this.drag || this.drag.pointer !== event.pointerId) return;
    const point = this.toUserSpace(event);
    if (!point) return;
    const dx = point.x - this.drag.x;
    const dy = point.y - this.drag.y;
    if (Math.abs(dx) > 2 || Math.abs(dy) > 2) this.dragMoved = true;
    this.panX.set(this.drag.panX + dx);
    this.panY.set(this.drag.panY + dy);
  }

  onPointerUp(event: PointerEvent): void {
    if (this.drag?.pointer === event.pointerId) this.drag = null;
  }

  zoomBy(factor: number): void {
    const from = this.zoom();
    const to = clamp(from * factor, 0.25, 4);
    const cx = WIDTH / 2;
    const cy = HEIGHT / 2;
    this.panX.set(cx - (cx - this.panX()) * (to / from));
    this.panY.set(cy - (cy - this.panY()) * (to / from));
    this.zoom.set(to);
  }

  /** The subgraph's scale, but the given node holds the middle of the canvas. */
  private centerOn(node: PlacedNode): void {
    this.fit();
    const scale = this.zoom();
    this.panX.set(WIDTH / 2 - node.x * scale);
    this.panY.set(HEIGHT / 2 - node.y * scale);
  }

  /** Frame the whole subgraph — the reset the zoom buttons drift away from. */
  fit(): void {
    const nodes = this.nodes();
    if (!nodes.length) {
      this.zoom.set(1);
      this.panX.set(0);
      this.panY.set(0);
      return;
    }
    const minX = Math.min(...nodes.map((n) => n.x - n.r));
    const maxX = Math.max(...nodes.map((n) => n.x + n.r));
    const minY = Math.min(...nodes.map((n) => n.y - n.r));
    const maxY = Math.max(...nodes.map((n) => n.y + n.r));
    const pad = 40;
    const scale = clamp(
      Math.min(WIDTH / (maxX - minX + pad), HEIGHT / (maxY - minY + pad)),
      0.25,
      2,
    );
    this.zoom.set(scale);
    this.panX.set(WIDTH / 2 - ((minX + maxX) / 2) * scale);
    this.panY.set(HEIGHT / 2 - ((minY + maxY) / 2) * scale);
  }

  // getScreenCTM rather than the bounding rect: it already accounts for preserveAspectRatio's
  // letterboxing, which a naive width ratio gets wrong on any non-matching aspect.
  private toUserSpace(event: MouseEvent | PointerEvent): { x: number; y: number } | null {
    const svg = this.svg()?.nativeElement;
    const ctm = svg?.getScreenCTM();
    if (!svg || !ctm) return null;
    const point = new DOMPoint(event.clientX, event.clientY).matrixTransform(ctm.inverse());
    return { x: point.x, y: point.y };
  }
}

function trayRows(count: number): number {
  return Math.ceil(count / Math.max(1, Math.floor(WIDTH / TRAY_STEP)));
}

function radius(kind: GraphNodeKind, degree: number): number {
  const base = kind === 'PERSON' ? 15 : kind === 'PROJECT' ? 13 : kind === 'TEAM' ? 12 : 11;
  return base + Math.min(9, Math.sqrt(degree) * 2.2);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max);
}
