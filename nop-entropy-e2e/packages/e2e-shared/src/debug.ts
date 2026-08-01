/**
 * E2E debugging utilities — engine-agnostic helpers for diagnosing test failures.
 *
 * These functions are intentionally side-effect-free (read-only) and return
 * structured data. They can be called from any spec file or ad-hoc debug script
 * to quickly identify environment, auth, proxy, RPC, or page-structure issues.
 *
 * Usage:
 *   import { diagnose, dumpEnv, probeRpc } from '@nop-chaos/e2e-shared';
 *
 *   test('debug', async ({ page }) => {
 *     await login(page, { baseUrl: 'http://localhost:4173' });
 *     const report = await diagnose(page);
 *     console.log(JSON.stringify(report, null, 2));
 *   });
 */

import type { Page } from '@playwright/test';

// ─── Types ──────────────────────────────────────────────────────────────────

export interface EnvDump {
  /** All VITE_* env vars visible to the browser. */
  viteEnv: Record<string, string | boolean | undefined>;
  /** Vite mode: development, production, mock, etc. */
  mode: string;
  dev: boolean;
  prod: boolean;
  baseUrl: string;
}

export interface AuthDump {
  isAuthenticated: boolean;
  user: {
    id?: string;
    username?: string;
    nickname?: string;
    roles?: string[];
  } | null;
  hasToken: boolean;
  tokenPreview: string;
  localStorageKeys: string[];
  sessionStorageKeys: string[];
}

export interface RpcProbeResult {
  endpoint: string;
  status: number;
  ok: boolean;
  durationMs: number;
  data: unknown;
  dataPreview: string;
  error?: string;
}

export interface ProxyProbe {
  path: string;
  reachable: boolean;
  status: number;
  durationMs: number;
  bodyPreview: string;
}

export interface MenuDump {
  source: 'mock' | 'backend' | 'prototype' | 'empty' | 'unknown';
  itemCount: number;
  items: Array<{
    label: string;
    routePath?: string;
    pageType?: string;
  }>;
}

export interface PageFieldInfo {
  name: string;
  type: string;
  required: boolean;
  label?: string;
}

export interface PageTableInfo {
  /** How this table was identified (e.g. "caption:用户列表", "aria-label:..."). */
  identifiedBy: string;
  rowCount: number;
  columnHeaders: string[];
}

export interface PageDialogInfo {
  identifiedBy: string;
  role: string;
  title?: string;
}

export interface PageButtonInfo {
  text: string;
  identifiedBy: string;
  visible: boolean;
}

export interface PageStructureDump {
  url: string;
  title: string;
  forms: Array<{ name?: string; fields: PageFieldInfo[] }>;
  tables: PageTableInfo[];
  dialogs: PageDialogInfo[];
  /** Visible toolbar/section buttons (not row-action buttons). */
  buttons: PageButtonInfo[];
  cxdClassCount: number;
  fluxSlotCount: number;
  /** "Page not found" / 404 markers in body text. */
  has404: boolean;
  /** Raw body innerHTML length — sanity check for non-empty page. */
  bodyHTMLLength: number;
}

export interface DiagnosticOptions {
  /** RPC endpoints to probe (e.g. ['LoginApi__login', 'SiteMapApi__getSiteMap']). */
  rpcProbes?: Array<{ endpoint: string; payload?: unknown }>;
  /** Proxy paths to probe (e.g. ['/r/LoginApi__get', '/graphql']). */
  proxyProbes?: string[];
  /** When true, also runs dumpPageStructure (default: true). */
  includePageStructure?: boolean;
}

export interface DiagnosticReport {
  timestamp: string;
  url: string;
  env: EnvDump;
  auth: AuthDump;
  proxy: ProxyProbe[];
  rpc: RpcProbeResult[];
  menu: MenuDump;
  pageStructure?: PageStructureDump;
}

// ─── Implementation ─────────────────────────────────────────────────────────

/**
 * Read Vite env state from the running page.
 *
 * `import.meta.env` is not directly accessible from `page.evaluate` because
 * it's statically replaced by Vite at compile time. Instead, we detect the
 * mode and env vars from behavioral signals:
 * - Vite dev client (`/@vite/client`) presence → dev mode
 * - Built hashed assets (`/assets/*.js`) → production/preview mode
 * - Global `window.__VITE_ENV__` if the app explicitly exposes envs
 * - DOM markers (e.g. `data-vite-mode` attribute on `<html>`)
 */
