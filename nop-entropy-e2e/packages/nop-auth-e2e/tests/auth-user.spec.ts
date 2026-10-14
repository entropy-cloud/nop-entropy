import { test } from '@nop-entropy/e2e-shared';
import { expect, type APIRequestContext, type Page } from '@playwright/test';
import { login, loginRpc, rpc } from '@nop-entropy/e2e-shared';
import type { EngineAdapter } from '@nop-entropy/e2e-shared';
import { UserPO } from './page-objects/user.po.js';

const TEST_ID = `e2e_${Date.now()}`;

interface UserSaveInput {
  userName: string;
  nickName: string;
  password: string;
  status: number;
  userType: number;
  gender: number;
  email?: string;
  phone?: string;
}

interface UserDetail {
  id: string;
  userId: string;
  userName: string;
  nickName: string;
  email?: string;
  phone?: string;
}

interface FindPageResult {
  total: number;
  items: { userName: string; nickName: string }[];
}

const createdUserIds: string[] = [];

async function cleanupTestUsers(request: APIRequestContext): Promise<void> {
  const resp = await rpc<{ items: { id: string; userName: string }[] }>(request, 'NopAuthUser__findPage', {
    query: { offset: 0, limit: 200 },
  });
  if (!resp.ok) return;
  for (const item of resp.data.items) {
    if (item.userName.startsWith('e2e_')) {
      await rpc(request, 'NopAuthUser__delete', { id: item.id }).catch(() => {});
    }
  }
}

function makeUserData(suffix: string, overrides?: Partial<UserSaveInput>): UserSaveInput {
  return {
    userName: `${TEST_ID}_${suffix}`,
    nickName: `E2E_${suffix}`,
    password: 'Test@1234',
    status: 1,
    userType: 1,
    gender: 1,
    ...overrides,
  };
}

/** 已登录的用户管理页面——spec 只拿业务页面对象，不接触引擎与 DOM。 */
async function openUserAdmin(page: Page, engine: EngineAdapter): Promise<UserPO> {
  await login(page, { username: 'nop', password: '123' });
  const userPO = new UserPO(page, engine);
  await userPO.goto();
  return userPO;
}

test.describe('用户管理 - RPC', () => {
  test.beforeAll(async ({ request }) => {
    await loginRpc(request);
    await cleanupTestUsers(request);
  });

  test.afterAll(async ({ request }) => {
    for (const id of createdUserIds) {
      await rpc(request, 'NopAuthUser__delete', { id }).catch(() => {});
    }
    createdUserIds.length = 0;
  });

  test('RPC: 创建用户', async ({ request }) => {
    const data = makeUserData('create');

    const resp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });

    expect(resp.ok).toBeTruthy();
    expect(resp.data.userName).toBe(data.userName);
    expect(resp.data.nickName).toBe(data.nickName);
    expect(resp.data.id).toBeTruthy();

    createdUserIds.push(resp.data.id);
  });

  test('RPC: 查询用户列表', async ({ request }) => {
    const data = makeUserData('findlist');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const resp = await rpc<FindPageResult>(request, 'NopAuthUser__findPage', {
      query: { filter: { $type: 'eq', name: 'userName', value: data.userName } },
    });

    expect(resp.ok).toBeTruthy();
    expect(resp.data.total).toBeGreaterThanOrEqual(1);
    expect(resp.data.items.some((item) => item.userName === data.userName)).toBeTruthy();
  });

  test('RPC: 读取用户详情', async ({ request }) => {
    // 不经通用 CRUD 写 email/phone：W12 联系方式写拦截仅放行 admin（ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED）
    const data = makeUserData('getdetail');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const resp = await rpc<UserDetail>(request, 'NopAuthUser__get', { id: saveResp.data.id });

    expect(resp.ok).toBeTruthy();
    expect(resp.data.userName).toBe(data.userName);
    expect(resp.data.nickName).toBe(data.nickName);
  });

  test('RPC: 更新用户', async ({ request }) => {
    const data = makeUserData('update');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const updatedNickName = `E2E已更新_${TEST_ID}`;
    const resp = await rpc<UserDetail>(request, 'NopAuthUser__update', {
      data: { id: saveResp.data.id, nickName: updatedNickName },
    });
    expect(resp.ok).toBeTruthy();

    const verify = await rpc<UserDetail>(request, 'NopAuthUser__get', { id: saveResp.data.id });
    expect(verify.data.nickName).toBe(updatedNickName);
  });

  test('RPC: 删除用户', async ({ request }) => {
    const data = makeUserData('delete');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });

    expect((await rpc(request, 'NopAuthUser__delete', { id: saveResp.data.id })).ok).toBeTruthy();

    const verify = await rpc<UserDetail>(request, 'NopAuthUser__get', { id: saveResp.data.id });
    expect(verify.ok).toBeFalsy();
  });
});

