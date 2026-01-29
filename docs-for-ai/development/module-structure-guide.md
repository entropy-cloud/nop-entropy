# Nop 平台标准项目结构与代码生成依赖关系

> **本文档说明 Nop 平台基于模型驱动开发的标准项目结构、代码生成流程和模块依赖关系**

## 一、标准项目结构

### 1.1 完整业务模块结构

基于 XML 数据模型自动生成的标准项目包含以下子模块：

```
{appName}/
├── {appName}-codegen/      # 代码生成辅助工程
├── {appName}-dao/         # 数据库实体定义和 ORM 模型
├── {appName}-service/      # GraphQL 服务实现
├── {appName}-web/          # AMIS 页面文件及 View 模型定义
├── {appName}-app/          # 测试使用的打包工程
├── {appName}-meta/         # 元数据模型
├── {appName}-api/          # 对外暴露的接口定义（可选）
├── {appName}-delta/        # Delta 定制模块（可选）
└── model/                 # XML 数据模型文件（使用模块名）
    └── {appName}.orm.xml   # 例如：nop-auth.orm.xml
```

### 1.2 各模块说明

| 模块 | 作用 | 是否生成 | 关键内容 |
|------|------|----------|----------|
| **{appName}-codegen** | 代码生成辅助工程，根据 ORM 模型更新当前工程代码 | 是 | `precompile/` 和 `postcompile/` 目录包含 `.xgen` 代码生成脚本 |
| **{appName}-dao** | 数据库实体定义和 ORM 模型 | 是 | Entity 类、DAO 接口、ORM XML 模型文件 |
| **{appName}-service** | GraphQL 服务实现 | 是 | BizModel 服务类、业务逻辑实现 |
| **{appName}-web** | AMIS 页面文件及 View 模型定义 | 是 | XView 模型、页面配置、前端资源 |
| **{appName}-app** | 测试使用的打包工程 | 是 | 应用启动类、配置文件、依赖所有子模块 |
| **{appName}-meta** | 元数据模型，根据 ORM 模型生成 | 是 | XMeta 模型文件（.xmeta） |
| **{appName}-api** | 对外暴露的接口定义和消息定义 | 可选 | API 接口、消息定义 |
| **{appName}-delta** | Delta 定制模块，用于扩展基础产品 | 否 | 差量定制代码、模型扩展 |

### 1.3 实例：nop-auth 模块

```bash
nop-auth/
├── nop-auth-codegen/      # 代码生成模块
│   ├── postcompile/
│   │   └── gen-orm.xgen   # ORM 代码生成脚本
│   └── pom.xml
├── nop-auth-dao/         # DAO 模块
│   └── src/main/resources/_vfs/nop/auth/orm/
│       ├── app.orm.xml      # ORM 模型定义
│       └── ...
├── nop-auth-service/      # 服务模块
│   └── src/main/java/io/nop/auth/service/
│       ├── entity/         # 实体服务
│       │   └── NopAuthUserBizModel.java
│       └── ...
├── nop-auth-web/          # Web 模块
│   └── src/main/resources/_vfs/nop/auth/
│       ├── pages/         # 页面配置
│       └── model/         # View 模型
├── nop-auth-meta/         # 元数据模块
│   ├── precompile/
│   │   └── gen-meta.xgen  # 元数据生成脚本
│   ├── postcompile/
│   │   └── gen-i18n.xgen  # 国际化生成脚本
│   └── src/main/resources/_vfs/nop/auth/model/
│       └── NopAuthUser/
│           └── NopAuthUser.xmeta  # 元数据定义
├── nop-auth-app/          # 应用模块
└── model/
    └── nop-auth.orm.xml   # XML 数据模型
```

## 二、代码生成依赖关系

### 2.1 代码生成流程

Nop 平台的代码生成是一个分阶段的增量式过程，每个阶段都有特定的输入和输出：

```mermaid
flowchart TD
    A[XML 数据模型<br/>model/{appName}.orm.xml] -->|gen-orm.xgen| B[ORM XML 模型<br/>app.orm.xml]
    B -->|gen-meta.xgen| C[XMeta 元数据<br/>*.xmeta]
    C -->|gen-service.xgen| D[GraphQL 服务<br/>BizModel.java]
    C -->|gen-web.xgen| E[XView 视图模型<br/>*.view.xml]
    B -->|gen-orm-entity.xgen| F[实体类/DAO接口<br/>Entity.java]

    style A fill:#e1f5fe
    style B fill:#fff9c4
    style C fill:#f8fff9
    style D fill:#ffebee
    style E fill:#e8f5e9
    style F fill:#fce4ec
```