export async function dumpEnv(page: Page): Promise<EnvDump> {
  return page.evaluate(() => {
    const hasViteClient = !!document.querySelector('script[src*="@vite/client"]');
    const hasBuiltAssets = !!document.querySelector(
      'script[src*="/assets/"][src$=".js"], link[rel="modulepreload"][href*="/assets/"]',
    );
    const hasDevModuleScripts = !!document.querySelector(
      'script[type="module"][src*="/src/"], script[type="module"][src*="/@fs/"]',
    );

    const globalEnv =
      (window as any).__VITE_ENV__ ??
      (window as any).__ENV__ ??
      {};

    const htmlDataset = document.documentElement.dataset;
    const mockFromDom = htmlDataset.mockMode ?? htmlDataset.viteEnableMock;

    const viteEnv: Record<string, string | boolean | undefined> = { ...globalEnv };
    if (mockFromDom !== undefined) viteEnv.VITE_ENABLE_MOCK = mockFromDom;

    return {
      viteEnv,
      mode: hasBuiltAssets ? 'production' : hasViteClient || hasDevModuleScripts ? 'development' : 'unknown',
      dev: (hasViteClient || hasDevModuleScripts) && !hasBuiltAssets,
      prod: hasBuiltAssets,
      baseUrl: window.location.origin,
    };
  });
}

/**
 * Read auth state from the running page (sessionStorage + localStorage).
 * Looks for the standard nop-chaos-next auth store key `auth:v2`.
 */
export async function dumpAuthState(page: Page): Promise<AuthDump> {
  return page.evaluate(() => {
    const raw = sessionStorage.getItem('auth:v2');
    let parsed: any = null;
    try {
      parsed = raw ? JSON.parse(raw) : null;
    } catch {
      parsed = null;
    }
    const state = parsed?.state ?? {};
    const user = state.user ?? null;
    const token: string | undefined =
      state.token ??
      localStorage.getItem('accessToken') ??
      localStorage.getItem('access_token') ??
      undefined;
    return {
      isAuthenticated: !!state.isAuthenticated,
      user: user
        ? {
            id: user.id,
            username: user.username,
            nickname: user.nickname,
            roles: user.roles,
          }
        : null,
      hasToken: !!token,
      tokenPreview: token ? `${token.slice(0, 16)}…` : '(none)',
      localStorageKeys: Object.keys(localStorage),
      sessionStorageKeys: Object.keys(sessionStorage),
    };
  });
}

/**
 * Execute a single RPC call from the browser context and return the structured result.
 * Uses the same `/r/<endpoint>` path as the real frontend, so proxy/auth headers apply.
 */
export async function probeRpc(
  page: Page,
  endpoint: string,
  payload?: unknown,
): Promise<RpcProbeResult> {
  const result = await page.evaluate(
    async ([endpoint, payload]) => {
      const url = `/r/${endpoint}`;
      const start = performance.now();
      try {
        const resp = await fetch(url, {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: payload ? JSON.stringify(payload) : '{}',
        });
        const durationMs = Math.round(performance.now() - start);
        const text = await resp.text();
        let data: unknown = text;
        try {
          data = JSON.parse(text);
        } catch {
          // keep as text
        }
        const dataPreview =
          typeof data === 'string' ? data.slice(0, 300) : JSON.stringify(data).slice(0, 300);
        return {
          endpoint,
          status: resp.status,
          ok: resp.ok,
          durationMs,
          data,
          dataPreview,
        };
      } catch (err: any) {
        return {
          endpoint,
          status: 0,
          ok: false,
          durationMs: Math.round(performance.now() - start),
          data: null,
          dataPreview: '',
          error: err?.message ?? String(err),
        };
      }
    },
    [endpoint, payload] as const,
  );
  return result;
}

/**
 * Probe a single proxy path via GET and return reachability info.
 * Use to verify the Vite proxy is forwarding correctly.
 */
