# 维度 01：依赖图与模块边界

## 审计结果：零发现

nop-code 模块 13 个子模块形成无环有向图，严格遵循 Nop 平台标准分层规则。

### 完整依赖图

```
nop-code-api (nop-api-core)
    ↑
nop-code-core (nop-api-core, nop-commons, nop-core)
    ↑           ↑           ↑
nop-code-graph nop-code-flow nop-code-lang-{java,python,typescript}
    (→core)     (→core,graph)  (→core)

nop-code-codegen (nop-ooxml-xlsx, nop-orm, nop-graphql-core, nop-xlang-debugger)
    ↑
nop-code-dao (nop-api-core, nop-orm)
    ↑
nop-code-meta (test: codegen, dao) → 纯资源模块
    ↑
nop-code-service (api, dao, core, graph, flow, lang-*, meta, nop-biz, nop-config, nop-ioc, nop-search-api)
    ↑           ↑
nop-code-web   nop-code-app (quarkus-web-orm-starter, auth-web, web-site)
```

### 合规检查

| 规则 | 结果 |
|------|------|
| api 层不依赖业务实现层 | 合规 |
| dao 层只依赖 api + 框架 | 合规 |
| core 层只依赖 api + 框架核心 | 合规 |
| service 层依赖 api + core + dao | 合规 |
| web 层不直接依赖 dao | 合规 |
| app 层依赖 web + service | 合规 |
| 无循环依赖 | 合规 |
| 框架特定依赖只在 app | 合规 |