### 2.2 模型文件类型

| 模型类型 | 位置 | 作用 | 生成来源 |
|---------|------|------|---------|
| ORM 模型 | `{appName}-dao/src/main/resources/_vfs/nop/{module}/orm/app.orm.xml` | 定义数据库表结构、字段映射、关联关系 | 从 `model/{appName}.orm.xml` 生成 |
| XMeta 模型 | `{appName}-meta/src/main/resources/_vfs/nop/{module}/model/{EntityName}/*.xmeta` | 定义实体的元数据、字段属性、UI 显示规则、验证规则 | 从 ORM 模型生成 |
| XView 模型 | `{appName}-web/src/main/resources/_vfs/nop/{module}/pages/{EntityName}/*.view.xml` | 定义页面的视图结构、表格、表单、操作等 | 从 XMeta 模型生成 |

## 三、Maven 构建流程

### 3.1 代码生成阶段

Nop 平台使用 `exec-maven-plugin` 在 Maven 生命周期的不同阶段自动执行代码生成：

| Maven 阶段 | 代码生成阶段 | 执行的脚本 | 生成内容 |
|-----------|-------------|-----------|---------|
| `generate-sources` | precompile | gen-orm.xgen | ORM 模型、实体类、DAO 接口 |
| `generate-sources` | precompile2 | gen-meta.xgen | XMeta 元数据 |
| `compile` | aop | gen-aop.xgen | AOP 代理类 |
| `generate-test-resources` | postcompile | gen-service.xgen, gen-web.xgen, gen-i18n.xgen | GraphQL 服务、视图模型、国际化文件 |

### 3.2 核心代码生成脚本

#### 3.2.1 xxx-codegen/postcompile/gen-orm.xgen
**关键**：统一生成 dao, service, web 等所有模块的代码
```xml
<c:script>
    // 第一行：根据ORM模型生成整个项目结构（dao, service, web等模块）
    codeGenerator.withTargetDir("../").renderModel('../../model/nop-auth.orm.xml','/nop/templates/orm', '/',$scope);
    // 第二行：生成实体类到dao模块
    codeGenerator.withTargetDir("../nop-auth-dao/src/main/java").renderModel('../../nop-auth-dao/src/main/resources/_vfs/nop/auth/orm/app.orm.xml',
        '/nop/templates/orm-entity','/',$scope);
</c:script>
```

> **关键点**：`/nop/templates/orm` 模板会生成 dao, service, web 等所有模块的完整代码，包括BizModel、XView等。这是Nop平台代码生成的核心入口。

#### 3.2.2 xxx-meta/precompile/gen-meta.xgen
根据 ORM 模型生成 XMeta 元数据：
```xml
<c:script>
    codeGenerator.renderModel('/nop/{module}/orm/app.orm.xml',
        '/nop/templates/meta', '/',$scope);
</c:script>
```

#### 3.2.3 xxx-meta/postcompile/gen-i18n.xgen
生成国际化文件：
```xml
<c:script>
    codeGenerator.withTplDir('/nop/templates/i18n').execute("/",{ moduleId: "nop/auth" },$scope);
</c:script>
```

> **注意**：xxx-codegen 的 `postcompile/gen-orm.xgen` 是代码生成的核心，在执行 `mvn install` 时自动运行，生成 dao, service, web 等所有模块的代码。

## 四、跨模块代码生成机制

### 4.1 代码生成流向

Nop 平台的模型驱动开发采用跨模块的代码生成机制：

```mermaid
flowchart LR
    A[<b>xxx-codegen</b><br/>postcompile/gen-orm.xgen<br/>调用/nop/templates/orm] -->|生成| B[<b>xxx-dao</b><br/>实体类/DAO接口]
    A -->|生成| E[<b>xxx-service</b><br/>BizModel/GraphQL 服务] ⭐
    A -->|生成| F[<b>xxx-web</b><br/>XView 视图模型] ⭐
    B -->|提供 ORM 模型| C[<b>xxx-meta</b><br/>precompile/gen-meta.xgen]
    C -->|生成| D[<b>xxx-meta</b><br/>XMeta 元数据]
    C -->|postcompile| G[i18n 文件]

    style A fill:#e1f5fe
    style B fill:#fff9c4
    style C fill:#f8fff9
    style D fill:#ffebee
    style E fill:#e8f5e9
    style F fill:#fce4ec
    style G fill:#f1f8e9
```