export async function probeProxy(page: Page, path: string): Promise<ProxyProbe> {
  const result = await page.evaluate(
    async (path) => {
      const start = performance.now();
      try {
        const resp = await fetch(path, { method: 'GET' });
        const text = await resp.text();
        return {
          path,
          reachable: true,
          status: resp.status,
          durationMs: Math.round(performance.now() - start),
          bodyPreview: text.slice(0, 200),
        };
      } catch (err: any) {
        return {
          path,
          reachable: false,
          status: 0,
          durationMs: Math.round(performance.now() - start),
          bodyPreview: err?.message ?? String(err),
        };
      }
    },
    path,
  );
  return result;
}

/**
 * Fetch the menu config and classify its source.
 * Looks at the request URL pattern to determine if it came from mock/prototype/backend.
 */
export async function dumpMenuConfig(page: Page): Promise<MenuDump> {
  // Listen for the menu fetch
  const menuPromise = page.waitForResponse(
    (resp) =>
      resp.url().includes('SiteMapApi') ||
      resp.url().includes('menu.json') ||
      resp.url().includes('prototype/menu'),
    { timeout: 3000 },
  );

  // Reload to trigger a menu fetch
  await page.evaluate(() => {
    window.location.hash = window.location.hash || '#/';
  });

  let source: MenuDump['source'] = 'unknown';
  let items: MenuDump['items'] = [];

  try {
    const resp = await menuPromise;
    const url = resp.url();
    if (url.includes('prototype')) source = 'prototype';
    else if (url.includes('mock') || url.includes('menu.json')) source = 'mock';
    else source = 'backend';

    const json = await resp.json();
    const rawItems = json?.items ?? json?.data?.menus ?? json?.menus ?? [];
    if (Array.isArray(rawItems)) {
      items = rawItems.map((it: any) => ({
        label: it.displayName ?? it.label ?? it.title ?? '(unnamed)',
        routePath: it.routePath ?? it.url ?? it.path,
        pageType: it.pageType ?? it.component,
      }));
    }
  } catch {
    // Menu didn't load; try reading from sidebar DOM
    const sidebarItems = await page.evaluate(() => {
      const items = document.querySelectorAll(
        '[class*="sidebar-item"] button, nav button, [role="menuitem"]',
      );
      return Array.from(items)
        .slice(0, 30)
        .map((el) => ({
          label: el.textContent?.trim().slice(0, 60) ?? '',
          routePath: undefined,
          pageType: undefined,
        }))
        .filter((it) => it.label.length > 0);
    });
    source = sidebarItems.length > 0 ? 'unknown' : 'empty';
    items = sidebarItems;
  }

  return { source, itemCount: items.length, items };
}

/**
 * Dump the semantic structure of the current page.
 *
 * Scans for forms, tables, dialogs, and buttons — identified by stable
 * attributes like `name`, `aria-label`, `role`, `data-*` rather than
 * engine-specific CSS classes. Useful both for debugging and for discovering
 * what semantic identifiers are available when building engine adapters.
 */
