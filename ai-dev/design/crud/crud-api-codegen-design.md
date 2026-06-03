# CRUD API 代码生成设计

**日期**：2026-06-03（更新于 2026-06-03）
**范围**：`nop-api-core`（通用接口）、`nop-codegen`（模板）、`*-meta`（Bean + 接口生成）
**状态**：草案

---

## 一、设计结论

1. 在 `nop-api-core` 中将现有 `ICrudApi<T>` 演进为 `ICrudApi<I, O>`，并新增 `ITreeApi<O>`，分别承载标准 CRUD 和树形操作的远程调用签名。
2. **Bean 命名遵循项目 `*Bean` 约定**：输入为 `{EntityName}InputBean`，输出为 `{EntityName}OutputBean`。项目中 `*Input`/`*Output` 已被基础设施层占用（`IRecordInput`、`IEvalOutput` 等），使用 `*Bean` 后缀与现有 codegen 模式一致（`WfStartRequestBean`、`RuleResultBean`），避免命名冲突。
3. **返回类型不使用实体类**。泛型参数 `O` 代表基于实体名衍生的输出对象（如 `NopAuthUserOutputBean`），由 `*-meta` 根据 xmeta 的 `published` props 生成。
4. **输入使用强类型**。泛型参数 `I` 代表基于实体名衍生的输入对象（如 `NopAuthUserInputBean`），由 `*-meta` 根据 xmeta 的 `insertable` / `updatable` props 生成。
5. **`CrudBizModel` 不实现 `ICrudApi`**。API 接口是纯客户端 CRUD 契约，框架的通用桥接机制负责将 API 调用路由到 `CrudBizModel` 的对应方法。因此 API 接口的输入类型可以独立演进为强类型 `I`，而不要求 `CrudBizModel` 本身改为直接接收 Bean。
6. **生成触发点在 `*-meta`**。因为 meta 修改会直接影响 InputBean/OutputBean 的字段列表（`insertable`、`updatable`、`published` 等属性变化时 Bean 需要重新生成）。
7. **树形操作独立为 `ITreeApi<O>`**，不复用 `ICrudApi`。不是所有实体都有树形结构，按需继承。

## 二、背景与动机

### 当前问题

Nop 平台的 CRUD 服务（基于 `CrudBizModel<T>`）通过通用 REST adapter 暴露 GraphQL/RPC 能力：

```text
/r/{bizObj}__{method}
```

这种模式在以下场景存在不足：

1. **外部系统跨模块 RPC 调用缺乏强类型契约**：调用方只能通过字符串拼接 operation name，没有编译期检查。
2. **与自定义 API 接口的调用方式不统一**：自定义业务方法（如 `WorkflowService`）有强类型 API 接口，而 CRUD 方法没有。
3. **缺少独立的 API 模块契约**：`ICrudBiz<T>` 在 `nop-orm` 中，依赖 ORM 层，不适合被外部系统引用。
4. **无法在 `*-api/` 模块中提供实体相关的强类型 DTO**：实体字段定义分散在 ORM 模型和 xmeta 中，外部系统无法获得类型安全的输入/输出结构。

### 目标

让 CRUD 服务拥有与自定义 API 接口一致的强类型 RPC 契约，外部系统只需依赖 `*-api/` 模块即可获得：
- 类型安全的 CRUD 调用能力
- 强类型的输入/输出 Bean
- 与自定义 API 接口统一的调用模式

### 不改变什么

- `CrudBizModel<T>` 的内部实现不变，不需要实现 `ICrudApi`
- `ICrudBiz<T>` 的接口定义不变
- 通用 REST adapter 继续工作
- 前端 AMIS 页面继续走通用 adapter，不依赖新接口
- 桥接由框架通用机制完成，不需要额外的桥接代码

## 三、核心设计

### 3.1 类型参数设计

`ICrudApi` 和 `ITreeApi` 使用独立的类型参数，不引用实体类：

