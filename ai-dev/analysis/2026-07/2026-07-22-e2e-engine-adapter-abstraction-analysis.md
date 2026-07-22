# E2E PageObject Abstraction 分析: EngineAdapter 屏蔽 Flux/AMIS 差异的现状与问题

> Status: open
> Date: 2026-07-22
> Scope: `nop-entropy-e2e/packages/e2e-shared/src/`, `nop-entropy-e2e/packages/nop-auth-e2e/tests/`
> Conclusion: 当前架构设计方向正确但实现不完整，大量 AMIS 细节泄漏到 PageObject 和 spec 层，无法在不修改 PO 和测试代码的前提下切换 Flux

## Context

- E2E 测试使用 Playwright，被测界面有 AMIS 和 Flux 两套 UI 框架
- 设计目标：`EngineAdapter` 接口屏蔽框架差异，PageObject 和 spec 不应出现框架特定选择器
- 通过 `E2E_ENGINE=flux` 环境变量切换适配器
- 问题：实际代码中大量 AMIS 特定 CSS 类名泄漏到 PO 和 spec 层

## 现状分析

### 1. EngineAdapter 接口定义（types.ts）

```
Methods: crudContainer, table, rows, cellValue, addButton, queryButton,
rowAction, dialog, drawer, formField, submitButton, selectOption, dateInputByLabel
```

**缺失的关键抽象：**

| 缺失方法 | 后果 |
|----------|------|
| `searchField(page, fieldName)` | 每个 PO 手写 `input[name^="filter_xxx"]` |
| `executeSearch(page)` / `searchButton(page)` | 每个 PO 手写 `.cxd-Table-searchableForm button[type="submit"]` |
| `refreshButton(page)` | 每个 PO 手写 `[class*="fa-sync"]` |
| `confirmDialog(page)` | `CrudListPage.deleteRow` 自己实现确认逻辑 |
| `checkboxField(dialog, fieldName)` | `setCheckbox` 在每个 PO 中重复实现 |
| `staticField(dialog, fieldName)` | `FormDialog.getField` 有 AMIS 硬编码 fallback |

### 2. 泄漏分布总览

```
泄漏类型                          文件                             行引用
─────────────────────────────────────────────────────────────────────
AMIS selector in PO search       user.po.ts:45-51                 input[name^="filter_"], .cxd-Table-searchableForm
AMIS selector in PO search       role.po.ts:31-36                 同上
AMIS selector in PO search       resource.po.ts:36-40             input[name^="filter_siteId"]
AMIS overlay in PO               user.po.ts:38-41                 .cxd-Modal-overlay
AMIS refresh icon in PO          user.po.ts:61-63                 [class*="fa-sync"]
AMIS refresh icon in PO          resource.po.ts:42                [class*="fa-sync"]
AMIS refresh icon in PO          role.po.ts:47-49                 [class*="fa-sync"]
AMIS form fallback in FormDialog FormDialog.ts:38-50              .cxd-Form-static, .cxd-PlainField
AMIS selector in spec            auth-role.spec.ts:166            page.locator('tr')...filter({ hasText...})
AMIS selector in spec            auth-role.spec.ts:226            page.locator('tr')...filter({ hasText...})
AMIS selector in spec            auth-role.spec.ts:244-247        同上
AMIS selector in deleteRow       CrudListPage.ts:54               [role="alertdialog"], waitForTimeout(500)
完全脱离架构的 diag 测试        diag-*.spec.ts (11 files)        .cxd-Modal, .cxd-DropDown-menuItem 等
```

### 3. 未实现 FluxAdaper 的方法

从 `AmisAdapter` 看，以下方法 Flux 完全缺失对应的公共接口（注意 FluxAdapter 内部实现了 `confirmDialog` 和 `datePickerSelect`，但它们不在 `EngineAdapter` 接口中，也无法被 PO 调用）：

- `datePickerSelect` — FluxAdapter 有，但接口中没有
- `confirmDialog` — FluxAdapter 有，但接口中没有
- `alertDialog` — FluxAdapter 有，但接口中没有

### 4. 浏览器 spec 中的直接选择器泄漏

`auth-role.spec.ts` 中的浏览器测试直接使用：
```typescript
await expect(page.locator('tr').filter({ hasText: roleId }).first()).toBeVisible({ timeout: 10_000 });
```

而不是通过 PO 方法如 `rolePO.assertRoleExists(roleId)` 来验证。这里 `page.locator('tr')` 在 Flux 下可能完全不可用（Flux 使用 `tbody tr[data-slot="table-row"]`）。

### 5. 重复的模式

每个 PO 都重复以下模板代码：

```
searchXxx():
  wait for overlay
  find filter input by input[name^="filter_xxx"]
  fill
  click search or refresh
  waitForTimeout(1500)

clickView/clickEdit/clickDelete/clickSave/readViewField:
  几乎逐字复制

setCheckbox():
  role.po.ts 和 resource.po.ts 完全相同的实现
```

### 6. 架构图（当前状态 vs 理想状态）

**当前实际：**
```
Spec
 ├─ engine-dependent (page.locator('tr') in auth-role.spec.ts)
 ├─ engine-agnostic RPC (clean)
 └─ delegates to ...
      PO
       ├─ searchUser() — AMIS-specific selectors
       ├─ setCheckbox() — AMIS-specific
       └─ FormDialog / CrudListPage
            ├─ getField() — has AMIS fallback
            └─ deleteRow() — hardcoded confirm dialog logic
```