### 4.2 关键代码生成路径

| 源模块 | 脚本位置 | 目标模块 | 生成的文件 | 执行时机 |
|--------|---------|---------|-----------|---------|
| xxx-codegen | postcompile/gen-orm.xgen | xxx-dao | Entity.java, IEntityDao, EntityDaoImpl | mvn install |
| xxx-codegen | postcompile/gen-orm.xgen | xxx-service | BizModel.java, GraphQL Schema | mvn install ⭐ |
| xxx-codegen | postcompile/gen-orm.xgen | xxx-web | XView 视图模型, 页面配置 | mvn install ⭐ |
| xxx-meta | precompile/gen-meta.xgen | xxx-meta | XMeta 元数据 | mvn install |
| xxx-meta | postcompile/gen-i18n.xgen | xxx-meta | i18n 文件 | mvn install |

**⭐ 关键**：xxx-codegen 的 `postcompile/gen-orm.xgen` 在执行 `mvn install` 时自动运行，通过 `/nop/templates/orm` 模板同时生成 dao, service, web 等所有模块的代码。

### 4.3 构建顺序

由于跨模块代码生成的存在，必须按照正确的顺序构建模块：

**依赖关系**：
```
xxx-codegen → xxx-dao → xxx-meta → xxx-service/web → xxx-app
```

**正确构建顺序**：
```bash
# 在根目录构建所有模块（推荐）
mvn clean install

# 或按顺序手动构建
cd xxx-codegen && mvn install
cd ../xxx-dao && mvn install
cd ../xxx-meta && mvn install    # ⭐ 会生成 xxx-service 和 xxx-web 的代码
cd ../xxx-service && mvn install
cd ../xxx-web && mvn install
cd ../xxx-app && mvn install
```

**修改模型后重新生成**：
```bash
# 修改模型文件后，只需重新构建
mvn clean install
```

### 4.4 目录结构与代码生成

```
{appName}/
├── {appName}-codegen/                 # 代码生成模块
│   └── postcompile/gen-orm.xgen      # 生成 dao, service, web 等所有模块的代码 ⭐
│   └── pom.xml
│
├── {appName}-dao/                     # DAO 模块
│   ├── src/main/resources/_vfs/nop/{module}/orm/app.orm.xml
│   ├── src/main/java/io/nop/{module}/dao/entity/    # 由 codegen 生成
│   └── pom.xml
│
├── {appName}-meta/                    # 元数据模块 ⭐ 核心
│   ├── precompile/gen-meta.xgen       # 生成 XMeta 元数据
│   ├── postcompile/gen-i18n.xgen       # 生成 i18n 文件
│   └── pom.xml
│
├── {appName}-service/                 # 服务模块
│   ├── src/main/java/io/nop/{module}/service/biz/  # 由 codegen 生成 ⭐
│   └── pom.xml
│
├── {appName}-web/                     # Web 模块
│   ├── src/main/resources/_vfs/nop/{module}/pages/   # 由 codegen 生成 ⭐
│   └── pom.xml
│
├── {appName}-app/                      # 应用模块
│   └── pom.xml
│
└── model/
    └── {appName}.orm.xml             # 源模型文件（如 nop-auth.orm.xml）
```

**关键要点**：
1. xxx-codegen 的 `postcompile/gen-orm.xgen` 在执行 `mvn install` 时自动运行，通过 `/nop/templates/orm` 模板同时生成 dao, service, web 等所有模块的代码 ⭐
2. xxx-meta 模块负责生成 XMeta 元数据和 i18n 文件
3. 修改模型文件后，只需执行 `mvn clean install` 即可重新生成所有代码
4. 生成的文件可以通过 x:override 控制覆盖策略，手工修改的内容在重新生成时会保留

## 五、模块依赖关系

### 5.1 依赖关系总结