```text
ICrudApi<I, O>
  I = 输入类型（如 NopAuthUserInputBean）
  O = 输出类型（如 NopAuthUserOutputBean）

ITreeApi<O>
  O = 输出类型（与 ICrudApi 共享同一个 O）
```

具体接口生成时绑定：

```java
@BizModel("NopAuthUser")
public interface NopAuthUserApi extends _NopAuthUserApi {
}
```

### 3.2 `ICrudApi<I, O>` — 通用泛型 CRUD API 接口

在 `nop-api-core` 中定义。

#### 方法集

| 方法 | 类型 | 输入 | 返回 |
|------|------|------|------|
| `get` | query | `String id` | `O` |
| `findPage` | query | `QueryBean query` | `PageBean<O>` |
| `findList` | query | `QueryBean query` | `List<O>` |
| `findFirst` | query | `QueryBean query` | `O` |
| `findCount` | query | `QueryBean query` | `long` |
| `save` | mutation | `I input` | `O` |
| `update` | mutation | `I input` | `O` |
| `delete` | mutation | `String id` | `boolean` |
| `saveOrUpdate` | mutation | `I input` | `O` |
| `batchDelete` | mutation | `Set<String> ids` | `Set<String>` |
| `batchGet` | query | `Collection<String> ids` | `List<O>` |

#### 设计约束

- `ICrudApi<I, O>` 只依赖 `nop-api-core`，不依赖 ORM 层
- 复用现有 `ICancelToken` + `FieldSelectionBean` 风格，而不是重新引入 `ApiRequest/ApiResponse` 包装
- 保留现有高级 CRUD 方法（如 `copyForNew`、`batchUpdate`、`deleted_*`）在 `ICrudApi` 中，核心变化是把输出类型改为 `O`，并将 `save/update/saveOrUpdate` 的输入改为强类型 `I`
- 框架自动将方法调用桥接到 `CrudBizModel` 的同名方法

### 3.3 `ITreeApi<O>` — 独立的树形操作接口

树形操作返回 `StdTreeEntity`（轻量级树结构，只有 id/displayName/parentId/level）或完整输出对象，与平铺 CRUD 的方法集有明显差异，应独立为单独接口。

#### 方法集

| 方法 | 类型 | 返回 | 说明 |
|------|------|------|------|
| `findRoots` | query | `List<O>` | 返回根节点（完整输出对象） |
| `findTreeEntityPage` | query | `PageBean<StdTreeEntity>` | 返回轻量级树节点（分页） |
| `findTreeEntityList` | query | `List<StdTreeEntity>` | 返回轻量级树节点（列表） |

需要树形操作的实体接口同时继承 `ITreeApi<O>`。不具备树形结构的实体不继承。

### 3.4 InputBean 生成

为每个实体生成 `{EntityName}InputBean`，继承通用基类 `CrudInputBase`。

#### 基类 `CrudInputBase`

在 `nop-api-core` 中提供一个通用基类，包含所有 InputBean 共享的基础设施：

```java
@DataBean
public abstract class CrudInputBase {
    private String _chgType;

    private Map<String, Object> _extAttrs;

    @JsonAnyGetter
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, Object> get_extAttrs() {
        return _extAttrs;
    }

    @JsonAnySetter
    public void set_extAttr(String name, Object value) {
        if (_extAttrs == null)
            _extAttrs = new LinkedHashMap<>();
        _extAttrs.put(name, value);
    }

    // _chgType getter/setter
}
```

**设计要点**：

1. **`_chgType` 字段**：固定生成在基类中，用于 `batchModify`、`saveOrUpdate` 以及 to-many 集合项的增删改控制（值：`"A"` 新增、`"U"` 修改、`"D"` 删除）。

2. **`@JsonAnySetter` / `@JsonAnyGetter` 机制**：吸收所有未在 Bean 中显式声明的 JSON 属性到 `_extAttrs` Map 中。这统一处理了 `_chgType_{propName}`（关系操作过滤）、`_writeMode_{propName}`（关系写入模式覆盖）以及未来可能增加的控制字段，无需逐一声明。

