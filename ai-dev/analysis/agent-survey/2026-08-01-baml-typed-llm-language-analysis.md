# BAML 多 IR 编译与类型化 LLM 调用分析 & Nop AI Agent DSL/类型

> Status: open
> Date: 2026-08-01
> Scope: `~/ai/baml`（Rust+TS+Go，面向 agent 的编程语言 + VM，3838 文件）vs `nop-ai-agent`（DSL-first）
> Conclusion:

## 一、总览与机制
BAML 是"agent 的编程语言"，类 TS 语法 + Rust 类型系统，把 LLM 调用做成语言一等类型化操作。核心：**多 IR 编译管线**（compiler2_ast→hir scope-tree→tir per-scope 类型推断 Salsa 增量查询→mir lower/optimize→ppir→字节码 emit + bytecode_cache）；**BEX 运行时**（bex_vm 栈式 VM 同步跑直到 I/O 才 yield + bex_engine 异步驱动仿 Deno 嵌 V8）；**绿色线程 + 分代 GC**（spawn/await 用堆 Future，safepoint permit 协调）；**stdlib 用 BAML 自写**（ns_llm/http 等内置模块本身是 .baml 源码）。

## 二、对 nop-ai-agent 的借鉴要点
1. **Salsa 增量查询式编译**（高价值，DSL-first）——启发 nop DSL 的增量解析/校验（只重算变更影响范围），提升大型 DSL 配置的编译性能。
2. **VM 同步执行 + I/O yield 执行模型**（中价值）——给"静态模型 + 解释执行"提供参考（对应 rivet Actor sleep/wake `2026-08-01-rivet-actor-runtime-analysis.md`：VM 同步直到 I/O 让出）。
3. **类型化 LLM 调用 + 编译期错误分析**（高价值）——强化 DSL-first 的类型保证：LLM 调用的输入/输出在编译期静态分析，而非运行时才发现错误。

## 三、结论
BAML 的 Salsa 增量编译 + 类型化 LLM 调用是 nop DSL-first 的高级参照（增量编译/类型保证）。局限：学习曲线极高、自研 VM/GC/语言全套是巨大投入、与宿主语言 FFI 桥接复杂——借鉴设计理念而非实现。

## References
- `~/ai/baml/`（compiler/、hir/、mir/、vm/、engine/）
- `ai-dev/design/nop-ai-agent/nop-ai-agent-dsl.md`
- `ai-dev/analysis/agent-survey/2026-08-01-rivet-actor-runtime-analysis.md`