test.describe('用户管理 - 浏览器', () => {
  test.beforeEach(async ({ request }) => {
    await loginRpc(request);
  });

  test.afterEach(async ({ request }) => {
    await loginRpc(request).catch(() => {});
    for (const id of createdUserIds) {
      await rpc(request, 'NopAuthUser__delete', { id }).catch(() => {});
    }
    createdUserIds.length = 0;
  });

  test('浏览器: 导航到用户管理页面', async ({ page, engine }) => {
    const userPO = await openUserAdmin(page, engine);

    await expect(page).toHaveURL(/NopAuthUser-main/);
    await userPO.assertListVisible();
  });

  test('浏览器: 创建新用户', async ({ page, engine }) => {
    const data = makeUserData('uicreate');
    const userPO = await openUserAdmin(page, engine);

    await userPO.createUser(data);

    await userPO.searchUser(data.userName);
    await userPO.assertUserExists(data.userName);
  });

  test('浏览器: 查看用户详情', async ({ request, page, engine }) => {
    await loginRpc(request);
    // 不经通用 CRUD 写 email/phone：W12 联系方式写拦截仅放行 admin（ERR_AUTH_CONTACT_CHANGE_NOT_ALLOWED）
    const data = makeUserData('uiview');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const userPO = await openUserAdmin(page, engine);
    await userPO.searchUser(data.userName);
    await userPO.assertUserExists(data.userName);

    await userPO.viewUser(data.userName);

    expect(await userPO.readViewField('userName')).toBe(data.userName);
    expect(await userPO.readViewField('nickName')).toBe(data.nickName);
  });

  test('浏览器: 编辑用户', async ({ request, page, engine }) => {
    await loginRpc(request);
    const data = makeUserData('uiedit');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const userPO = await openUserAdmin(page, engine);
    await userPO.searchUser(data.userName);
    await userPO.assertUserExists(data.userName);

    const updatedNickName = `E2E编辑后_${TEST_ID}`;
    await userPO.editUser(data.userName, { nickName: updatedNickName });

    await userPO.searchUser(data.userName);
    expect(await userPO.readTableField(data.userName, 'nickName')).toBe(updatedNickName);
  });

  test('浏览器: 删除用户', async ({ request, page, engine }) => {
    await loginRpc(request);
    const data = makeUserData('uidelete');
    const saveResp = await rpc<UserDetail>(request, 'NopAuthUser__save', { data });
    createdUserIds.push(saveResp.data.id);

    const userPO = await openUserAdmin(page, engine);
    await userPO.searchUser(data.userName);
    await userPO.assertUserExists(data.userName);

    await userPO.deleteUser(data.userName);

    await userPO.searchUser(data.userName);
    await userPO.assertUserNotExists(data.userName);

    const idx = createdUserIds.indexOf(saveResp.data.id);
    if (idx >= 0) createdUserIds.splice(idx, 1);
  });

  test('浏览器: 按用户名检索用户', async ({ request, page, engine }) => {
    await loginRpc(request);
    const dataA = makeUserData('searchA');
    const dataB = makeUserData('searchB');
    const saveA = await rpc<UserDetail>(request, 'NopAuthUser__save', { data: dataA });
    const saveB = await rpc<UserDetail>(request, 'NopAuthUser__save', { data: dataB });
    createdUserIds.push(saveA.data.id, saveB.data.id);

    const userPO = await openUserAdmin(page, engine);

    await userPO.searchUser(dataA.userName);
    await userPO.assertUserExists(dataA.userName);

    await userPO.searchUser(dataB.userName);
    await userPO.assertUserExists(dataB.userName);
  });
});
