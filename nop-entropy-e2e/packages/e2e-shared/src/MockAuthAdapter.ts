import type { Page } from '@playwright/test';

export type LoginVariant = 'harbor' | 'default';

const MOCK_TOKEN_PREFIX = 'mock-token:';
const MOCK_REFRESH_TOKEN_PREFIX = 'mock-refresh-token:';

export interface LoginOptions {
  setup?: () => Promise<void> | void;
  username?: string;
  roles?: string[];
  defaultPassword?: string;
  harborPassword?: string;
  mockMenuRoutes?: boolean;
}

export const defaultSiteMapResponse = {
  status: 0,
  data: {
    children: [
      {
        id: 'dashboard',
        displayName: 'Dashboard',
        routePath: '/dashboard',
        component: 'dashboard',
        hidden: false,
        meta: { sort: 1 },
      },
      {
        id: 'ai-workbench',
        displayName: 'AI Workbench',
        routePath: '/ai-workbench',
        component: 'ai-workbench',
        hidden: false,
        meta: { sort: 2 },
      },
      {
        id: 'flow-editor',
        displayName: 'Flow Editor',
        routePath: '/flow-editor',
        component: 'flow-editor',
        hidden: false,
        meta: { sort: 3 },
        children: [
          {
            id: 'flow-editor-list',
            displayName: 'Flow Library',
            routePath: '/flow-editor',
            component: 'flow-editor',
            hidden: false,
          },
          {
            id: 'flow-editor-edit',
            displayName: 'Flow Editor Edit',
            routePath: '/flow-editor/:id',
            component: 'flow-editor/:id',
            hidden: true,
          },
        ],
      },
      {
        id: 'data-management',
        displayName: 'Data Management',
        routePath: '/data-management',
        component: 'data-management',
        hidden: false,
        meta: { sort: 4 },
        children: [
          {
            id: 'master-detail',
            displayName: 'Master Detail',
            routePath: '/data-management/master-detail',
            component: 'data-management/master-detail',
            hidden: false,
          },
          {
            id: 'master-detail-id',
            displayName: 'Master Detail Detail',
            routePath: '/data-management/master-detail/:id',
            component: 'data-management/master-detail/:id',
            hidden: true,
          },
        ],
      },
      {
        id: 'plugins',
        displayName: 'Plugins',
        routePath: '/plugins',
        component: 'plugins',
        hidden: false,
        meta: { sort: 5 },
        children: [
          {
            id: 'plugins-management',
            displayName: 'Plugin Management',
            routePath: '/plugins/management',
            component: 'plugins/management',
            hidden: false,
          },
          {
            id: 'plugins-demo',
            displayName: 'Plugin Demo',
            routePath: '/plugins/demo',
            component: 'plugin',
            hidden: false,
            url: '/plugins/plugin-demo.system.js',
          },
        ],
      },
      {
        id: 'amis-preview',
        displayName: 'Amis Preview',
        routePath: '/amis/preview',
        component: 'AMIS',
        hidden: false,
        url: '/data/amis-preview.json',
        meta: { sort: 6 },
      },
      {
        id: 'settings',
        displayName: 'Settings',
        routePath: '/settings',
        component: 'settings',
        hidden: false,
        meta: { sort: 7 },
        children: [
          {
            id: 'settings-theme',
            displayName: 'Theme',
            routePath: '/settings/theme',
            component: 'settings/theme',
            hidden: false,
          },
          {
            id: 'settings-language',
            displayName: 'Language',
            routePath: '/settings/language',
            component: 'settings/language',
            hidden: false,
          },
          {
            id: 'settings-layout',
            displayName: 'Layout',
            routePath: '/settings/layout',
            component: 'settings/layout',
            hidden: false,
          },
        ],
      },
      {
        id: 'help',
        displayName: 'Help',
        routePath: '/help',
        component: 'help',
        hidden: false,
        meta: { sort: 8 },
        children: [
          {
            id: 'help-guide',
            displayName: 'Guide',
            routePath: '/help/guide',
            component: 'help/guide',
            hidden: false,
          },
        ],
      },
    ],
  },
};

