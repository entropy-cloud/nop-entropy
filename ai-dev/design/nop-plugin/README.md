# nop-plugin 设计文档索引

> 本目录按 AGE（Attractor-Guided Engineering）owner-doc 模式组织。

## 定位

记录 `nop-plugin` 模块（`nop-core-framework/nop-plugin`：api / manager / support）的架构决策与使用契约。

**当前设计基调（2026-08-22 定位反转，见 `00-vision.md` §〇）**：plugin 只做**粗粒度引入与激活门控**——远程/本地 artifact 加载、声明式结构贡献（beans 节点级 Delta）、插件级依赖条件激活（load ≠ activate）。细粒度定制归编译期结构空间（Delta x-extends），请求级差异归上下文数据与领域层。多实例/fiber 与服务级依赖已从设计中移除（non-goals 6/7）。

本目录只记录架构决策层面（选了什么、为什么、拒绝了什么），不记录代码实现细节——源码是代码层面的唯一事实。

## 文档结构

| 文档 | 层级 | 职责 |
|------|------|------|
| `00-vision.md` | Vision | 定位反转裁决、增强目标（三项）、不可违反约束、non-goals、成功标准 |
| `01-architecture-baseline.md` | Architecture Baseline | 单层状态机、effect 系统化、coeffect 插件级门控、HMR、核心接口契约（含实例机制删除去向表）、模块边界 |
| `02-dsh-usage-coverage.md` | 评估 | 对照 dsh plugin 用法的覆盖度（E/I/H 改判 agent 层职责；F 收敛为纯插件级） |
| `03-coeffect-and-agent-example.md` | 使用示例 | coeffect 用法 + agent 场景单激活流组装示例 + dsh 同场景对照 |
| `04-interface-comparison.md` | 接口对比 | nop-plugin 与 dsh 接口逐项对比（fiber 对应关系改判为"有意拒绝"及论证） |
| `05-artifact-loading-design.md` | artifact 加载 | 类加载模型（parent=平台、plugin 类从 jar）+ 加载器接口（URL 下载到本地 repository + SHA256 校验）——不受反转影响 |

## 阅读顺序

1. 必读：`00-vision.md` §〇 —— 定位反转的根本裁决与认识论边界论证。
2. 必读：`01-architecture-baseline.md` —— 反转后的状态机与接口契约。
3. 按需：`04-interface-comparison.md` §2.4 —— 为什么 Nop 有意不做 fiber。
4. 按需：源码锚点见各文档末尾（标注 ★ 的条目待 R1-R4 重构，见 roadmap）。

## 关联设计

- `../nop-ioc/bean-dependency-semantics.md` —— plugin 的子容器复用 IoC 的 bean 依赖语义。
- `../xlang-scope-access-design.md` —— scope 访问机制。
- `../../analysis/2026-08/2026-08-21-dsh-plugin-system-reference.md` —— dsh 插件系统机制参考（三层模型/生命周期/可逆性语义的机制级调研基线）。
- `../../articles/dsh-architecture-from-reversible-computation.md` —— 理论解读（§4.6 认识论边界是本次定位反转的理论依据）。
