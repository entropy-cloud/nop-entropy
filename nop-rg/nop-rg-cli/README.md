# nop-rg-cli

nop-rg 命令行入口（ripgrep 兼容子集）。

## 用法

```
nop-rg PATTERN [PATH] [-g glob]... [-i] [-c] [-l] [--json] [--no-ignore] [--threads N] [-r] [--jfr <file>] [--delegate-rg[=path]]
```

| 参数 | 语义 |
| --- | --- |
| `-g/--glob` | 包含/排除 glob（可重复，`!` 前缀排除，rg/globset 语义） |
| `-i` | 忽略大小写（ASCII 折叠；非 ASCII 精确匹配） |
| `-c` | 输出每个文件的**匹配行数**（rg 语义，非命中次数） |
| `-l` | 只输出命中的文件路径 |
| `--json` | 输出 rg --json 兼容消息（begin/match/end；不含 summary/stats） |
| `--no-ignore` | 关闭 .gitignore 过滤（隐藏文件始终跳过，同 rg） |
| `--threads N` | 并行度（默认 CPU 核数） |
| `-r` | 按正则搜索（默认字面量；rg 默认正则、需 `-F` 才是字面量） |
| `--jfr <file>` | 搜索期间录制 JFR（CPU/分配/锁事件），结束自动 dump |
| `--delegate-rg[=path]` | 委托系统 rg 执行（其余参数原样透传；`=path` 指定 rg 可执行文件） |
| `--vector` | Vector API（SIMD）字面量搜索。前置：classpath 含 nop-rg-vector 且 JVM 加 `--add-modules jdk.incubator.vector`；模块在但孵化模块缺失时静默降级标量（stderr 提示），classpath 缺失时显式报错。`--regex` 优先于 `--vector`。**模式长度 < 16 字节时自动走标量 BMH**（长度阈值策略，plan 2267 R1：6B SIMD 实测仅为标量 72-80%，16B 起反超 2.5-3.5x） |

退出码：命中 0 / 未命中 1 / 错误 2。

## 与 rg 的已知差异（实测 rg 15.1.0）

- 文本输出恒带行号（等价 rg -n；rg 在管道下默认无行号）。
- **无条件尊重 .gitignore**；rg 仅在 git 仓库内应用 .gitignore。
- `-i` 为 ASCII 折叠；rg 为 Unicode 折叠。
- 非 UTF-8 文件的正则语义：整文件 UTF-8 解码（replacement char），rg 为字节域。
- 默认字面量搜索；rg 默认正则。
- 大文件（>256MB，可配置）走分块扫描路径；该路径暂不支持正则（显式报错）。

## JFR 录制分析

录制产物为标准 .jfr，可用 JDK 自带工具或 JMC 分析：

```bash
jfr summary search.jfr
jfr print --events jdk.ExecutionSample search.jfr | head -100
```

配置 `nop-rg-jfr.jfc` 启用：ExecutionSample（CPU 热点）、ObjectAllocationInNewTLAB/OutsideTLAB（分配热点）、
JavaMonitorWait/Enter（锁竞争）、FileRead（仅 read API，mmap 路径无此事件）、GCHeapStatistics、CPULoad。

## 运行测试

```bash
./mvnw test -pl nop-rg/nop-rg-cli -am          # 单测 + 与系统 rg 的对比测试（rg 不可用时自动跳过对比）
```
