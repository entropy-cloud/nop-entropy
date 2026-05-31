# 维度 16：测试覆盖与质量

## 第 1 轮（初审）

### 检查范围

- 202 个测试文件 vs 423 个主源文件（测试比 0.48）
- 覆盖 4 条核心路径：窗口算子（28 个测试）、CEP NFA（30 个测试）、Checkpoint 恢复（~30 个测试）、状态管理（~20 个测试）

### 结论：无发现

| 路径 | 覆盖评估 |
|------|----------|
| 窗口算子 | 充分 — 7 种 WindowAssigner + 6 种 Trigger + 3 种 Evictor 全覆盖，含 snapshot/restore、late data |
| CEP NFA | 充分 — NFA 核心、NFA 编译、SharedBuffer、CepOperator 状态恢复、Skip 策略 E2E、模型构建 |
| Checkpoint | 非常充分 — 协调器、恢复、E2E、JDBC/文件存储、savepoint、两阶段提交、分布式 ExactlyOnce |
| 状态管理 | 良好 — 后端 snapshot/restore、分片路由、多算子链隔离 |

错误路径：约 60 个测试使用 assertThrows。有专门的安全边界测试（路径遍历、畸形模式）。

AutoTest 快照：无（模块使用 JUnit 5 手写测试风格）。

## 维度复核结论

（待复核）
