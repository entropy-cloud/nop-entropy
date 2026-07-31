# 07: Zero-test 模块在 CI 中不可见 — 构建通过 ≠ 质量可接受

> Date: 2026-07-31
> Severity: Medium — MA4.3 审计才发现 nop-ai 7 个模块 0 测试（nop-ai-api 84 main / 0 test、nop-ai-dao 66/0、nop-ai-tools 19/0 等），此前的每次构建都通过

## 场景

nop-ai 模块组自初始提交起经过多轮 `mvn clean install` + `mvn test`，构建从未失败。MA4.3（测试覆盖审计）才发现：

| 模块/包 | main 文件 | test 文件 |
|---------|-----------|-----------|
| nop-ai-api | 84 | 0 |
| nop-ai-dao | 66 | 0 |
| nop-ai-tools | 19 | 0 |
| nop-ai-core/api/ | 43 | 0 |
| nop-ai-mcp-server | 2 | 0 |
| nop-ai-app | 1 | 0 |
| nop-spring-mcp-server-support | 2 | 0 |

其中 nop-ai-api（公开 API 契约层）、nop-ai-dao（ORM/DAO 层）是基础设施模块，其契约回归完全依赖下游模块测试间接覆盖 — 下游模块即使有测试，也只覆盖自己用到的路径。

## 根因

1. **`mvn test` 的通过条件是"存在且通过的测试"**，不是"测试覆盖达标"。模块无测试 = 零测试类 = 零测试运行 = 构建绿色。
2. **测试覆盖审计缺位**：MA1-MA6 审计中，只有 MA4.3 专门查"每个模块有没有测试"，之前的审计集中在代码质量/安全/架构，没有模块级测试存在性检查。
3. **测试隔离性审计（MA5.6）也不检查存在性**：它查的是"既有测试是否互相污染"，对零测试模块同样静默通过。

## 正确做法

1. **审计的固定检查项：模块测试存在性扫描** — 对每个子模块执行 `ls src/test/java`（或统计 test 文件数），零测试模块直接列为 P1 finding，不等"有机会再补"。
2. **基础设施模块（api/dao/toolkit）优先补测试**：公开契约、持久化映射、工具执行器是被下游大量消费的路径，零测试的风险最高。
3. **CI/审计门禁补模块级最低测试要求**：nop-ai 闭环执行的标准 — 零测试模块必须 ≥3 个行为断言测试方法（`TestChatOptions`、`TestNopAiOrmEntityMapping` 等模式）。
4. **MR 收尾时跑一次模块级覆盖清单**：MR 修复完成后的验证不只跑"全部测试通过"，还要列"本 MR 触及的模块是否有测试"。

## 判定规则

> **"构建通过"只证明测试没失败，不证明模块被测试。** 判定一个模块是否有测试保护：`src/test` 下存在行为断言类（非空断言/catch-and-pass）且测试运行时会加载该模块的 main 代码路径。
>
> 零测试模块按 live defect 处理（P1），不降级为 deferred。

## 适用范围

- 多模块仓库的审计-修复闭环
- 新模块合入前的质量门禁
- 测试覆盖类审计（MA4.3 类）

## 参考

- `ai-dev/audits/2026-07-31-XXXX-arm-MA4.3-nop-ai-test-coverage.md`（7 个零测试模块清单）
- `ai-dev/audits/arm-index.md`（MA4.3-01/02/05 等 P1 行，MR2/MR4 修复后均有测试文件证据）
