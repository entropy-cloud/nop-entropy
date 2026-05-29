# 维度01：依赖图与模块边界

## 第 1 轮（初审）

### [维度01-01] nop-code-api 是一个空壳孤儿模块

- **文件**: `nop-code/nop-code-api/pom.xml`
- **行号**: 全文
- **证据片段**:
  ```xml
  <artifactId>nop-code-api</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <dependencies>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-api-core</artifactId>
          <version>${nop-entropy.version}</version>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: nop-code-api 被声明为 nop-code 的子模块，但无 src/ 目录，无 Java 代码，无资源文件。没有任何其他 nop-code 子模块依赖它。实际的 API 契约（ICodeIndexService、约 30 个 DTO）全部放在了 service 模块中。
- **风险**: (1) 增加无意义的 Maven reactor 构建时间；(2) 产生维护困惑——后续开发者不确定 API 契约应放在哪里；(3) java.version=11 与项目其他模块不一致。
- **建议**: 二选一：(a) 将 service 模块中的 ICodeIndexService 及 DTO 迁入 api 模块；(b) 从父 pom 的 modules 中移除 nop-code-api。
- **信心水平**: 确定
- **误报排除**: 不是"看起来不优雅"的问题。模块存在于 reactor 但完全无人使用，违反了 api 层隔离职责。
- **复核状态**: 未复核

### [维度01-02] nop-code-meta 无 compile-scope 内部依赖

- **文件**: `nop-code/nop-code-meta/pom.xml`
- **行号**: 全文 31 行
- **证据片段**:
  ```xml
  <dependencies>
      <dependency>
          <artifactId>nop-code-codegen</artifactId>
          <scope>test</scope>
      </dependency>
      <dependency>
          <artifactId>nop-code-dao</artifactId>
          <scope>test</scope>
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: nop-code-meta 仅包含 XML/YAML 资源文件，无 Java 源码。两个内部依赖均为 test-scope。分层规则要求"meta 依赖 dao"，但 compile-scope 层面 meta 零依赖。
- **风险**: XMeta 引用了 dao 层的 ORM 实体名，但缺少 compile-scope 对 dao 的依赖。Nop 平台 XMeta 验证在运行时通过 VFS 完成，当前运行无风险。
- **建议**: 当前可保持现状。建议添加注释说明为什么 dao 是 test-only 依赖。
- **信心水平**: 很可能
- **误报排除**: 分层规则明确写了"meta 依赖 dao"但实际 compile-scope 并无此依赖。因 meta 是纯资源模块、运行时验证由 VFS 完成，实际运行无风险，定性为 P3。
- **复核状态**: 未复核

### [维度01-03] nop-code-lang-java 显式声明了对 nop-commons 的冗余依赖

- **文件**: `nop-code/nop-code-lang-java/pom.xml`
- **行号**: L11-28
- **证据片段**:
  ```xml
  <dependencies>
      <dependency>
          <artifactId>nop-code-core</artifactId>
          <version>1.0.0-SNAPSHOT</version>
      </dependency>
      <dependency>
          <groupId>io.github.entropy-cloud</groupId>
          <artifactId>nop-commons</artifactId>     <!-- 冗余：已通过 nop-code-core 传递 -->
      </dependency>
  </dependencies>
  ```
- **严重程度**: P3
- **现状**: nop-code-core 已经依赖 nop-commons（compile）。经搜索，lang-java 的 Java 源码中没有任何 import io.nop.commons.* 语句，该依赖未被直接使用。
- **风险**: 极低。冗余依赖不会产生运行时错误，仅增加维护噪音。
- **建议**: 移除 nop-commons 的显式声明。
- **信心水平**: 确定
- **误报排除**: 冗余 Maven 依赖经代码搜索确认无直接 import，属可清理项。不违反分层规则，定性为 P3。
- **复核状态**: 未复核

## 依赖图总览

```
                    ┌──────────┐
                    │   api    │ (空壳)
                    └──────────┘

┌──────────┐
│   core   │◄──────┬───────┬────────┬────────────┬─────────────┐
└──────────┘       │       │        │            │             │
              ┌────┴──┐ ┌──┴───┐ ┌──┴──────┐ ┌──┴──────────┐ │
              │ graph │ │flow  │ │lang-java│ │lang-python  │ │
              └────┬──┘ └──────┘ └─────────┘ └─────────────┘ │
                   │            │                              │
                   └────────────┤   ┌─────────┐               │
                                ├──►│ service │◄──────────────┘
                   ┌────────┐   │   └────┬────┘
                   │  meta  │───┘        │
                   └────────┘            │
                                   ┌─────┴─────┐
                                   │    web     │
                                   └─────┬─────┘
                                         │
                                   ┌─────┴─────┐
                                   │    app     │
                                   └───────────┘
```

## 合规模块

| 模块 | 判定 | 说明 |
|------|------|------|
| nop-code-core | 合规 | 仅依赖 api + 框架核心，不依赖 dao |
| nop-code-dao | 合规 | 仅依赖 api + nop-orm |
| nop-code-codegen | 合规 | 仅依赖代码生成工具链 |
| nop-code-graph | 合规 | 仅依赖 core |
| nop-code-flow | 合规 | 仅依赖 core + graph |
| nop-code-lang-* | 合规 | 仅依赖 core |
| nop-code-service | 合规 | 依赖 api + core + dao + 计算模块 |
| nop-code-web | 合规 | 依赖 meta + service |
| nop-code-app | 合规 | Quarkus 仅在 app 模块 |

**无循环依赖。核心分层方向正确。**