3. **桥接时的转换**：`OrmEntityCopier` 中 `_chgType` / `_writeMode_*` 只从 `Map<String, Object>` 读取，不从 Bean 属性读取。桥接层将 InputBean 转为 Map 时，`@JsonAnyGetter` 确保 `_extAttrs` 中的控制字段被正确展开到 Map 中。

#### 实体字段（xmeta 生成）

来自 xmeta 中满足以下条件的 props：

- `insertable == true` **或** `updatable == true`

两个字段集的并集，因为 `save` 和 `update` 共用同一个 InputBean。框架桥接时负责根据操作类型（save vs update）应用对应的字段过滤（`insertable` vs `updatable`）。

每个实体字段包含：
- Java 类型来自 prop 的 `<schema type>`
- `@PropMeta(propId = N)` 保持稳定序号
- `mandatory` 属性来自 prop 的 `mandatory`

#### 关系字段

对 xmeta 中 `insertable == true` 或 `updatable == true` 的关系 prop，根据 `ext:kind` 确定类型：

| 关系类型 | InputBean 中的 Java 类型 | 说明 |
|---------|-------------------------|------|
| to-one | `_{RelatedEntityName}InputBean` | 生成基类层直接引用同目录 `_gen` 中的关联 InputBean，避免生成层反向依赖保留层 |
| to-many（子表） | `List<_{RelatedEntityName}InputBean>` | 每个子表项使用关联对象的 InputBean 基类，包含 `id`、`_chgType` 等控制字段 |

**为什么 to-one 使用关联 InputBean**：

1. 同一 `*-api/` 模块内的所有生成基类都位于 `beans._gen`，不存在生成层反向依赖保留层的问题。
2. 调用方获得 IDE 自动补全——知道关联对象可以提交哪些字段。
3. `OrmEntityCopier.copyRefEntity()` 的 inline 模式处理嵌套 Map 时，桥接层将 InputBean 序列化为 Map 即可正确工作。
4. 仅链接 id 的场景：只设置关联 InputBean 的 `id` 字段，其他字段为 null。

**为什么 to-many 也使用关联 InputBean**：

1. 每个集合项的 `_chgType`（A/U/D）由 `CrudInputBase` 基类提供。
2. 比裸 `List<Map>` 提供更好的类型约束和 IDE 支持。
3. 嵌套关系通过 `@JsonAnySetter` 机制处理（更深层的关系控制字段会被吸收到 `_extAttrs`）。

#### 命名与位置

```
{appName}-api/src/main/java/{apiPackage}/beans/
  ├── _gen/
  │   └── _{EntityName}InputBean.java    // 强制覆盖，生成字段
  └── {EntityName}InputBean.java          // 保留文件，用户扩展
```

#### 生成规则

```
_{EntityName}InputBean extends CrudInputBase {
  xmeta props 中 (insertable || updatable) == true 的字段（含关系字段）
  to-one 关系 → _{RelatedEntityName}InputBean
  to-many 关系 → List<_{RelatedEntityName}InputBean>
}
```

### 3.5 OutputBean 生成

为每个实体生成 `{EntityName}OutputBean`，字段来自 xmeta 中满足以下条件的 props：

- `published != false`（默认 `published == true`）

`published == false` 的字段（如 `password`、`salt`）不应出现在输出中。

#### 关系字段的类型映射

基于 `CrudBizModel` 的实际返回行为确定：

| 关系类型 | OutputBean 中的 Java 类型 | 说明 |
|---------|-------------------------|------|
| to-one（实体引用） | `Map<String, Object>` | 实体的 to-one 字段返回的是 ORM 实体代理（`IOrmEntity`），API 层无法引用实体类。GraphQL 引擎按 `FieldSelectionBean` 选择字段后序列化为 Map |
| to-many（集合） | `List<Map<String, Object>>` | 集合字段返回的是 `IOrmEntitySet`，序列化后为 Map 列表 |

