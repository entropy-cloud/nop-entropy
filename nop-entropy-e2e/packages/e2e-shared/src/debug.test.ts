import { describe, it, expect } from 'vitest';
import { formatReport, type DiagnosticReport } from './debug';

function makeReport(overrides: Partial<DiagnosticReport> = {}): DiagnosticReport {
  return {
    timestamp: '2026-07-21T12:00:00.000Z',
    url: 'http://localhost:4173/#/NopAuthUser-main',
    env: {
      viteEnv: { VITE_ENABLE_MOCK: 'true' },
      mode: 'development',
      dev: true,
      prod: false,
      baseUrl: 'http://localhost:4173',
    },
    auth: {
      isAuthenticated: true,
      user: { id: 'u1', username: 'nop', nickname: 'Nopper', roles: ['admin'] },
      hasToken: true,
      tokenPreview: 'eyJhbGciOiJIUz…',
      localStorageKeys: ['accessToken', 'nop-language:v1'],
      sessionStorageKeys: ['auth:v2'],
    },
    proxy: [
      { path: '/r/LoginApi__get', reachable: true, status: 401, durationMs: 5, bodyPreview: '{}' },
      { path: '/graphql', reachable: false, status: 0, durationMs: 3000, bodyPreview: 'timeout' },
    ],
    rpc: [
      {
        endpoint: 'LoginApi__login',
        status: 200,
        ok: true,
        durationMs: 120,
        data: { ok: true },
        dataPreview: '{"ok":true}',
      },
    ],
    menu: {
      source: 'backend',
      itemCount: 12,
      items: [
        { label: 'Dashboard', routePath: '/dashboard' },
        { label: 'User Management', routePath: '/NopAuthUser-main' },
      ],
    },
    pageStructure: {
      url: 'http://localhost:4173/#/NopAuthUser-main',
      title: 'User Management',
      forms: [{ name: 'filterForm', fields: [{ name: 'userName', type: 'text', required: false }] }],
      tables: [{ identifiedBy: 'caption:User List', rowCount: 5, columnHeaders: ['Name', 'Email'] }],
      dialogs: [{ identifiedBy: 'title:Create User', role: 'dialog', title: 'Create User' }],
      buttons: [{ text: 'Add', identifiedBy: 'aria-label:add', visible: true }],
      cxdClassCount: 42,
      fluxSlotCount: 0,
      has404: false,
      bodyHTMLLength: 50000,
    },
    ...overrides,
  };
}

describe('formatReport', () => {
  it('renders all sections when fully populated', () => {
    const out = formatReport(makeReport());
    expect(out).toContain('=== E2E Diagnostic Report ===');
    expect(out).toContain('--- Environment ---');
    expect(out).toContain('VITE_ENABLE_MOCK = "true"');
    expect(out).toContain('--- Auth ---');
    expect(out).toContain('user: nop (Nopper)');
    expect(out).toContain('--- Proxy ---');
    expect(out).toContain('/r/LoginApi__get: ✓');
    expect(out).toContain('/graphql: ✗');
    expect(out).toContain('--- RPC ---');
    expect(out).toContain('LoginApi__login: ✓');
    expect(out).toContain('--- Menu ---');
    expect(out).toContain('source: backend');
    expect(out).toContain('User Management → /NopAuthUser-main');
    expect(out).toContain('--- Page Structure ---');
    expect(out).toContain('engines: cxd=42  data-slot=0');
    expect(out).toContain('caption:User List rows=5 cols=Name|Email');
    expect(out).toContain('title:Create User role=dialog');
  });

  it('omits page structure section when undefined', () => {
    const out = formatReport(makeReport({ pageStructure: undefined }));
    expect(out).not.toContain('--- Page Structure ---');
  });

  it('truncates long menu lists with "and N more"', () => {
    const items = Array.from({ length: 15 }, (_, i) => ({ label: `Item ${i}`, routePath: `/item-${i}` }));
    const out = formatReport(makeReport({ menu: { source: 'backend', itemCount: 15, items } }));
    expect(out).toContain('... and 5 more');
  });

  it('shows null/empty markers for unauthenticated state', () => {
    const out = formatReport(
      makeReport({
        auth: {
          isAuthenticated: false,
          user: null,
          hasToken: false,
          tokenPreview: '(none)',
          localStorageKeys: [],
          sessionStorageKeys: [],
        },
      }),
    );
    expect(out).toContain('user: (null) (-)');
    expect(out).toContain('token: (none)');
  });

  it('shows error and data preview for failed RPC', () => {
    const out = formatReport(
      makeReport({
        rpc: [
          {
            endpoint: 'SiteMapApi__getSiteMap',
            status: 401,
            ok: false,
            durationMs: 5,
            data: null,
            dataPreview: '',
            error: 'Unauthorized',
          },
        ],
      }),
    );
    expect(out).toContain('SiteMapApi__getSiteMap: ✗ status=401');
    expect(out).toContain('error: Unauthorized');
  });
});
