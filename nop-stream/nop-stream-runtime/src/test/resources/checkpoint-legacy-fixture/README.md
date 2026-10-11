# checkpoint-legacy-fixture — 跨版本格式钉（WI10, plan 2426）

本目录是「旧版本代码写出」的 checkpoint 存储树快照（`storage/<jobId>/<pipelineId>/*.epoch`
manifest + checkpoint 数据），由 `TestLegacyCheckpointFixtureRestore` 在每次测试中经
manifest-first auto-restore 消费，钉住路线图 WI10 硬约束：**存储格式 / 序列化字节 /
epoch manifest 结构零变更——存量 checkpoint 必须能被新代码 restore**。

## 再生政策（Regeneration Policy）

- **本 fixture 是格式钉，只能显式有意再生**。默认测试套件永不再生。
- 再生方式：删除本目录下的 `storage/` 后，运行
  `mvnw test -pl nop-stream/nop-stream-runtime -Dtest=TestLegacyCheckpointFixtureGenerator -Dnop.stream.test.fixture.regenerate=true`
  （生成器拒绝覆盖已存在的 fixture——先删后生，强制这个动作是显式的）。
- **只有当存储格式合法演进时才允许再生**，且必须同步：
  1. `ai-dev/design/nop-stream/checkpoint-design.md` 的格式契约节（版本号/字段）；
  2. 结构重构 roadmap 或后继 roadmap 的格式演进条目；
  3. 提交说明中记录新旧格式差异与兼容窗口。
- 生成管线：`fixture-source-job`，40 元素限速 source → keyBy(%2) → reduce → 2PC sink，
  interval 150ms / timeout 30s / maxRetained 5 / local 存储。恢复边界（最新数据 checkpoint）
  随生成时刻的 epoch 边界漂移，属设计内——恢复测试钉的是结构不变量与格式可解析性，
  不钉恢复点的绝对值。

## 内容

- `storage/` — LocalFileCheckpointStorage 布局：`storage/<jobId>/<pipelineId>/<epochId>.epoch`
  （manifest）与同族 checkpoint 数据文件。