**为什么不用 `{RelatedEntityName}OutputBean`**：不同实体在同一模块中，引用其他实体的 OutputBean 会产生循环依赖问题（A 的 OutputBean 引用 B 的，B 的又可能引用 A 的）。使用 `Map<String, Object>` 避免 API 模块内部的循环依赖。调用方如果需要强类型反序列化，可以自行将 Map 转为对应 OutputBean。

**为什么不用实体类**：

1. **API 模块不依赖 ORM 层**：`*-api/` 只依赖 `nop-api-core`，不引用实体类所在的 `*-dao/`。
2. **字段可见性控制**：实体类包含所有数据库列（包括 `password`、`salt` 等敏感字段），而 OutputBean 只暴露 `published != false` 的字段。
3. **解耦**：外部系统不应感知 ORM 实体结构，API 契约应独立于持久化模型。
4. **关系字段类型**：实体的关系字段类型是 `IOrmEntitySet` 等 ORM 类型，API 层无法直接使用。

#### 命名与位置

```
{appName}-api/src/main/java/{apiPackage}/beans/
  ├── _gen/
  │   └── _{EntityName}OutputBean.java    // 强制覆盖，生成字段
  └── {EntityName}OutputBean.java          // 保留文件，用户扩展
```

#### 生成规则

```
_{EntityName}OutputBean 的字段 = xmeta props 中 published != false 的字段
```

每个字段包含：
- Java 类型来自 prop 的 `<schema type>`
- `@PropMeta(propId = N)` 保持稳定序号
- to-one 关系字段类型为 `Map<String, Object>`
- to-many 关系字段类型为 `List<Map<String, Object>>`

### 3.6 具体 API 接口生成

#### 生成位置

放在 `*-api/` 模块中，包路径 `{apiPackageName}.crud`。

#### 生成内容

对每个实体（`notGenCode = false`），生成：

```java
// 强制覆盖：_{EntityName}Api.java
public interface _{EntityName}Api
    extends ICrudApi<_{EntityName}InputBean, _{EntityName}OutputBean> {
}

// 保留文件（仅首次生成）：{EntityName}Api.java
@BizModel("{entityName}")
public interface {EntityName}Api extends _{EntityName}Api {
}
```

如果实体具有树形结构（xmeta 中定义了 `parentProp`），同时继承 `ITreeApi`：

```java
public interface _{EntityName}Api
    extends ICrudApi<_{EntityName}InputBean, _{EntityName}OutputBean>,
    ITreeApi<_{EntityName}OutputBean> {
}
```

### 3.7 生成触发点：`*-meta`

**决定在 `*-meta` 模块的生成阶段触发**，具体挂在 `precompile/gen-meta.xgen` 或新增独立的 `precompile/gen-crud-api.xgen`。

原因：

1. **InputBean/OutputBean 的字段来自 xmeta**。当 meta 修改（如字段增减、`insertable`/`published` 变化）时，Bean 必须重新生成。如果生成点在 `*-codegen/postcompile`，meta 修改后需要先跑完 meta 再跑 codegen，链路更长。
2. **`*-meta` 已经读取 ORM 模型**。`gen-meta.xgen` 已经解析了所有实体信息，直接可用。
3. **输出到 `*-api/`** 通过模板的路径变量控制，生成器可以将文件输出到任意模块目录。

### 3.8 桥接机制

`CrudBizModel` 不实现 `ICrudApi`。框架的通用 CRUD 桥接机制处理 API 接口到 BizModel 的路由：

```text
外部调用方
  → {EntityName}Api (ICrudApi<I, O>)
  → [框架拆包：将 I (InputBean) 转为 Map，构建 FieldSelectionBean、IServiceContext]
  → CrudBizModel 的对应方法
  → [框架装包：将返回实体转为 O (OutputBean)]
  → O / PageBean<O> / List<O>
```

