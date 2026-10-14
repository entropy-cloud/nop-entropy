import type { Page as PlaywrightPage } from '@playwright/test';

/**
 * hash 路由导航原语（全库唯一实现）。
 *
 * 不用 `page.goto('#/route')`：SPA 已加载时对同页 hash 的 goto 会被壳层自身的导航
 * （如登录后重定向）踩掉或静默忽略。改为直接赋值 `location.hash` 触发 hashchange，
 * 再校验 hash 生效，未生效或被弹回则重试。
 *
 * 文档尚未加载（about:blank / SPA 未启动）时 hash 赋值不会渲染应用，退回整页加载——
 * 相对 URL 由 playwright.config 的 `use.baseURL` 解析，与导航原语同源。
 */
export async function gotoHash(
  page: PlaywrightPage,
  hashRoute: string,
  attempts = 3,
): Promise<void> {
  const target = `#/${hashRoute.replace(/^#?\/?/, '')}`;

  const booted = await page
    .evaluate(() => Boolean(document.body && document.body.childElementCount > 0))
    .catch(() => false);
  if (!booted) {
    await page.goto(target);
    await page.waitForLoadState('networkidle').catch(() => {});
    return;
  }

  for (let i = 0; i < attempts; i++) {
    await page.evaluate((h) => {
      window.location.hash = h;
    }, target);

    const applied = await page
      .waitForFunction((h) => window.location.hash === h, target, { timeout: 3_000 })
      .then(() => true)
      .catch(() => false);
    if (!applied) {
      await page.waitForTimeout(600);
      continue;
    }

    // 防回弹：壳层登录重定向链（/auth/login → / → homePath）未走完时赋 hash 会被
    // catch-all 路由弹回 homePath——静置后复读一次，被弹回则重试
    await page.waitForTimeout(800);
    const stillThere = await page.evaluate((h) => window.location.hash === h, target);
    if (stillThere) {
      await page.waitForLoadState('networkidle').catch(() => {});
      return;
    }
  }

  throw new Error(`hash 导航未生效: ${target}（当前 ${page.url()}）`);
}

/**
 * `gotoHash` 的非抛错版本：被登录守卫弹回登录路由等场景下返回 false，
 * 供"登出后访问受保护页面"这类**预期导航失败**的用例使用。
 */
export async function tryGotoHash(
  page: PlaywrightPage,
  hashRoute: string,
): Promise<boolean> {
  return gotoHash(page, hashRoute).then(
    () => true,
    () => false,
  );
}
