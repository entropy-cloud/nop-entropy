# WI30 Closure Audit——10-wi30-scalar-udf-spi.md

> Auditor: 独立子 agent（fresh session）
> Date: 2026-10-08
> Verdict: **APPROVED**

## 验证矩阵

| # | 验证项 | 结果 | 证据 |
|---|---|---|---|
| 1 | SPI 接口 | PASS | IScalarFunctionResolver（flow/spi）——contains/registeredNames/resolve + AutoCloseable |
| 2 | 实现 | PASS | StreamScalarFunctionResolver——LinkedHashMap 注册 + static volatile default + setDefault/registerAsDefault/call + close 幂等 |
| 3 | 求值通道 | PASS | compileScalar SqlRegularFunction 分支——编译期一次解析、未注册 typed fail-fast（列已注册名）|
| 4 | 打印通道 | PASS | SqlScalarXplPrinter SqlRegularFunction 分支——StreamScalarFunctionResolver.call 静态调用 + import 前置（requiredImports 收集）|
| 5 | E2E | PASS | TestUdfE2E——UDF 查询真实执行 strlen(item) 7 行精确断言 |
| 6 | 生命周期 | PASS | close 幂等测试 |
| 7 | 文档 | PASS | nop-stream-sql.md §3a UDF 小节新撰（含 ServiceLoader→IoC 差异披露）|
| 8 | 零越权 | PASS | git 变更恰限 nop-stream-flow/spi + nop-stream-sql + docs/ai-dev |
| 9 | 回归 | PASS | 全模块 BUILD SUCCESS |

## 执行发现

- ServiceLoader 字面表述实为 IoC/静态持有同构（如实披露于 SPI javadoc 与用户文档）
- EQL 函数名解析后统一小写（strLen→strlen）
- 裸 FQN 不可用先例再次实证（Boolean.TRUE 亦不可用，true 字面量替代——WI29 交叉发现）