关键转换：
- **InputBean → Map**：将 InputBean 通过 Jackson 序列化为 `Map<String, Object>`，`@JsonAnyGetter` 确保 `_extAttrs` 中的 `_chgType_*`、`_writeMode_*` 等控制字段被正确展开。传递给 `CrudBizModel.save(data, ctx)` 等方法。
- **Entity → OutputBean**：将 `CrudBizModel` 返回的实体对象转换为 OutputBean，只保留 `published != false` 的字段。

这些转换由框架通用机制完成（类似现有自定义 API 接口的 `ApiRequest` 拆包），不需要为每个实体生成桥接代码。

### 3.9 与已有接口的关系

| | `ICrudApi<I, O>`（新增） | `I*Biz extends ICrudBiz<T>`（现有） | `{ServiceName}.java`（现有） |
|---|---|---|---|
| **定位** | 外部 CRUD RPC 调用 | 模块内部 BizModel 间调用 | 外部自定义业务 RPC 调用 |
| **位置** | `*-api/.../crud/` | `*-dao/.../biz/` | `*-api/` |
| **方法签名** | `FieldSelectionBean` + `ICancelToken` + 强类型 I/O | 裸参数 + `IServiceContext` | `ApiRequest/ApiResponse` + Message Bean |
| **依赖** | 仅 `nop-api-core` | `nop-orm`、`nop-biz` | 仅 `nop-api-core` + Message Bean |
| **BizModel 实现** | 不实现，框架桥接 | BizModel 直接实现 | 不实现，框架桥接 |

三者互不替代，服务不同场景。

## 四、拒绝了什么

### 方案 A：返回类型使用实体类

`ICrudApi<I, T>` 的 `T` 直接使用 ORM 实体类。

**拒绝理由**：
- `*-api/` 模块不能依赖 `*-dao/`（循环依赖，且违反分层原则）
- 实体类包含所有数据库列，无法控制字段可见性
- 实体的关系字段使用 ORM 类型（`IOrmEntitySet`），外部系统无法理解
- 外部系统不应感知持久化模型

### 方案 B：输入使用 `Map<String, Object>`

`ICrudApi` 的 `save`/`update` 方法接收 `Map<String, Object>`。

**拒绝理由**：
- 失去编译期类型检查，外部调用方无法知道可提交哪些字段
- 与返回值的强类型设计不对称
- `CrudBizModel` 不实现 `ICrudApi`，API 接口的参数格式完全可以独立设计
- 强类型输入让 IDE 可以提供自动补全和重构支持

### 方案 C：为每个实体生成独立的完整 API 接口（不用泛型基接口）

每个实体生成包含所有 CRUD 方法签名的独立接口。

**拒绝理由**：
- 大量重复代码
- 修改 CRUD API 契约时需要更新所有实体接口
- 无法在调用方统一接收 `ICrudApi<I, O>` 类型的引用

### 方案 D：树形操作放在 `ICrudApi` 中

将 `findRoots`、`findTreeEntityPage` 等方法放在 `ICrudApi<I, O>` 中。

**拒绝理由**：
- 不是所有实体都有树形结构，强制实现树形方法违反接口隔离原则
- `StdTreeEntity` 的返回类型与 `O` 无关，参数和返回结构与 CRUD 操作差异明显
- 独立的 `ITreeApi` 允许按需继承

### 方案 E：生成触发点放在 `*-codegen`

**拒绝理由**：
- meta 修改字段属性时 Bean 必须重新生成，如果在 `*-codegen` 触发则链路更长
- `*-meta` 已经有完整的实体信息，是最自然的触发点

### 方案 F：Bean 命名为 `{EntityName}Input` / `{EntityName}Output`（无 Bean 后缀）

**拒绝理由**：
- 项目中 `*Input`/`*Output` 已被基础设施层占用（`IRecordInput`、`IRecordOutput`、`IShellInput`、`IShellOutput`、`IEvalOutput` 等），语义为流/记录处理接口
- 作为 API DTO，应使用 `*Bean` 后缀与项目主导命名模式保持一致
- `*Bean` 后缀明确标识这是可跨系统序列化传输的数据对象