**理想状态：**
```
Spec — 只调用 PO 方法，不出现任何 CSS 选择器
  └─ PO
       └─ CrudListPage / FormDialog
            └─ EngineAdapter
                 ├─ AmisAdapter
                 └─ FluxAdapter
```

## 根因分析

1. **快速交付 vs 架构纪律的冲突**：`searchUser` 等方法最初为了尽快让测试跑通，直接在 PO 里写了 AMIS 选择器，没有先补全 `EngineAdapter` 接口
2. **EngineAdapter 接口演化不完整**：`addButton` 一开始在接口里，但 `searchButton`、`refreshButton` 等是后续才发现的通用模式，没有及时补入接口
3. **diag 测试是纯调试工具**：`diag-*.spec.ts` 是为了查看 AMIS DOM 结构而写的 ad-hoc 脚本，被提交到版本库但没有 PR review 清理
4. **缺少 Flux 的验证环境**：当前所有测试只在 AMIS 上跑过（E2E_ENGINE 默认 'amis'），没有实际用 Flux 运行过，所以泄漏没有被发现
5. **PO 方法命名不规范**：`UserPO` 叫 `searchUser`，`RolePO` 叫 `searchRole`，`ResourcePO` 叫 `searchBySite` — 应统一为 `search(field, value)` 并通过 config 配置默认搜索字段

## 改进建议

### P0: 补全 EngineAdapter 接口（阻断泄漏根源）

在 `EngineAdapter` 接口中新增：

```typescript
// 搜索相关
searchField(page: Page, fieldName: string): Locator;
searchButton(page: Page): Locator;
executeSearch(page: Page): Promise<void>;

// 刷新
refreshButton(page: Page): Locator;

// 对话框
confirmDialogAction(dialog: Locator): Locator;  // 获取确认按钮
checkboxField(dialog: Locator, fieldName: string): Locator;

// 静态字段读取
staticFieldValue(dialog: Locator, fieldName: string): Promise<string>;

// 行查找
findRowByText(page: Page, text: string): Locator | null;
```

### P1: 抽取通用方法到 CrudListPage

- 将 `searchUser`、`searchRole`、`searchBySite` 统一为 `CrudListPage.search(fieldName, value)`
- 将 `setCheckbox` 提到 `CrudListPage` 或 `FormDialog`
- 将 `clickView`、`clickEdit`、`clickDelete`、`clickSave`、`readViewField` 等重复模式提到 `CrudListPage`（当前只有 `deleteRow` 在基类，view/edit/save 在各 PO 重复）

### P2: 清理 spec 层的直接选择器

- `auth-role.spec.ts` 中 `page.locator('tr')...` 替换为 `rolePO.assertRoleExists(roleId)` 等方法
- 创建统一的 `assertEntityExists(text)` / `assertEntityNotExists(text)` 在 `CrudListPage`

### P3: 处理 diag 测试

- 将 `diag-*.spec.ts` 移动到 `_tmp/` 或 `debug/` 子目录并配置 Playwright 排除
- 或者统一标记为 `test.skip` 并添加注释说明用途

### P4: 补充 FluxAdapter 缺失实现

- 为 `FluxAdapter` 补充 `EngineAdapter` 接口中定义的所有方法（如有缺失）
- 在 CI 中增加 `E2E_ENGINE=flux` 的测试运行（至少对核心 CRUD 流程）

## 迁移路径评估

| 方案 | 工作量 | 风险 | 效果 |
|------|--------|------|------|
| A. 仅补全接口 + 修改现有 PO | 中（~2 天） | 低 | 高 |
| B. A + 抽取通用方法到基类 | 中（~3 天） | 中 | 更高 |
| C. B + 清理 spec 直接选择器 | 中-高（~4 天） | 低 | 最高 |
| D. C + Flux CI 验证 | 高（~5 天 + infra） | 中 | 理论全覆盖 |

推荐路径：B（当前泄漏严重，基类抽取是投资回报最高的）

## Open Questions

- [ ] Flux 界面在实际开发中的活跃程度如何？是否需要完整的 E2E 覆盖？
- [ ] `diag-*.spec.ts` 是否还有保留价值？还是应直接删除？
- [ ] `CrudListPage` 的 `findRowByField` 中 `> td:nth-child(2)` fallback 是否也是 AMIS 泄漏？

## References

- `nop-entropy-e2e/packages/e2e-shared/src/types.ts` — EngineAdapter 接口定义
- `nop-entropy-e2e/packages/e2e-shared/src/AmisAdapter.ts` — AMIS 实现
- `nop-entropy-e2e/packages/e2e-shared/src/FluxAdapter.ts` — Flux 实现
- `nop-entropy-e2e/packages/e2e-shared/src/CrudListPage.ts` — CRUD 基类
- `nop-entropy-e2e/packages/e2e-shared/src/FormDialog.ts` — 表单对话框
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/auth-role.spec.ts` — 泄漏最多的 spec
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/auth-user.spec.ts` — 相对更干净的 spec
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/page-objects/user.po.ts` — 含有 AMIS 泄漏的 PO
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/page-objects/role.po.ts` — 同上
- `nop-entropy-e2e/packages/nop-auth-e2e/tests/page-objects/resource.po.ts` — 同上
- `nop-entropy-e2e/README.md` — README 文档（与代码实际已偏离）
