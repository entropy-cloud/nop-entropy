# 维度 19：命名一致性审查

## 发现

### [19-01] P3 — JobCoreErrors 混合错误码格式

- **文件**: JobCoreErrors
- **现状**: 同一文件中混合使用两种错误码格式：
  - 标准 `nop.err.job.*` 前缀格式（4 个错误码）
  - 裸标识符格式如 `JOB_TIMEOUT` 等（5 个错误码）
- **风险**: 格式不一致可能影响错误码的统一处理、文档生成和搜索。
- **建议**: 统一到 `nop.err.job.*` 前缀格式，或明确标注裸标识符为保留的历史格式。

### [19-02] P3 — 错误码类命名跨子模块不一致

- **文件**: JobApiErrors, JobCoreErrors, NopJobErrors
- **现状**: 错误码类的命名风格不一致：
  - `JobApiErrors` / `JobCoreErrors`：无 `Nop` 前缀
  - `NopJobErrors`：有 `Nop` 前缀
  
  但所有类中的常量名统一使用 `NopJob*` 前缀（如 `NopJobConstants`）。
- **建议**: 统一类命名风格，建议全部添加 `Nop` 前缀（如 `NopJobApiErrors`、`NopJobCoreErrors`）。