export const defaultMenuResponse = {
  home: '/dashboard',
  items: [
    {
      id: 'dashboard',
      title: 'Dashboard',
      path: '/dashboard',
      icon: 'layout-dashboard',
      pageType: 'builtin',
      componentId: 'dashboard',
      sort: 1,
    },
    {
      id: 'ai-workbench',
      title: 'AI Workbench',
      path: '/ai-workbench',
      icon: 'bot',
      pageType: 'builtin',
      componentId: 'ai-workbench',
      sort: 2,
    },
    {
      id: 'flow-editor',
      title: 'Flow Editor',
      path: '/flow-editor',
      icon: 'git-branch',
      pageType: 'builtin',
      componentId: 'flow-editor',
      sort: 3,
      children: [
        {
          id: 'flow-editor-list',
          title: 'Flow Library',
          path: '/flow-editor',
          icon: 'list',
          pageType: 'builtin',
          componentId: 'flow-editor',
        },
        {
          id: 'flow-editor-edit',
          title: 'Flow Editor Edit',
          path: '/flow-editor/:id',
          icon: 'edit',
          pageType: 'builtin',
          componentId: 'flow-editor-edit',
          hideInMenu: true,
        },
      ],
    },
    {
      id: 'data-management',
      title: 'Data Management',
      path: '/data-management',
      icon: 'database',
      pageType: 'builtin',
      componentId: 'data-management',
      sort: 4,
      children: [
        {
          id: 'master-detail',
          title: 'Master Detail',
          path: '/data-management/master-detail',
          icon: 'table',
          pageType: 'builtin',
          componentId: 'master-detail',
        },
        {
          id: 'master-detail-id',
          title: 'Master Detail Detail',
          path: '/data-management/master-detail/:id',
          icon: 'file-text',
          pageType: 'builtin',
          componentId: 'master-detail-detail',
          hideInMenu: true,
        },
      ],
    },
    {
      id: 'plugins',
      title: 'Plugins',
      path: '/plugins',
      icon: 'puzzle',
      pageType: 'builtin',
      componentId: 'plugins-overview',
      sort: 5,
      children: [
        {
          id: 'plugins-management',
          title: 'Plugin Management',
          path: '/plugins/management',
          icon: 'plug-zap',
          pageType: 'builtin',
          componentId: 'plugins-management',
        },
        {
          id: 'plugins-demo',
          title: 'Plugin Demo',
          path: '/plugins/demo',
          icon: 'blocks',
          pageType: 'plugin',
          componentId: 'plugins-demo',
          pluginUrl: '/plugins/plugin-demo.system.js',
        },
      ],
    },
      {
        id: 'flux-demo',
        title: 'Flux Demo',
        path: '/flux-demo',
        icon: 'sparkles',
        pageType: 'flux',
        schemaPath: '/data/flux-demo.json',
        sort: 7,
      },
      {
        id: 'flux-dashboard',
        title: 'Flux Dashboard',
        path: '/flux-dashboard',
        icon: 'layout-dashboard',
        pageType: 'flux',
        schemaPath: '/data/flux-dashboard.json',
        sort: 8,
      },
      {
        id: 'flux-report',
        title: 'Flux Report',
        path: '/flux-report',
        icon: 'file-text',
        pageType: 'flux',
        schemaPath: '/data/flux-report.json',
        sort: 9,
      },
      {
        id: 'flux-complex-form',
        title: 'Flux Complex Form',
        path: '/flux-complex-form',
        icon: 'table-properties',
        pageType: 'flux',
        schemaPath: '/data/flux-complex-form.json',
        sort: 10,
      },
      {
      id: 'settings',
      title: 'Settings',
      path: '/settings',
      icon: 'settings-2',
      pageType: 'builtin',
      componentId: 'settings-home',
      sort: 7,
      children: [
        {
          id: 'settings-theme',
          title: 'Theme',
          path: '/settings/theme',
          icon: 'palette',
          pageType: 'builtin',
          componentId: 'settings-theme',
        },
        {
          id: 'settings-language',
          title: 'Language',
          path: '/settings/language',
          icon: 'languages',
          pageType: 'builtin',
          componentId: 'settings-language',
        },
        {
          id: 'settings-layout',
          title: 'Layout',
          path: '/settings/layout',
          icon: 'panels-top-left',
          pageType: 'builtin',
          componentId: 'settings-layout',
        },
      ],
    },
    {
      id: 'help',
      title: 'Help',
      path: '/help',
      icon: 'badge-help',
      pageType: 'builtin',
      componentId: 'help-home',
      sort: 8,
      children: [
        {
          id: 'help-guide',
          title: 'Guide',
          path: '/help/guide',
          icon: 'book-open-text',
          pageType: 'builtin',
          componentId: 'help-guide',
        },
      ],
    },
  ],
};