export async function dumpPageStructure(page: Page): Promise<PageStructureDump> {
  return page.evaluate(() => {
    // Forms
    const forms = Array.from(document.querySelectorAll('form')).map((form) => {
      const fields: PageFieldInfo[] = Array.from(
        form.querySelectorAll('input, textarea, select'),
      ).map((el) => ({
        name: el.getAttribute('name') ?? '',
        type: el.getAttribute('type') ?? el.tagName.toLowerCase(),
        required: el.hasAttribute('required'),
        label:
          el.getAttribute('aria-label') ??
          el.getAttribute('placeholder') ??
          form.querySelector(`label[for="${el.id}"]`)?.textContent?.trim() ??
          undefined,
      }));
      return {
        name: form.getAttribute('name') ?? undefined,
        fields: fields.filter((f) => f.name || f.label),
      };
    });

    // Tables — identified by caption/aria-label/name attribute
    const tables: PageTableInfo[] = Array.from(document.querySelectorAll('table')).map((table) => {
      const caption =
        table.querySelector('caption')?.textContent?.trim() ||
        table.getAttribute('aria-label') ||
        table.getAttribute('data-entity') ||
        '(unlabeled table)';
      const headers = Array.from(table.querySelectorAll('thead th, thead [role="columnheader"]')).map(
        (th) => th.textContent?.trim() ?? '',
      );
      const rowCount = table.querySelectorAll('tbody tr').length;
      return {
        identifiedBy: `caption:${caption}`,
        rowCount,
        columnHeaders: headers,
      };
    });

    // Dialogs / drawers / modals
    const dialogSelectors = [
      '[role="dialog"]',
      '[role="alertdialog"]',
      '.cxd-Modal',
      '.cxd-Dialog',
      '.cxd-Drawer',
      '[data-slot="dialog-surface"]',
      '[data-slot="drawer-surface"]',
    ];
    const seenDialogs = new Set<Element>();
    const dialogs: PageDialogInfo[] = [];
    for (const sel of dialogSelectors) {
    for (const el of Array.from(document.querySelectorAll(sel))) {
        if (seenDialogs.has(el)) continue;
        seenDialogs.add(el);
        const title =
          el.getAttribute('aria-label') ||
          el.querySelector('[role="title"], .cxd-Modal-title, [data-slot="dialog-title"]')
            ?.textContent?.trim() ||
          undefined;
        dialogs.push({
          identifiedBy: title ? `title:${title}` : `selector:${sel}`,
          role: el.getAttribute('role') ?? '(none)',
          title,
        });
      }
    }

    // Visible toolbar buttons (not inside rows)
    const buttons: PageButtonInfo[] = Array.from(
      document.querySelectorAll('button, [role="button"]'),
    )
      .filter((el) => {
        const rect = el.getBoundingClientRect();
        if (rect.width === 0 || rect.height === 0) return false;
        // Exclude row-level action buttons
        const row = el.closest('tr, [role="row"], tbody');
        return !row;
      })
      .slice(0, 20)
      .map((el) => ({
        text: el.textContent?.trim().slice(0, 60) ?? '',
        identifiedBy:
          el.getAttribute('aria-label') ??
          el.getAttribute('data-testid') ??
          el.getAttribute('name') ??
          'text',
        visible: true,
      }))
      .filter((b) => b.text.length > 0);

    // Engine markers
    const cxdClassCount = document.querySelectorAll('[class*="cxd-"]').length;
    const fluxSlotCount = document.querySelectorAll('[data-slot]').length;

    const bodyText = document.body.innerText;
    return {
      url: window.location.href,
      title: document.title,
      forms,
      tables,
      dialogs,
      buttons,
      cxdClassCount,
      fluxSlotCount,
      has404: bodyText.includes('Page not found') || bodyText.includes('404'),
      bodyHTMLLength: document.body.innerHTML.length,
    };
  });
}

/**
 * Run a full diagnostic suite and return a structured report.
 *
 * This is the main entry point for debugging. Typical usage:
 *
 * ```ts
 * test('diagnose', async ({ page }) => {
 *   await login(page, { baseUrl: 'http://localhost:4173' });
 *   const report = await diagnose(page, {
 *     rpcProbes: [{ endpoint: 'LoginApi__login', payload: { ... } }],
 *     proxyProbes: ['/r/LoginApi__get'],
 *   });
 *   console.log(JSON.stringify(report, null, 2));
 * });
 * ```
 */
export async function diagnose(
  page: Page,
  options: DiagnosticOptions = {},
): Promise<DiagnosticReport> {
  const env = await dumpEnv(page);
  const auth = await dumpAuthState(page);

  const proxy: ProxyProbe[] = [];
  for (const path of options.proxyProbes ?? ['/r/LoginApi__get', '/graphql']) {
    proxy.push(await probeProxy(page, path));
  }

  const rpc: RpcProbeResult[] = [];
  for (const { endpoint, payload } of options.rpcProbes ?? []) {
    rpc.push(await probeRpc(page, endpoint, payload));
  }

  const menu = await dumpMenuConfig(page);

  const pageStructure = options.includePageStructure === false ? undefined : await dumpPageStructure(page);

  return {
    timestamp: new Date().toISOString(),
    url: page.url(),
    env,
    auth,
    proxy,
    rpc,
    menu,
    pageStructure,
  };
}

/**
 * Pretty-print a diagnostic report.
 * Useful for quick console output in debug specs.
 */
