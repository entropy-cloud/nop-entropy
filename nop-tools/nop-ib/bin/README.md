# nop-ib — 无状态增量编译与测试服务

面向 AI（与开发者）的日常编译/测试反馈服务。**平台开发期的编译与测试一律走 nop-ib；`mvn` 只用于最终打包**（`install`/`deploy`/发布镜像）。

## 目录结构

```
~/nop-ib/
├── nop-ib.jar      # CLI + 服务 jar
├── lib/            # 运行时依赖
├── bin/
│   ├── ib          # CLI 客户端（任意 cwd、任意 Maven 项目）
│   ├── start.sh    # 启动 HTTP 服务（默认端口 8188，env NOP_IB_PORT 可换）
│   └── stop.sh     # 停止服务
├── nop-ib.log      # 服务日志
├── nop-ib.pid      # pid 文件
└── README.md       # 本文档
```

## 启动 / 停止

```bash
~/nop-ib/bin/start.sh     # 幂等：已在跑则报告健康状态；默认端口 8188
~/nop-ib/bin/stop.sh      # 停止（SIGTERM，2 秒不退升级 SIGKILL）
NOP_IB_PORT=9000 ~/nop-ib/bin/start.sh   # 换端口
```

健康检查：`curl http://127.0.0.1:8188/v1/health` → `{"version","cores","workerBudget","activeJobs","pendingTotal"}`。

> 只 loopback 监听（127.0.0.1），无鉴权——本机开发工具边界。

## 服务调用（AI 主入口）

任何程序向服务发送 **项目地址 + 指令**：

```bash
# 同步等待结果（多数场景）
curl -s -X POST http://127.0.0.1:8188/v1/run -d '{
  "projectDir": "/path/to/project",
  "cmd": "check",
  "args": ["--paths", "src/main/java/Foo.java"],
  "wait": true
}'

# 异步：wait=false 返回 job id，轮询 GET /v1/job/{id}
curl -s -X POST http://127.0.0.1:8188/v1/run -d '{"projectDir":"...","cmd":"test","args":["-pl",":module"],"wait":false}'
curl -s http://127.0.0.1:8188/v1/job/job-42
```

- `cmd` 白名单：`status | plan | compile | test | check | model | clean`
- `projectDir` 可以是仓库根、**子模块目录**或 **symlink 别名**——服务统一归一到 reactor 根（同项目只有一条构建队列，杜绝并发踩踏）
- payload 与 CLI `--json` 同构；`exit` 字段即构建结果（0 成功 / 1 失败 / 6 partial / 75 超时）
- **同项目串行 + 自动排队**：同一项目一次只跑一个构建，其余 FIFO 排队（上限 32，满则 429）
- **跨项目自适应并行**：全局预算 = CPU 核数一半（本机 16 核 → 8），多项目并发不超载机器

## 新项目接入（一次性）

```bash
~/nop-ib/bin/ib model refresh    # 在项目根目录执行：导出 Maven 构建事实（大项目 ~50s）
```

之后模型随 pom 变化自动检测过期；过期时服务自动回退真 Maven 并给出原因，重新 `model refresh` 即恢复快路径。

## CLI 直用（不经服务）

```bash
~/nop-ib/bin/ib check --paths src/main/java/Foo.java   # 项目根目录下执行
~/nop-ib/bin/ib test -pl :module-name
~/nop-ib/bin/ib clean -pl :module-name                 # mvn-clean 同义面 + 索引/缓存失效
```

## 缓存与状态（全在项目内，服务无状态）

全部落在各项目自己的 `.nop-ib/` 目录：`model.json`（构建事实）、`units/`（类图/ABI 索引）、`test-cache.json`（opt-in `--cache` 的模块级绿结果缓存）。服务进程崩溃/重启零影响——重建连接重发请求即可（幂等）。

## 配置

| 属性 | 作用 | 默认 |
|---|---|---|
| `NOP_IB_PORT`（env） | 服务端口 | 8188 |
| `nop.ib.maven`（-D） | fallback 用 Maven 可执行文件路径 | mvnw > PATH 全局 mvn |
| `nop.ib.serviceWorkerBudget` | 全局 job 并发上限 | max(2, cores/2) |
| `nop.ib.workerBudget` | 单 job 测试 worker 上限 | 4×cores |

## 何时仍然用 mvn

**只在这些场景**：`mvn install` / `deploy` / 发布打包、CI 流水线、全量冷构建、代码生成链（xgen 模板变更/AOP 模块——ib 会显式回退 Maven 并给原因）。日常开发（改代码→编译→跑测试）**不碰 mvn**。

## 重建分发

```bash
cd <nop-entropy 源码>/nop-tools/nop-ib && ./bin/package-ib.sh   # 重新打包到 ~/nop-ib
```
