# Relation Prop WriteMode 设计

> Status: draft  
> Date: 2026-04-19  
> Scope: `nop-biz` CrudBizModel / OrmEntityCopier / ObjMetaBasedValidator  
> Affects: `IObjPropMeta`, `OrmEntityCopier`, `CrudToolProvider`

---

## 一、问题背景

在 low-code relation write 场景中，前端提交的 relation payload 按业务语义分为 3 类执行路径（lane）：

| Lane | 语义 | 执行上下文 |
|------|------|-----------|
| `inline` | 随 aggregate root 内联保存 | root 的 auth / hook |
| `link` | 只操作 source↔target 的 link/unlink/replace | root 上下文，不动 target 自身 |
| `biz` | 转发到 target 自己的标准 BizModel 入口 | target 的 auth / hook / validator |

当前 `OrmEntityCopier` 对所有 relation prop 统一走 `inline` 路径（通过 `_chgType` 控制 row-level 增删改），缺少 prop-level 的 lane selection 能力。

## 二、设计原则

### 2.1 ObjMeta 的读/写对称性

类型层次：

- `IObjSchema` — 结构接口（props、className、type），是 building block
- `IObjMeta extends IObjSchema` — 在 IObjSchema 基础上增加 CRUD 元数据（primaryKey、selection、filter、keys、tree）
- `ObjMetaImpl.getRootSchema()` 返回 `this`（`ObjMetaImpl.java:80`），ObjMeta **本身就是** root schema
- CrudBizModel 全程使用 `IObjMeta`（`getThisObj().getObjMeta()`），不存在 ObjSchema 与 ObjMeta 分开使用的场景

读/写对称性：

ObjMeta 定义了领域模型对象的完整形状（所有 prop、类型、约束）。同一个 ObjMeta 在读、写两个方向上对称使用：

- **输出**（query/get）：从 ObjMeta 读取 prop 定义，通过 GraphQL selection 做切片返回
- **输入**（save/update）：向 ObjMeta 写入 prop 数据，通过 fieldSelection 控制写入范围

prop 的定义和元数据（类型、mandatory、transformIn/transformOut、insertable、updatable）在两个方向上共享。在 CRUD 语义下，prop-level 的配置和转换逻辑跨场景一致；如果不同场景需要不同行为，通过衍生字段处理。

这意味着 writeMode 作为 prop-level 的配置，天然属于 ObjMeta/IObjPropMeta 的范畴——它和 `insertable`、`updatable`、`mandatory` 是同一层面的声明。

### 2.2 Validator 与 Copier 的职责边界

```
ObjMetaBasedValidator:  JSON → JSON  (结构校验 + 类型转换，仍在 JSON 域)
OrmEntityCopier:        JSON → Entity (ORM 层写入，writeMode routing 在此执行)
```

writeMode routing 不应在 Validator 层处理——它决定的是"数据写到哪、怎么写"，是 Entity 层的事。

### 2.3 配置来源：prop 约束 + 前端参与

遵循已有 `_chgType` 模式：

| 层面 | 角色 | 例子 |
|------|------|------|
| prop meta 的 `writeMode` | 安全地板 / 约束声明 | `writeMode="biz"` 表示此 relation **必须**走 target BizModel |
| 前端 payload `_writeMode_propName` | 运行时选择 | `_writeMode_tags="link"` 表示本次只操作 link |
| **合并规则** | prop 更严时 prop 赢 | prop 声明 `biz`，前端不能降级为 `inline` |

与 `insertable` / `updatable` 语义一致——prop 声明不可逾越的约束，前端在约束范围内有选择权。

## 三、WriteMode 枚举

| 枚举值 | 含义 | 需要的能力 |
|--------|------|-----------|
| `inline` | 随 aggregate root 内联保存 | `IDaoProvider`（当前行为，默认值） |
| `link` | 只操作 FK/ref 绑定，不深入 target entity | `IDaoProvider` |
| `biz` | 转发到 target BizModel 的标准 save/update/delete | `IBizObjectManager` |

`OrmEntityCopier` 已同时持有 `IDaoProvider` 和 `IBizObjectManager`（`OrmEntityCopier.java:64-65`），无需新增依赖。

## 四、架构落点

### 4.1 为什么是 OrmEntityCopier 而不是 CrudToolProvider

- `CrudToolProvider` 是薄工厂（34 行），只创建 `OrmEntityCopier` 和 `Validator`
- `OrmEntityCopier.copyField()`（`OrmEntityCopier.java:199-236`）是实际做 prop-level 分发的位置
- `OrmEntityCopier` 已持有 `IBizObjectManager`，具备转发到 target BizModel 的能力
- `CrudToolProvider` 仍然参与：未来可在此配置 routing strategy 或创建带策略的 copier

### 4.2 为什么不是应用层

- `OrmEntityCopier` 已经是应用层和 ORM 层的桥梁，不是"底层"
- 在应用层再包一层 router 会把 routing 和 copy 拆成两个阶段，但输入输出耦合
- 应用层的 router 会积累 ad-hoc 逻辑，"后面下沉"几乎不会发生

## 五、数据流