| 源模块 | 目标模块 | 依赖说明 |
|--------|---------|---------|
| xxx-codegen | xxx-dao | codegen 生成 dao 的代码（测试时依赖） |
| xxx-codegen | xxx-service | codegen 生成 service 的代码 ⭐ |
| xxx-codegen | xxx-web | codegen 生成 web 的代码 ⭐ |
| xxx-dao | xxx-meta | dao 提供 ORM 模型供 meta 生成 XMeta |
| xxx-meta | - | 生成 XMeta 元数据和 i18n 文件 |
| xxx-service | xxx-app | service 提供 GraphQL 服务 |
| xxx-web | xxx-app | web 提供页面和视图 |

### 5.2 构建顺序

**依赖链**：
```
xxx-codegen → xxx-dao → xxx-meta → xxx-service/web → xxx-app
```

**在根目录一次构建所有模块**（推荐）：
```bash
mvn clean install
```

Maven 会自动按照依赖顺序构建，无需手动指定顺序。

## 六、代码生成模板

### 6.1 模板目录结构

代码生成模板位于 `nop-kernel/nop-codegen/src/main/resources/_vfs/nop/templates/`：

```
templates/
├── orm/              # ORM 相关模板
│   ├── {appName}-codegen/
│   ├── {appName}-dao/
│   ├── {appName}-service/
│   ├── {appName}-web/
│   └── {appName}-meta/
├── orm-entity/       # 实体类模板
├── orm-dao/         # DAO 接口模板
├── meta/            # XMeta 模板
├── i18n/            # 国际化模板
└── backend/          # 后端通用模板
```

### 6.2 常用模板

| 模板路径 | 用途 | 生成内容 |
|---------|------|----------|
| `/nop/templates/orm-entity` | 生成实体类 | Entity.java |
| `/nop/templates/meta` | 生成元数据 | *.xmeta |
| `/nop/templates/i18n` | 生成国际化文件 | i18n.yaml |

## 七、开发工作流程

### 7.1 模型驱动开发流程

```mermaid
flowchart TD
    A[1. 编辑 XML 模型<br/>model/{appName}.orm.xml] -->|执行代码生成| B[2. 运行 Maven 构建或 CodeGen 类]
    B -->|precompile| C[3. 生成 ORM 模型]
    B -->|precompile2| D[4. 生成 XMeta 模型]
    B -->|postcompile| E[5. 生成 i18n 等文件]
    C --> F[6. 编译项目]
    D --> F
    E --> F
    F --> G[7. 测试和调试]
    G -->|需要修改| H[8. 修改 XML 模型]
    H --> A

    style A fill:#e1f5fe
    style B fill:#fff9c4
    style C fill:#f8fff9
    style D fill:#ffebee
    style E fill:#e8f5e9
    style F fill:#fce4ec
    style G fill:#f1f8e9
    style H fill:#e0f2f1
```

### 7.2 快速开发模式

除了使用 Maven 构建来执行代码生成，还可以在 IDEA 中直接运行 CodeGen 类：

```java
// 例如: NopAuthCodeGen.java
public class NopAuthCodeGen {
    public static void main(String[] args) {
        AppConfig.getConfigProvider().updateConfigValue(CoreConfigs.CFG_CORE_MAX_INITIALIZE_LEVEL,
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            File projectDir = MavenDirHelper.projectDir(NopAuthCodeGen.class);
            XCodeGenerator.runPostcompile(projectDir, "/", false);
        } finally {
            CoreInitialization.destroy();
        }
    }
}
```

**优点**:
- 无需执行完整的 Maven 编译过程
- 代码生成速度更快
- 便于开发期调试

## 八、Delta 定制模块

### 8.1 Delta 模块的作用

Delta 模块是 Nop 平台实现定制化开发的核心机制，可以在不修改基础产品源码的情况下增加、修改或删除功能。

### 8.2 Delta 模块结构

```
{appName}-delta/
├── model/                 # Delta 模型文件
│   ├── nop-auth-delta.orm.xml
│   └── ...
└── src/main/resources/_vfs/_delta/
    └── nop/auth/        # 差量定制代码
        ├── orm/          # ORM 差量
        ├── model/        # Meta 差量
        └── ...
```

### 8.3 Delta 模型示例

在 Delta 模型的 XML 文件中，只包含需要被扩展的表和字段：

