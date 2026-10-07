import type { APIRequestContext } from '@playwright/test';
import { rpc } from '@nop-entropy/e2e-shared';

const E2E_ROLE_ID = 'e2e-admin';

/**
 * 授予默认用户 e2e-admin 角色并绑定功能点权限。
 *
 * 平台契约（docs-for-ai/02-core-guides/auth-and-permissions.md）：自定义 action 的
 * xui:permissions 依赖 NopAuthRoleResource 角色资源授权——dev 空库无任何授权时，
 * PageProvider 把按钮的 xui:roles 置为 none，任何账号都看不到功能点按钮（功能死锁）。
 *
 * 播种链（均经 RPC，幂等）：
 * 1. NopAuthSite 'main' 行——NopAuthResource.siteId 的 obj/NopAuthSite 字典校验查库；
 * 2. NopAuthResource FNPT 行——NopAuthRoleResource.resourceId 的 ORM 外键校验查库
 *    （站点地图本身用静态 action-auth.xml，无需 DB 资源行，这是校验层要求）；
 * 3. 角色 + 角色资源授权 + 用户绑定；
 * 4. 等 site-map 缓存过期（nop.auth.site-map.cache-timeout=1s）后重建。
 *
 * 必须在该模块任何页面被 PageProvider 加载之前完成：页面模型的权限转换会按首次
 * 调用时的授权快照原地把 xui:roles 写进缓存模型（transformPermissions 原地
 * remove xui:permissions），先加载后播种会永久冻结为 none。
 */
export async function seedE2ePermissions(
  request: APIRequestContext,
  permissions: string[],
  warmupPages: string[] = [],
  username = 'nop',
): Promise<void> {
  await rpc(request, 'NopAuthSite__save', {
    data: { siteId: 'main', displayName: 'Main', orderNo: 0, status: 1 },
  });

  await rpc(request, 'NopAuthRole__save', {
    data: { roleId: E2E_ROLE_ID, roleName: 'E2E Admin', isPrimary: 0 },
  });

  // TOPM 根节点行必须先建：SUBM.parentId 的外键校验要求它存在
  await rpc(request, 'NopAuthResource__save', {
    data: {
      resourceId: 'test-orm-nop-job', resourceType: 'TOPM', siteId: 'main',
      displayName: '测试nop-job', orderNo: 10000, status: 1,
    },
  });

  for (const perm of permissions) {
    const entityId = perm.split(':')[0];
    // NopAuthResource.parentId / NopAuthRoleResource.resourceId 都有 ORM 外键校验（查 DB 行），
    // 且 newSiteResource 会用 DB 行无条件覆盖静态 entry 的 parentId——所以 DB 行必须带与
    // 静态树一致的 parent 链（TOPM → SUBM → FNPT），否则父子断裂、权限映射清空
    await rpc(request, 'NopAuthResource__save', {
      data: {
        resourceId: `${entityId}-main`, resourceType: 'SUBM', siteId: 'main',
        parentId: 'test-orm-nop-job', displayName: entityId, orderNo: 0, status: 1,
      },
    });
    const resourceId = `FNPT:${perm}`;
    await rpc(request, 'NopAuthResource__save', {
      data: {
        resourceId, resourceType: 'FNPT', siteId: 'main',
        parentId: `${entityId}-main`, displayName: perm, orderNo: 0, status: 1,
      },
    });
    await rpc(request, 'NopAuthRoleResource__save', {
      data: { roleId: E2E_ROLE_ID, resourceId },
    });
  }

  const userResp = await rpc<{ items: { userId: string }[] }>(request, 'NopAuthUser__findPage', {
    query: { offset: 0, limit: 1, filter: { $type: 'eq', name: 'userName', value: username } },
  });
  const userId = userResp.data?.items?.[0]?.userId;
  if (userId) {
    await rpc(request, 'NopAuthUserRole__save', { data: { userId, roleId: E2E_ROLE_ID } });
  }

  // 等 site-map 缓存（cache-timeout=1s）过期，并主动触发一次重建，
  // 避免 PageProvider 权限转换撞上重建前后各半的快照
  await new Promise((r) => setTimeout(r, 1500));
  await rpc(request, 'SiteMapApi__getSiteMap', { includeFunctionPoints: true });

  // 预热页面：PageProvider 权限转换按首次调用的授权快照原地把 xui:roles 写进缓存模型，
  // 先用已授权状态冻结，后续浏览器加载（含缓存撞期）都复用这份正确结果
  for (const path of warmupPages) {
    await rpc(request, 'PageProvider__getPage', { path });
  }
}

/** nop-job.action-auth.xml 中 NopJobSchedule 的全部功能点权限 */
export const SCHEDULE_PERMISSIONS = [
  'NopJobSchedule:query',
  'NopJobSchedule:mutation',
  'NopJobSchedule:triggerNow',
  'NopJobSchedule:enableSchedule',
  'NopJobSchedule:disableSchedule',
  'NopJobSchedule:pauseSchedule',
  'NopJobSchedule:resumeSchedule',
  'NopJobSchedule:archiveSchedule',
];
