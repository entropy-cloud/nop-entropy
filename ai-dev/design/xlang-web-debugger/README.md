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
| [02-lease-lifecycle.md](02-lease-lifecycle.md) | 子域详设（D2） | 租约续期与生命周期：per-session 时钟 + per-thread 判定、TTL 30s/续期 10s/宽限 15s、租约到期（放行不销毁）与宽限期满（放行+销毁）双路径、close 语义仅会话销毁、多标签页共享租约、边界时序（断网/休眠/杀页签/beforeunload） | active |
| [03-sessionization.md](03-sessionization.md) | 子域详设（D3） | 会话化：方案 A（全局外壳 + ThreadLocal 会话标识路由）裁定（双机制传递链论证拒绝方案 B）、标记契约（save-restore/编译期窗口/插入点枚举）、路由规则表（含已销毁直通）、每会话实例生命周期（含 notifier 接线）、单步定向语义从零重建（per-thread 唤醒契约）、IDEA 默认会话零改动 | active |
| [04-execution-backend-link.md](04-execution-backend-link.md) | 子域详设（D4） | 调试模式与执行后端联动：三个静态绑定直通落点的运行期降级（单谓词多落点，debugger.enabled 直读）、递归终止性论证、四开关联动矩阵、运行期切换作用域限定、性能评估 | active |
| [05-frontend-architecture.md](05-frontend-architecture.md) | 子域详设（D5） | flux 前端架构：两新包（flux-xlang-lang/flux-xlang-debug）职责边界 + editor registry 开放化（三分发点收敛、半开放类型裁定、替换语义）+ 构建链（generated/ 落点与 check:src-artifacts/oversized 等门禁豁免清单、.d.ts shim、打包面）+ 诊断桥注入式工厂（编译端点 owner 归 item 14/D6）+ zustand 双向状态桥（多线程挂起 schema 对齐 D1）+ 跨仓交付影响登记 | active |
| [06-grammar-spike.md](06-grammar-spike.md) | spike 记录（D5 附） | Lezer grammar 三来源裁定（新写采纳/treesitter 拒绝/ANTLR 转译降为 token 语义对照）、POC 全绿证据（0 错误节点 + 12 项结构断言 + 假绿纠偏记录）、一期范围与 6 项 follow-up（含后缀 ++）、11 条工程坑清单、item 15 实现输入 | active |
| [07-repl-security.md](07-repl-security.md) | 子域详设（D6） | REPL 安全：装饰器层 per-entry 取消（LintDeadlineExecutor 先例复用 + Error 系取消异常裁定 + 残余兜底诚实声明）、沙箱纵深防御规则表（白名单默认拒绝 + 危险构造一期无运行时拦截声明）、权限矩阵/审计事件/限制表、compile 端点 schema（兑现 05 open question）、D3 plainExecute 语义修订 | active |

## 约束锚点

- 前后端协议分歧以 [01-ws-protocol.md](01-ws-protocol.md) 为仲裁基准；传输容器边界以 `00-overview.md` §3.4/§3.7 为准。
- 三条用户硬约束（前端基座不引 Monaco / 租约续期防永久阻塞 / 每会话独立 debugger 实例）见 `00-overview.md` §一。
- 调试通道禁止无鉴权暴露；未鉴权握手必须 4401 关闭。
