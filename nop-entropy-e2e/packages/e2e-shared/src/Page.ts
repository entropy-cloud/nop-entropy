import type { Locator, Page as PlaywrightPage } from '@playwright/test';
import type { EngineAdapter } from './types';
import { gotoHash, tryGotoHash } from './hashRoute';

/** 所有页面对象的基类：持有 page 与渲染引擎适配器，并提供确定性 hash 导航与字段定位。 */
export abstract class BasePage {
  constructor(
    protected page: PlaywrightPage,
    protected engine: EngineAdapter,
  ) {}

  /** 导航到 hash 路由（传不含前导 `#/` 的路径，如 `NopAuthUser-main`）。 */
  async goto(hashRoute: string): Promise<void> {
    await gotoHash(this.page, hashRoute);
  }

  /** 尽力导航到 hash 路由；被登录守卫弹回时返回 false（预期导航失败的用例）。 */
  async tryGoto(hashRoute: string): Promise<boolean> {
    return tryGotoHash(this.page, hashRoute);
  }

  /** 按 fieldName 定位页面内的字段输入控件（不限 CRUD 查询表单）。 */
  protected fieldInput(fieldName: string): Locator {
    return this.engine.fieldInput(this.page, fieldName);
  }

  /** 按 fieldName 填写页面内的字段输入控件。 */
  protected async fillField(fieldName: string, value: string): Promise<void> {
    await this.fieldInput(fieldName).fill(value);
  }
}
