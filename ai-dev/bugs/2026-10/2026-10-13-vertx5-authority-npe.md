# 2026-10-13 Vert.x 5 authority() 迁移导致的 getHost NPE（产品 + 测试双层）

## Problem

- `nop-quarkus-web` 的 `TestVertxHttpServerContextAdapter.testBasicFields` 确定性失败（隔离运行必现）：
  `NullPointerException: Cannot invoke "io.vertx.core.net.HostAndPort.host()" because the return value of "HttpServerRequest.authority()" is null`
- 产品面隐患：`VertxHttpServerContext.getHost()` 对无 Host 头的请求（HTTP/1.0 客户端）会直接 NPE，该适配器服务所有 Quarkus 入口的 `IHttpServerContext` 消费方。
- 该失败此前不可见：本轮之前每次全量构建都在更早的模块失败，`nop-quarkus-web` 的测试从未真正执行过。

## Diagnostic Method

- 构建日志 NPE 栈指向 `getHost` → `request().authority().host()`；隔离重跑复现，排除负载因素（与前 3 个偶发失败不同类）。
- `git log` 显示最近提交 `c01d6c42c3`（WI5 Quarkus 4.0.0.Beta1 全仓统一，plan 2431）将 `getHost()` 从 `request().host()`（旧 API，Host 头原串）迁到 `authority().host()`，但未同步本测试的 JDK 代理 Stub——Stub 无 `authority` 分支，动态代理对未知方法返回 null，必现 NPE。
- 对照 Vert.x 5 javadoc/字节码：`authority()` 在请求无 Host 头时返回 null（合法状态）；`HostAndPort` 为接口（`host()`/`port()`），host 不含端口。
- 检查唯一生产调用方 `AuthHttpServerFilter`：经 `StringHelper.replace`（null 安全，按 "" 处理），`getHost()` 返回 null 是可接受契约面。

## Root Cause

- WI5 迁移改了产品 API 调用链但漏更对应契约测试（测试注释仍写着旧契约 `request().host()`），且迁移本身引入了 null-authority NPE 路径。

## Fix

- 产品：`VertxHttpServerContext.getHost()` 对 `authority()` 判空，无 Host 头返回 null（语义："host 未知"，非失败）。
- 测试：Stub 的 `case "host"` 替换为 `case "authority"`（返回 `HostAndPort` 动态代理，host 不含端口），断言更新为 `"host"` 并注明新契约；新增 `missingHostHeaderYieldsNullHost` 回归测试钉住 null 面。

## Tests

- `nop-quarkus/nop-quarkus-web/src/test/java/io/nop/quarkus/web/filter/TestVertxHttpServerContextAdapter.java` — 11/11 通过（原 10 例修复 + 1 例新增 null-authority 回归）。

## Affected Files

- `nop-quarkus/nop-quarkus-web/src/main/java/io/nop/quarkus/web/filter/VertxHttpServerContext.java`
- `nop-quarkus/nop-quarkus-web/src/test/java/io/nop/quarkus/web/filter/TestVertxHttpServerContextAdapter.java`

## Notes For Future Refactors

- API 迁移（如 Q4/Vert.x 5）触及 `IHttpServerContext` 适配层时，两侧镜像测试（`TestServletHttpServerContextAdapter` / `TestVertxHttpServerContextAdapter`）必须同步更新；两者的语义分歧（spring=RemoteHost，vertx=authority host）是有意设计，勿"统一"。
- JDK 动态代理 Stub 对未 stub 的方法返回 null——迁移后新调用的方法若未补分支，失败形态是 NPE 而非明确的"未 stub"提示，排查看栈要直接定位到适配器方法。
