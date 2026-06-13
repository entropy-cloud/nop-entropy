# 维度 01：依赖图与模块边界 — nop-ai-agent

## 第 1 轮（初审）

### 检查范围

**已读关键文件：**

| 文件 | 用途 |
|------|------|
| `nop-ai/nop-ai-agent/pom.xml` | 模块依赖声明 |
| `nop-ai/nop-ai-core/pom.xml` | 上游依赖链分析 |
| `nop-ai/nop-ai-toolkit/pom.xml` | 上游依赖链分析 |
| `nop-ai/nop-ai-api/pom.xml` | API 层依赖基线 |
| `nop-ai/pom.xml` | 父模块模块列表 |
| `nop-ai/nop-ai-agent/src/main/java/**` 全部手写 Java 源文件 | import 扫描 |
| `nop-ai/nop-ai-agent/src/test/java/**` 全部测试 Java 源文件 | import 扫描 |
| `nop-ai/nop-ai-agent/src/main/resources/_vfs/**` 全部资源文件 | 注册模型/record-mappings 引用验证 |
| `nop-ai/nop-ai-service/pom.xml` | 反向依赖检查 |
| `nop-ai/nop-ai-web/pom.xml` | 反向依赖检查 |
| `nop-ai/nop-ai-app/pom.xml` | 运行时依赖链完整性 |
| `docs-for-ai/01-repo-map/module-groups.md` | 模块分组基线 |
| `docs-for-ai/01-repo-map/domain-module-pattern.md` | 标准业务模块骨架 |

**已执行检查项（11 项）：**

1. pom.xml 直接依赖提取与合规判定
2. 传递依赖链完整构建
3. 主源码全部 `io.nop.*` import 提取，按模块归类
4. 测试源码全部 `io.nop.*` import 提取，按模块归类
5. 反向依赖扫描：nop-ai-core、nop-ai-toolkit、nop-ai-service、nop-ai-web、nop-ai-shell、nop-ai-skills、nop-ai-tools、nop-ai-rag 中是否 import nop-ai-agent 类型
6. 跨层边界检查：nop-dao / nop-graphql / nop-biz / nop-ioc / nop-config / nop-web / nop-ai-service / nop-ai-dao / nop-ai-web 类型是否出现在主源码
7. 循环依赖检查
8. 缺失依赖检查
9. 生成产物引用检查
10. `nop-record-mapping` test scope 下的主资源引用安全性验证
11. 非 io.nop 第三方依赖完整性检查

---

### 完整依赖图

```
nop-ai-agent
│
├── [compile, direct] nop-ai-toolkit
│   ├── [compile, transitive] nop-ai-api
│   │   └── [compile] nop-api-core
│   ├── [compile, transitive] nop-xlang
│   │   └── [compile] nop-core
│   │       └── [compile] nop-commons
│   ├── [compile, transitive] nop-http-api
│   ├── [compile, transitive] nop-search-api
│   ├── [compile, transitive] nop-api-core  (duplicate, via both)
│   └── [compile, transitive] nop-diff
│
├── [compile, direct] nop-ai-core
│   ├── [compile, transitive] nop-ai-api  (duplicate)
│   ├── [compile, transitive] nop-api-core  (duplicate)
│   ├── [compile, transitive] nop-dao       ← 不被 nop-ai-agent 主源码使用
│   ├── [compile, transitive] nop-http-api  (duplicate)
│   ├── [optional, transitive] nop-http-client-jdk
│   ├── [compile, transitive] nop-xlang  (duplicate)
│   ├── [compile, transitive] nop-markdown  ← 仅测试代码使用
│   └── [compile, transitive] nop-diff  (duplicate)
│
├── [test, direct] nop-autotest-junit
│
└── [test, direct] nop-record-mapping
```

**反向依赖（谁依赖 nop-ai-agent）：无。** nop-ai-agent 是 nop-ai 子系统中的叶子模块。

---

### 发现

**本维度零发现。**

经过 11 项检查，nop-ai-agent 的依赖图与模块边界合规，无违规或高风险问题。

逐项合规判定：
- **api 层不依赖业务实现层**: 合规。依赖的 nop-ai-toolkit/nop-ai-core 为 AI 基础设施层。
- **core 层不依赖 dao**: 合规。主源码无任何 `io.nop.dao.*` import。nop-ai-core 传递引入 nop-dao 但未使用。
- **无循环依赖**: 合规。全部 nop-ai 子模块均不 import `io.nop.ai.agent.*`。
- **框架特定依赖只出现在 app 模块**: 合规。主源码无 ioc/config/graphql/biz/web import。
- **缺失依赖声明**: 合规。所有主源码 import 均可通过直接依赖或平台核心包传递链获得。
- **生成产物引用**: 合规。手写保留类正确继承 `_gen` 下的生成基类。
- **nop-record-mapping test scope**: 合规。通过 `optional="true"` 安全处理。
- **第三方依赖**: 合规。仅 slf4j，通过 nop-core 传递链获得。

### 合规的模块清单

| 模块 | 依赖边界状态 |
|------|-------------|
| nop-ai-agent | **合规** — 叶子模块，无反向依赖，无循环依赖，无跨层边界违规，依赖声明完整 |