### 方案 G：Bean 命名为 `{EntityName}Vo` / `{EntityName}Dto`

**拒绝理由**：
- `*Vo` 在项目中零使用，引入新后缀增加认知成本
- `*Dto` 仅在 `nop-code-service` 一个模块中使用（33 个文件），不是项目标准模式
- 代码生成应遵循项目中最广泛使用的 `*Bean` 模式

## 五、生成物清单

对每个 ORM 实体（`notGenCode = false`），在 `*-meta` 生成阶段输出到 `*-api/`：

| 生成文件 | 覆盖策略 | 说明 |
|---------|---------|------|
| `{apiPackage}/crud/_{EntityName}Api.java` | 强制覆盖 | 生成的 API 基接口 |
| `{apiPackage}/crud/{EntityName}Api.java` | 保留 | 用户可扩展的具体接口 |
| `{apiPackage}/beans/_gen/_{EntityName}InputBean.java` | 强制覆盖 | InputBean 基类（`insertable \|\| updatable` 字段） |
| `{apiPackage}/beans/{EntityName}InputBean.java` | 保留 | 用户可扩展的 InputBean |
| `{apiPackage}/beans/_gen/_{EntityName}OutputBean.java` | 强制覆盖 | OutputBean 基类（`published != false` 字段） |
| `{apiPackage}/beans/{EntityName}OutputBean.java` | 保留 | 用户可扩展的 OutputBean |

## 六、实施路径

### Phase 1：接口定义 + InputBean/OutputBean 生成

1. 在 `nop-api-core` 中定义 `ICrudApi<I, O>` 和 `ITreeApi<O>` 接口
2. 在 `nop-codegen` 中增加 InputBean/OutputBean 和 API 接口的生成模板
3. 在 `*-meta/precompile/gen-meta.xgen`（或新增 `gen-crud-api.xgen`）中集成模板
4. 对 `nop-auth` 模块验证生成结果

### Phase 2：桥接适配

1. 确认框架的通用 RPC 桥接能否处理 `I → Map` 和 `Entity → O` 的转换
2. 如需要，在桥接层增加 InputBean → Map 和 Entity → OutputBean 的通用转换器

### Phase 3（可选）：GraphQL Schema 增强

1. 基于 `ICrudApi<I, O>` 和 xmeta 自动生成更精确的 GraphQL schema
2. 提供比通用 adapter 更丰富的类型定义

## 七、与已有设计的关系

- `ai-dev/design/crud/relation-write-mode-design.md` — CRUD 写入模式设计，与本设计互补
- `docs-for-ai/02-core-guides/api-model-and-codegen.md` — API 模型代码生成文档，CRUD API 生成是该文档的扩展
- `docs-for-ai/02-core-guides/model-first-development.md` — 模型优先开发流程，CRUD API 生成是生成链的新环节
- `docs-for-ai/03-runbooks/debug-codegen-and-generated-files.md` — 生成链调试手册

## 八、开放问题

1. **OutputBean 是否包含 `@BizLoader` 计算字段**：xmeta 中的 virtual/lazy 字段是否生成到 OutputBean？当前设计中 `published != false` 的 virtual 字段会被包含，但如果 `published` 默认为 `true`，所有 `@BizLoader` 字段都会自动进入 OutputBean——这是否合理？
2. **OutputBean 的关系字段是否也使用 `*OutputBean`**：当前设计 OutputBean 中关系字段类型为 `Map<String, Object>`，避免循环依赖。但 InputBean 已解决了同模块内的类型引用问题，OutputBean 是否也可行？需要验证两个实体互引 OutputBean 时是否产生编译期循环依赖。
3. **`CrudInputBase` 是否也需要输出对应的 `CrudOutputBase`**：如果 OutputBean 也需要 `@JsonAnyGetter`/`@JsonAnySetter` 来吸收运行时动态字段，可以提供类似的基类。
