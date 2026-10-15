# xlang-web-debugger 设计目录

本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织，承载"XLang 在线开发调试编辑器"的设计决策与使用契约。roadmap 状态索引见 `ai-dev/backlog/xlang-web-debugger-roadmap.md`（本文档不追踪执行状态）。

## 阅读顺序

1. **[00-overview.md](00-overview.md)**（必读入口）— Vision + Architecture Baseline 双职：总体组件图、端到端时序、传输容器/网关模块归属/范围三类裁定、里程碑验收标准（M1-M6）。
2. 按需深入 D1-D6 子域详设（落地后从下表进入）。

## 文档职责边界

| 文档 | 层级 | 职责 | 状态 |
| --- | --- | --- | --- |
| `00-overview.md` | Vision + Architecture Baseline 双职 | 总纲：组件/时序/共存/传输容器/范围裁定/里程碑判据；协议分层声明（容器层归 D0 锁定） | active |
| [01-ws-protocol.md](01-ws-protocol.md) | 子域详设（D1） | WS 协议详设：envelope schema、消息集与 `IDebugger` 映射表（24 方法逐一登记）、版本化、鉴权方案（查询参数裁定）、扩展事件登记、关闭码映射、CSV 对照 | active |
| 02-lease-lifecycle | 子域详设（D2） | 租约续期与生命周期：TTL/renew/宽限/多标签页归属、resume 与 close 双语义状态机 | 待立项（roadmap item 3） |
| 03-sessionization | 子域详设（D3） | 会话化：方案 A/B 裁定、会话标识透传、每会话实例生命周期、单步目标策略、IDEA 兼容 | 待立项（roadmap item 4） |
| 04-execution-backend-link | 子域详设（D4） | 调试模式与执行后端联动：两条静态绑定绕过路径的消除策略、配置联动矩阵 | 待立项（roadmap item 5） |
| 05-frontend-architecture | 子域详设（D5） | flux 前端架构：包结构、扩展点、语言注册开放化 | 待立项（roadmap item 6） |
| 06-grammar-spike | spike 记录（D5 附） | Lezer grammar 三来源 POC 结论与裁定 | 待立项（roadmap item 6） |
| 07-repl-security | 子域详设（D6） | REPL 安全：取消机制裁定、沙箱规则、权限矩阵、审计日志 | 待立项（roadmap item 7） |

## 约束锚点

- 前后端协议分歧以 [01-ws-protocol.md](01-ws-protocol.md) 为仲裁基准；传输容器边界以 `00-overview.md` §3.4/§3.7 为准。
- 三条用户硬约束（前端基座不引 Monaco / 租约续期防永久阻塞 / 每会话独立 debugger 实例）见 `00-overview.md` §一。
- 调试通道禁止无鉴权暴露；未鉴权握手必须 4401 关闭。