```
前端 payload
  │
  │  _writeMode_orderItems = "link"
  │  _writeMode_address = "biz"
  │
  ▼
ObjMetaBasedValidator (JSON → JSON)
  │  不感知 writeMode，只做结构校验 + 类型转换
  │  _writeMode_* 作为透传字段保留（类似 _chgType_*）
  ▼
OrmEntityCopier.copyField()
  │
  │  resolveWriteMode(propMeta, map, propName)
  │    1. propMeta.prop_get("writeMode")  → 安全约束
  │    2. map.get("_writeMode_" + propName) → 前端选择
  │    3. mergeWriteMode(propMode, frontendMode) → prop 更严则 prop 赢
  │
  ├── inline → 现有 copyRefEntity / copyRefEntitySet 逻辑（不动）
  │
  ├── link → copyLinkOp()
  │         只操作 FK/ref 绑定
  │         不深入 target entity 属性
  │
  └── biz → forwardToTargetBizModel()
            跳过本地 copy（target 的 Validator 会完整处理）
            调用 target BizModel 的 save/update/delete action
            target 的 auth / hook / validator 全部生效
```

## 六、WriteMode 合并规则

```
mergeWriteMode(propMode, frontendMode):
  propMode       frontendMode    result
  ──────────     ────────────    ──────────
  null           null            inline (默认)
  null           inline         inline
  null           link            link
  null           biz             biz
  inline         null            inline
  inline         inline         inline
  inline         link            link (inline 不阻止降级)
  inline         biz             biz  (inline 不阻止升级)
  link           *               link  (link 约束：只能操作 link)
  biz            *               biz  (biz 约束：必须走 BizModel)
```

安全语义：
- `link` 和 `biz` 是单向约束——prop 声明后不可被前端覆盖
- `inline` 等价于"不约束"，前端可以自由选择任何 lane

## 七、各 Lane 的 OrmEntityCopier 行为

### 7.1 inline（默认，现有行为）

不改动。走现有的 `copyRefEntity` / `copyRefEntitySet` → `syncEntitySet` 逻辑。

### 7.2 link

```java
void copyLinkOp(Object fromValue, IOrmEntity target, IEntityRelationModel propModel, ...) {
    if (propModel.isToOneRelation()) {
        // 只设置 FK 引用，不递归 copy target 属性
        if (StringHelper.isEmptyObject(fromValue)) {
            target.orm_propValueByName(propModel.getName(), null);
        } else {
            // fromValue 应为 simple type（id）或仅含 id 的 map
            Object refEntity = daoProvider.dao(propModel.getRefEntityName())
                .loadEntityById(getId(fromValue, propModel.getRefEntityModel()));
            target.orm_propValueByName(propModel.getName(), refEntity);
        }
    } else {
        // toMany: link/unlink 基于 id 匹配
        IOrmEntitySet<IOrmEntity> refSet = target.orm_refEntitySet(propModel.getName());
        refSet.orm_forceLoad();
        // 只做 add/remove，不 copy 实体属性
        syncLinkSet(fromValue, refSet, propModel);
    }
}
```

### 7.3 biz

```java
void forwardToTargetBizModel(Object fromValue, IEntityRelationModel propModel,
                              IObjPropMeta propMeta, ...) {
    String refBizObjName = propMeta.getRefBizObjName();
    IBizObject targetBizObj = bizObjectManager.getBizObject(refBizObjName);

    if (propModel.isToOneRelation()) {
        // 转发到 target 的 save/update
        Map<String, Object> targetData = (Map<String, Object>) fromValue;
        String action = resolveTargetAction(targetData); // 有 id → update, 无 id → save
        targetBizObj.getAction(action).invoke(args, selection, context);
    } else {
        // toMany: 逐项转发，支持 _chgType A/U/D
        Collection<?> items = (Collection<?>) fromValue;
        for (Object item : items) {
            Map<String, Object> itemData = (Map<String, Object>) item;
            String chgType = (String) itemData.get("_chgType");
            String action = resolveTargetAction(itemData, chgType);
            targetBizObj.getAction(action).invoke(args, selection, context);
        }
    }
}
```

关键：biz lane **跳过本地 copy**，让 target 的 Validator 完整处理数据。

## 八、已知关联问题

### 8.1 fieldSelection 范围内的 mandatory 检查缺失

`ObjMetaBasedValidator._validate()`（`ObjMetaBasedValidator.java:148`）遍历 `data.entrySet()`，只校验数据中存在的字段。当 selection 声明了某 prop 但数据中缺省时，当前静默跳过。

建议区分场景：
- **save**（创建）：ObjMeta 的 `mandatory` 全量检查
- **update**（部分更新）：只校验数据中实际提交的字段
- **自定义 selection**（如 `copyForNew`）：selection 范围内的 mandatory 字段缺失应报错

此问题与 writeMode routing 正交，可独立解决。

## 九、修改清单（预估）

| 文件 | 改动 |
|------|------|
| `IObjPropMeta` / `ObjPropMetaImpl` | 无需新增 Java 方法，`writeMode` 通过 `prop_get("writeMode")` 读取（ext props 机制） |
| `OrmEntityCopier` | `copyField()` 中加入 `resolveWriteMode` 分发；新增 `copyLinkOp`、`forwardToTargetBizModel` |
| `ObjMetaBasedValidator` | `_validate()` 中 `_writeMode_*` 作为透传字段（类似 `_chgType_*` 的处理） |
| xmeta schema（可选） | 在 prop 的 xdef 中声明 `writeMode` 枚举约束，IDE 可提示 |

## 十、不在本次范围

- `_chgType` 的 row-level 语义不变，与 writeMode 正交使用
- `CascadePropMeta` / cascade delete 机制不变
- mandatory 检查的 selection-scoped 强化（独立问题）