export function formatReport(report: DiagnosticReport): string {
  const lines: string[] = [];
  lines.push('=== E2E Diagnostic Report ===');
  lines.push(`Time: ${report.timestamp}`);
  lines.push(`URL:  ${report.url}`);

  lines.push('\n--- Environment ---');
  lines.push(`mode: ${report.env.mode}  dev: ${report.env.dev}  baseUrl: ${report.env.baseUrl}`);
  for (const [k, v] of Object.entries(report.env.viteEnv)) {
    lines.push(`  ${k} = ${JSON.stringify(v)}`);
  }

  lines.push('\n--- Auth ---');
  lines.push(`authenticated: ${report.auth.isAuthenticated}`);
  lines.push(`user: ${report.auth.user?.username ?? '(null)'} (${report.auth.user?.nickname ?? '-'})`);
  lines.push(`token: ${report.auth.tokenPreview}`);
  lines.push(`storage: LS=[${report.auth.localStorageKeys.join(',')}] SS=[${report.auth.sessionStorageKeys.join(',')}]`);

  lines.push('\n--- Proxy ---');
  for (const p of report.proxy) {
    lines.push(
      `  ${p.path}: ${p.reachable ? '✓' : '✗'} status=${p.status} ${p.durationMs}ms ${p.bodyPreview.slice(0, 80)}`,
    );
  }

  lines.push('\n--- RPC ---');
  for (const r of report.rpc) {
    lines.push(
      `  ${r.endpoint}: ${r.ok ? '✓' : '✗'} status=${r.status} ${r.durationMs}ms`,
    );
    if (r.error) lines.push(`    error: ${r.error}`);
    if (r.dataPreview) lines.push(`    data: ${r.dataPreview.slice(0, 120)}`);
  }

  lines.push('\n--- Menu ---');
  lines.push(`source: ${report.menu.source}  items: ${report.menu.itemCount}`);
  for (const item of report.menu.items.slice(0, 10)) {
    lines.push(`  - ${item.label} → ${item.routePath ?? '?'}`);
  }
  if (report.menu.itemCount > 10) lines.push(`  ... and ${report.menu.itemCount - 10} more`);

  if (report.pageStructure) {
    const ps = report.pageStructure;
    lines.push('\n--- Page Structure ---');
    lines.push(`title: ${ps.title}  404: ${ps.has404}  bodyHTML: ${ps.bodyHTMLLength}b`);
    lines.push(`engines: cxd=${ps.cxdClassCount}  data-slot=${ps.fluxSlotCount}`);
    lines.push(`forms: ${ps.forms.length}`);
    for (const f of ps.forms) {
      lines.push(`  form name=${f.name ?? '(none)'} fields=${f.fields.map((x) => x.name).join(',')}`);
    }
    lines.push(`tables: ${ps.tables.length}`);
    for (const t of ps.tables) {
      lines.push(`  ${t.identifiedBy} rows=${t.rowCount} cols=${t.columnHeaders.join('|')}`);
    }
    lines.push(`dialogs: ${ps.dialogs.length}`);
    for (const d of ps.dialogs) {
      lines.push(`  ${d.identifiedBy} role=${d.role}`);
    }
    lines.push(`buttons (non-row): ${ps.buttons.length}`);
    for (const b of ps.buttons.slice(0, 8)) {
      lines.push(`  "${b.text}" [${b.identifiedBy}]`);
    }
  }

  return lines.join('\n');
}

// ─── Flux runtime debug recorder ────────────────────────────────────────────

/**
 * 手动开启 flux 调试记录器（`window.__FLUX_DEBUG__ = true`）。
 *
 * 注意：e2e-shared 的 `test` fixture 已默认开启（无需调用本函数）。
 * 仅当使用 Playwright 原生 `test`（而非 `@nop-chaos/e2e-shared` 的 `test`）时，
 * 需要手动调用，且必须在导航前（页面加载前）执行，因为 flux env 在首次渲染时创建。
 *
 * 用法：
 *   import { enableFluxDebug, dumpFluxDebug, formatFluxDebug } from '@nop-chaos/e2e-shared';
 *
 *   test.beforeEach(async ({ page }) => {
 *     await enableFluxDebug(page);
 *   });
 *
 *   test('...', async ({ page }) => {
 *     // ... 测试逻辑
 *     if (hasError) console.log(formatFluxDebug(await dumpFluxDebug(page)));
 *   });
 */
