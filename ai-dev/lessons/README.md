# Lessons Learned

经验教训索引。每条教训一个独立文件，顺序编号。

**编号规则**：`NN-简短标识.md`，NN 从 01 递增。

**新增教训时**：
1. 查看当前最大编号，+1
2. 用下一节模板创建文件
3. 在本文件索引表中追加一行

## 索引

| # | 文件 | 标题 | 日期 |
|---|------|------|------|
| 01 | [01-batch-memory-accumulation.md](01-batch-memory-accumulation.md) | 分批处理≠流式处理：累积后再持久化仍会 OOM | 2026-05-10 |
| 02 | [02-metrics-design-convention.md](02-metrics-design-convention.md) | 禁止直接注入 MeterRegistry：Nop Metrics 三件套规范 | 2026-05-11 |
| 03 | [03-plan-guide-is-mandatory.md](03-plan-guide-is-mandatory.md) | Plan Guide 是强制程序，不是参考文档 | 2026-05-20 |
| 04 | [04-git-checkout-directory-destroys-unrelated-changes.md](04-git-checkout-directory-destroys-unrelated-changes.md) | git checkout 回退整个目录会丢失无关修改 | 2026-07-15 |
| 05 | [05-overclaimed-closure-fix-status-drift.md](05-overclaimed-closure-fix-status-drift.md) | Overclaimed Closure：fix-status 与 live repo 漂移 | 2026-07-31 |
| 06 | [06-credential-field-multi-layer-convergence.md](06-credential-field-multi-layer-convergence.md) | 凭证字段跨层暴露：收敛必须从 ORM 源模型开始 | 2026-07-31 |
| 07 | [07-zero-test-modules-invisible-in-ci.md](07-zero-test-modules-invisible-in-ci.md) | Zero-test 模块在 CI 中不可见：构建通过 ≠ 质量可接受 | 2026-07-31 |
| 08 | [08-tool-executor-security-boundary.md](08-tool-executor-security-boundary.md) | Tool Executor 层是安全缺陷集中区：SSRF/路径逃逸同源 P1 | 2026-07-31 |
