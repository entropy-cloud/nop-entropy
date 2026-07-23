> Audit Status: planned
> Audit Type: multi-dimensional
> Mission: nop-metadata
> Dimension: 01 — 依赖图与模块边界

## 第 1 轮（初审）

### [维度01-001] nop-metadata-api 未注册到 nop-bom 版本管理

- **文件**: `nop-bom/pom.xml:1148-1188`
- **证据片段**:
  ```xml
  <!-- BOM 中存在的 nop-metadata 子模块 -->
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-codegen</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-core</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-dao</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-meta</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-service</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-web</artifactId>
  </dependency>
  <dependency>
      <groupId>io.github.entropy-cloud</groupId>
      <artifactId>nop-metadata-app</artifactId>
  </dependency>
  <!-- nop-metadata-api 缺失 -->
  ```
- **严重程度**: P2
- **现状**: `nop-metadata-api` 是 `nop-metadata` 模块组中唯一未注册到 `nop-bom` `dependencyManagement` 的子模块。参照 `nop-wf-api`、`nop-job-api` 均已注册。
- **风险**: 外部项目如果使用 `nop-bom` 进行版本管理但需要引入 `nop-metadata-api`，无法获得自动版本对齐，必须手工硬编码版本号。
- **建议**: 在 `nop-bom/pom.xml` 的 nop-metadata 模块群区域添加 `nop-metadata-api` 的 dependency 声明。
- **信心水平**: 确定
- **误报排除**: 这不是"未显式声明平台核心包"误报。`nop-metadata-api` 是业务模块的子模块 artifact，非平台核心包。对比参照：`nop-wf-api`、`nop-job-api` 已注册，说明 api 子模块注册到 BOM 是预期模式。
- **复核状态**: 未复核

### 零发现清单（合规项）

以下所有子模块依赖合规，无违反分层规则：
- `nop-metadata-api`: 仅依赖 `nop-api-core` ✅
- `nop-metadata-core`: 依赖 `nop-metadata-api` + `nop-api-core` ✅
- `nop-metadata-codegen`: 无内部 nop-metadata 依赖 ✅
- `nop-metadata-dao`: 依赖 api + nop-orm，codegen 为 test scope ✅
- `nop-metadata-meta`: 依赖 dao(codegen) 均为 test scope ✅
- `nop-metadata-service`: 依赖 api+core+dao+meta ✅
- `nop-metadata-web`: 依赖 service，不直连 dao ✅
- `nop-metadata-app`: 依赖 service+web，Quarkus 仅此处出现 ✅

### 依赖图

```
nop-metadata-api --> nop-api-core
nop-metadata-core --> api, api-core
nop-metadata-codegen (无内部依赖)
nop-metadata-dao --> api, orm, codegen(test)
nop-metadata-meta --> dao(test), codegen(test)
nop-metadata-service --> api, core, dao, meta, biz, wf, job
nop-metadata-web --> service, web
nop-metadata-app --> service, web, quarkus-starter
```

### 总结评估

依赖图总体健康，无循环依赖，无分层违规。`nop-metadata-api` 未注册到 BOM 是唯一发现 (P2)。