export async function enableFluxDebug(page: Page): Promise<void> {
  await page.addInitScript(() => {
    (window as any).__FLUX_DEBUG__ = true;
  });
}

export interface FluxDebugEntryDump {
  phase: string;
  ts: number;
  url?: string;
  method?: string;
  ok?: boolean;
  status?: number;
  error?: string;
  level?: string;
  message?: string;
  dataPreview?: string;
}

export interface FluxDebugDump {
  enabled: boolean;
  entryCount: number;
  entries: FluxDebugEntryDump[];
  /** 过滤后的错误条目（phase=error 或 notify level=error） */
  errors: FluxDebugEntryDump[];
  /** 与给定 URL 片段匹配的请求/响应条目 */
  requests: FluxDebugEntryDump[];
}

/**
 * 从页面读取 flux 调试记录（`window.__fluxDebug`）。
 * 若调试开关未开启，entries 为空且 enabled 为 false。
 */
export async function dumpFluxDebug(page: Page): Promise<FluxDebugDump> {
  return page.evaluate(() => {
    const w = window as any;
    const list: unknown[] = w.__fluxDebug ?? [];
    const toDump = (e: any): FluxDebugEntryDump => ({
      phase: e?.phase,
      ts: e?.ts,
      url: e?.url,
      method: e?.method,
      ok: e?.ok,
      status: e?.status,
      error: e?.error,
      level: e?.level,
      message: e?.message,
      dataPreview:
        typeof e?.data === 'string'
          ? e.data.slice(0, 300)
          : e?.data != null
            ? JSON.stringify(e.data).slice(0, 300)
            : e?.dataPreview,
    });
    const entries = list.map(toDump);
    return {
      enabled: w.__FLUX_DEBUG__ === true,
      entryCount: entries.length,
      entries,
      errors: entries.filter(
        (e: FluxDebugEntryDump) => e.phase === 'error' || (e.phase === 'notify' && e.level === 'error'),
      ),
      requests: entries.filter((e: FluxDebugEntryDump) => e.phase === 'request' || e.phase === 'response'),
    };
  });
}

/**
 * 按 URL 片段过滤 flux 调试记录（如 'NopAuthResource__update'）。
 */
export async function dumpFluxDebugFor(
  page: Page,
  urlFragment: string,
): Promise<FluxDebugDump> {
  const dump = await dumpFluxDebug(page);
  return {
    ...dump,
    entries: dump.entries.filter((e) => (e.url ?? '').includes(urlFragment)),
    errors: dump.errors.filter((e) => (e.url ?? '').includes(urlFragment)),
    requests: dump.requests.filter((e) => (e.url ?? '').includes(urlFragment)),
  };
}

/**
 * 格式化 flux 调试记录为可读文本（console 输出用）。
 */
export function formatFluxDebug(dump: FluxDebugDump): string {
  const lines: string[] = [];
  lines.push(`=== Flux Debug (enabled: ${dump.enabled}, entries: ${dump.entryCount}) ===`);
  for (const e of dump.entries) {
    const t = new Date(e.ts).toISOString().slice(11, 23);
    const detail =
      e.phase === 'request'
        ? `${e.method ?? 'POST'} ${e.url} data=${e.dataPreview ?? ''}`
        : e.phase === 'response'
          ? `${e.url} ok=${e.ok} status=${e.status} data=${e.dataPreview ?? ''}`
          : e.phase === 'error'
            ? `${e.url ?? ''} ${e.error ?? ''}`
            : `${e.level} ${e.message}`;
    lines.push(`  [${t}] ${e.phase}: ${detail}`);
  }
  if (dump.errors.length > 0) {
    lines.push(`--- Errors (${dump.errors.length}) ---`);
    for (const e of dump.errors) {
      lines.push(`  [${e.level ?? 'error'}] ${e.url ?? ''} ${e.error ?? e.message ?? ''}`);
    }
  }
  return lines.join('\n');
}