```markdown
## NopAuthUser（扩展表）

- 表名: (继承基础表)
- 类名: NopAuthUserEx
- 基类: io.nop.auth.dao.entity.NopAuthUser
- 对象名: io.nop.auth.dao.entity.NopAuthUser
- 标签: not-gen

#### 字段列表

|编号|标签|主键|非空|字段名|属性名|显示|中文名|类型|长度|备注|
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 51 |  | false | true | mall_user_id | mallUserId |  | 商城用户ID | VARCHAR | 36 | 关联到商城用户表 |
```

生成的 ORM 文件结构：
```xml
<orm x:extends="super,default/nop-auth.orm.xml">
  <entities>
      <entity className="app.mall.delta.dao.entity.NopAuthUserEx"
              displayName="用户"
              name="io.nop.auth.dao.entity.NopAuthUser">
              ...
      </entity>
  </entities>
</orm>
```

### 8.4 使用 Delta 模块

在其他模块（如 `app-mall-app`）中，只需依赖 Delta 模块即可实现对基础功能的定制：

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>app-mall-delta</artifactId>
    <version>${project.version}</version>
</dependency>
```

## 九、最佳实践

### 9.1 模型设计

1. **保持模型的简洁性**
   - XML 模型应专注于数据结构定义
   - 避免在模型中包含业务逻辑

2. **合理命名**
   - 表名使用 snake_case（如 `nop_auth_user`）
   - 类名使用 PascalCase（如 `NopAuthUser`）
   - 字段名使用 camelCase（如 `userName`）

3. **使用标签控制生成**
   - `not-gen` 标记不需要生成的字段
   - `internal` 标记内部字段
   - `tenant` 标记租户字段

### 9.2 代码生成

1. **增量生成**
   - 代码生成器支持增量更新，不会覆盖手动修改的代码
   - 使用 `x:override` 和 `x:extends` 控制覆盖策略

2. **Delta 定制优先**
   - 优先使用 Delta 模块进行定制
   - 避免直接修改基础产品代码

3. **模型驱动**
   - 所有代码都应从模型生成
   - 避免手动修改生成的代码（除特殊情况外）

### 9.3 构建和部署

1. **遵循构建顺序**
   - 确保模块按依赖顺序构建
   - 使用 Maven Reactor 自动管理依赖

2. **统一版本管理**
   - 在父 POM 中统一管理版本
   - 使用 `${project.version}` 引用版本

3. **增量部署**
   - 利用 Delta 机制实现增量部署
   - 避免大规模代码合并

## 十、参考资源

- [代码生成文档](../../docs/dev-guide/nocode/dyn-codegen.md)
- [模块依赖文档](../../architecture/development/module-dependencies.md)
- CLI 工具文档：本仓库 `docs/dev-guide/` 下未提供对应 `cli.md`，如需要请在 `docs`/`docs-en` 中搜索 “cli”/“nop-cli”。

## 十一、常见问题

### Q1: 为什么需要 xxx-codegen 模块？

**答**: xxx-codegen 模块包含代码生成脚本（.xgen 文件）和 CodeGen 类，用于在 Maven 构建时自动执行代码生成。这个模块只在构建时使用，不参与运行时。

### Q2: xxx-meta 模块和 xxx-dao 模块有什么区别？

**答**: 
- xxx-dao: 定义数据库结构和 ORM 映射
- xxx-meta: 基于 ORM 模型生成 XMeta 元数据，包含字段属性、UI 配置等更丰富的信息

### Q3: 如何只生成 DAO 模块而不生成前端代码？

**答**: 使用 `orm-dao` 模板而非 `orm` 模板：
```bash
java -jar nop-cli.jar gen -t=/nop/templates/orm-dao -o=nop-mall-dao model/nop-mall.orm.xml
```

### Q4: 为什么有些模块有 precompile，有些有 postcompile？

**答**: 
- precompile: 在编译前生成，用于生成基础模型和代码
- postcompile: 在编译后生成，用于生成需要访问已编译类的文件（如 i18n）

### Q5: 如何调试代码生成脚本？

**答**: 
1. 在 IDEA 中运行 CodeGen 类的 main 方法
2. 使用 Nop 平台的断点调试功能
3. 查看 `XCodeGenerator` 的日志输出

### Q6: Delta 模块如何影响基础产品？

**答**: Delta 模块通过 x:extends 机制覆盖基础产品的模型定义。在运行时，系统会优先加载 Delta 目录下的模型文件，从而实现对基础功能的定制。

---

**文档维护者**: AI Assistant (Sisyphus)
**最后更新**: 2025-01-09
**文档版本**: v1.0
