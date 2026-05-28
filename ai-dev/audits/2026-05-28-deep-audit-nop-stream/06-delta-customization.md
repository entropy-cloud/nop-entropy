# 维度 06: Delta 定制合规性

## 适用性
不适用 - nop-stream 是引擎模块，不使用 Delta 定制机制（无 x:extends / x:override 模式）。模块中没有 `.xml` 模型文件需要 Delta 定制。CEP 的 XDSL 模型通过 precompile 生成，不涉及 Delta 覆盖。
