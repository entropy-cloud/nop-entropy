# nop-plugin 增强设计：愿景

**日期**：2026-08-14
**范围**：`nop-core-framework/nop-plugin`（api / manager / support）
**状态**：草案（plan-first，待 plan audit 后实施）
**灵感来源**：Cordis _A Programming Paradigm for Spatiotemporal Composability_（temporal/spatial composability、revertible effects、reactive coeffects）

---

## 一、增强目标

现有 `nop-plugin` 已实现 load/unload + plugin=子容器模式（`AbstractPlugin.doStart()` 用 `loadFromResource(id, resource, BeanContainer.instance())` 创建以宿主为父的子容器；`doStop()` 调 `beanContainer.stop()` 自动 destroy 全部 bean）。本次增强在此基础上吸收 Cordis 思想，补齐四项能力：

1. **加载/激活两态分离**：plugin 引入 LOADED / ACTIVATED / DEACTIVATED 状态。加载只产出静态定义（BeansModel），激活才实例化——条件满足时激活、不满足时去激活，无需重新加载。
2. **Revertible effects 系统化**：从手动 `IPluginCancelToken.appendOnCancel` 提升为自动 effect 跟踪，统一聚合 bean destroy + subscription cancel + 手动回调为可观测列表，便于 quiescence 验证与调试。
3. **Reactive coeffect 条件激活**：基于依赖/配置的运行时条件性激活/去激活（activating / deactivating / neutral），区别于 build 时一次性条件装配。
4. **HMR**：plugin 的 beans.xml（XDSL）变更 → loader 依赖追踪 → 自动 reload，无需重启宿主。

## 二、不可违反的约束

1. **不改 nop-ioc 核心**：`BeanContainerImpl` 的 `enabledBeans` 保持 `final`，不引入运行时动态 register/unregister。plugin 可逆性通过"子容器 create/stop"实现，而非活容器动态增删——IoC 的不可变性符合可逆计算的静态结构哲学。
2. **结构层保持节点级 Delta 优势**：plugin 定义继续用 beans.xml（完整 XDSL，`_BeansModel extends AbstractDslModel`），不退回到配置行级。这是相对 Cordis 的核心优势，不可放弃。
3. **loader 被动模式**：加载（loader 输出静态 BeansModel）与激活（实例化）保持两段分离，loader 不主动驱动运行时——结构变更自动失效重算，运行时是独立消费者。
4. **观测等价而非完全恢复**：unload/deactivate 承认外部副作用（已发请求、已写文件、已 spawn 子进程）不可逆，追求 quiescence（静默等价），不奢求信息守恒的精确复原。与 Cordis 论文 §3.3.2 的观测等价立场一致。

## 三、Non-goals

1. **不实现 Cordis 式形式化演算/元理论**（Preservation/Confluence 定理证明）：本设计吸收 Cordis 的工程思想，不照搬其 PL 理论形式化。
2. **不引入新的差量空间**：结构层沿用 XDSL/XDef，不为 plugin 另造差量坐标系。
3. **不替代 `<ioc:condition>` / `<ioc:collect-beans>`**：build 时条件装配保留，coeffect 条件激活是运行时补充，二者并存。
4. **不做跨进程 plugin 编排**：本设计限单 JVM 内的 plugin 生命周期。远程 plugin 仅复用现有 `HttpPluginResourceResolver`（下载 uber jar），不涉及跨进程协调。

## 四、成功标准

1. plugin 可处于"已加载未激活"态，依赖/配置条件变化时运行时激活/去激活，无需 reload。
2. plugin 的所有可逆操作（bean destroy / subscription cancel / 手动回调）聚合成统一可观测 effect 列表；unload/deactivate 后列表清空、达 quiescence，可被测试断言。
3. beans.xml 修改后，plugin 经 loader 依赖追踪自动 reload，宿主容器与其它 plugin 不受影响。
4. 上述增强不修改 `nop-ioc` 任何公开接口与核心实现（`BeanContainerImpl` / `IBeanContainer` 不变）。

## 五、设计收敛路径

`00-vision`（本文）→ `01-architecture-baseline`（架构决策与接口契约）→ `ai-dev/plans/`（拆分实施计划）→ 实施 → 关键结论同步到 `docs-for-ai/`。