export function buildMockLoginResponse(username: string, roles: string[] = ['admin', 'developer']) {
  const normalizedUsername = username.trim() || 'nop';

  return {
    accessToken: `${MOCK_TOKEN_PREFIX}${encodeURIComponent(normalizedUsername)}`,
    expiresIn: 5 * 60,
    refreshToken: `${MOCK_REFRESH_TOKEN_PREFIX}${encodeURIComponent(normalizedUsername)}`,
    refreshExpiresIn: 24 * 60 * 60,
    userInfo: {
      username: normalizedUsername,
      nickname: normalizedUsername === 'nop' ? 'NOP Mock User' : `${normalizedUsername} Mock User`,
      email: `${normalizedUsername}@mock.local`,
      roles: roles.map((role: string) => ({ value: role })),
    },
  };
}

export async function login(page: Page, options: LoginOptions = {}): Promise<LoginVariant> {
  const {
    setup,
    username,
    roles,
    defaultPassword = '123456',
    harborPassword: _harborPassword = '123456',
    mockMenuRoutes = true,
  } = options;
  const preferredUsername = username ?? 'nop';

  await page.addInitScript(() => {
    const persistedLanguage = window.localStorage.getItem('nop-language:v1');
    window.localStorage.clear();
    window.sessionStorage.clear();

    if (persistedLanguage) {
      window.localStorage.setItem('nop-language:v1', persistedLanguage);
    }
  });

  await page.route('**/r/LoginApi__login?*', async (route) => {
    const requestBody = route.request().postDataJSON() as
      | { principalId?: string; principalSecret?: string }
      | undefined;
    const requestedUsername = requestBody?.principalId?.trim() || preferredUsername;
    const requestedPassword = requestBody?.principalSecret?.trim() || defaultPassword;

    if (!requestedPassword) {
      await route.fulfill({
        status: 400,
        contentType: 'application/json',
        body: JSON.stringify({ msg: 'Password is required in mock mode', status: -1 }),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(buildMockLoginResponse(requestedUsername, roles)),
    });
  });

  if (mockMenuRoutes !== false) {
    await page.route('**/r/SiteMapApi__getSiteMap', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(defaultSiteMapResponse),
      });
    });

    await page.route('**/data/menu-config.json', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(defaultMenuResponse),
      });
    });
  }

  await setup?.();
  await page.goto('/#/auth/login');

  const passwordInput = page.locator('input[type="password"]');
  const hasPasswordInput = await passwordInput
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  if (!hasPasswordInput) {
    const usernameInput = page.locator('input').first();
    const submitButton = page.locator('button[type="submit"]');

    await submitButton.waitFor({ state: 'visible' });
    await usernameInput.fill(username ?? 'harbor');
    await submitButton.click();
    await page.waitForURL((url) => !url.href.includes('/auth/login'));
    return 'harbor';
  }

  const usernameInput = page.locator('input').first();
  const submitButton = page.locator('button[type="submit"]');

  await submitButton.waitFor({ state: 'visible' });
  await usernameInput.fill(preferredUsername);
  await passwordInput.fill(defaultPassword);
  await submitButton.click();
  await page.waitForURL((url) => !url.href.includes('/auth/login'));

  return 'default';
}

export class MockAuthAdapter {
  static login = login;
  static buildMockLoginResponse = buildMockLoginResponse;
  static defaultSiteMapResponse = defaultSiteMapResponse;
  static defaultMenuResponse = defaultMenuResponse;
}
