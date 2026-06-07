# Nop AI Shell 支持的 Shell 语法规范

**日期**：2026-06-07
**范围**：`nop-ai-shell` 模块 — Bash 子集语法参考
**状态**：active

---

## 一、设计原则

本规范定义 `nop-ai-shell` 支持的 bash 语法子集。设计原则：

1. **覆盖 LLM 常见输出** — 只实现 LLM（特别是 coding agent LLM）实际会生成的语法
2. **渐进式支持** — 分 Tier 1/2/3，低 Tier 先实现
3. **安全优先** — 不支持的功能返回明确错误，不静默忽略
4. **与现有 parser 对齐** — `BashSyntaxParser` 已经支持的语法直接标记状态

---

## 二、语法总览

### 支持状态图例

| 符号 | 含义 |
|------|------|
| ✅ | 已实现（parser + executor 均工作） |
| 🔧 | parser 已实现，executor 需要修复 |
| 📋 | 待实现 |
| ❌ | 不支持（有意拒绝） |

---

## 三、Tier 1 — 基础语法（必须支持）

### 3.1 简单命令

```
command [arg1] [arg2] ... [argN]
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 命令名 | `ls` | ✅ | 查找 ShellCommandRegistry |
| 参数 | `echo hello world` | ✅ | 空格分隔 |
| 单引号 | `echo 'hello world'` | ✅ | 原样内容，不展开 |
| 双引号 | `echo "hello world"` | ✅ | 保留空格（当前不展开变量，Tier 2 增强） |
| 数字参数 | `head -5 file.txt` | ✅ | 解析为 ARGUMENT token |
| 未注册命令 | `python script.py` | ✅ | 返回 exit code 127 |

### 3.2 环境变量传递

```
VAR=value command
export VAR=value
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 局部变量 | `FOO=bar echo $FOO` | ✅ parser | 仅对该命令可见 |
| 导出变量 | `export FOO=bar` | ✅ parser | 对后续所有命令可见 |
| 多变量 | `A=1 B=2 cmd` | ✅ parser | 顺序传递 |

### 3.3 管道

```
cmd1 | cmd2 | cmd3
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 双命令管道 | `ls | grep foo` | 🔧 | parser 完成，executor 需修复 |
| 多级管道 | `cat file | grep foo | wc -l` | 🔧 | 同上 |
| 管道 + 重定向 | `cmd1 | cmd2 > out.txt` | 🔧 | parser 完成 |
| 管道 + 逻辑 | `cmd1 | cmd2 && cmd3` | 🔧 | 优先级：pipe > && |

### 3.4 逻辑运算符

```
cmd1 && cmd2    # cmd1 成功才执行 cmd2
cmd1 || cmd2    # cmd1 失败才执行 cmd2
cmd1 ; cmd2     # 顺序执行
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| AND | `mkdir dir && cd dir` | 🔧 | 短路：左侧失败跳过右侧 |
| OR | `test -f file || echo "missing"` | 🔧 | 短路：左侧成功跳过右侧 |
| 分号 | `echo a; echo b` | 🔧 | 顺序执行 |
| 混合 | `cmd1 && cmd2 || cmd3` | 🔧 | && 优先级高于 \|\| |

**优先级**：pipe (1) > OR (3) > AND (4) > SEMICOLON (2)

> 注意：当前 parser 中 SEMICOLON precedence=2, OR=3, AND=4。这意味着 `&&` 比 `||` 绑定更紧，与 bash 一致。

### 3.5 I/O 重定向

```
cmd > file      # stdout 到文件
cmd >> file     # stdout 追加到文件
cmd < file      # 从文件读 stdin
cmd 2> file     # stderr 到文件
cmd 2>&1        # stderr 合并到 stdout
cmd &> file     # stdout + stderr 到文件
cmd &>> file    # stdout + stderr 追加
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 输出重定向 | `echo hello > file.txt` | 🔧 | FileShellOutput |
| 追加重定向 | `echo world >> file.txt` | 🔧 | FileShellOutput(append) |
| 输入重定向 | `grep foo < input.txt` | 🔧 | FileShellInput |
| stderr 重定向 | `cmd 2> err.txt` | 🔧 | sourceFd=2 |
| FD 合并 | `cmd 2>&1` | 🔧 | DuplexShellOutput |
| 合并输出 | `cmd &> all.txt` | 🔧 | TeeOutput |
| 合并追加 | `cmd &>> all.txt` | 🔧 | TeeOutput(append) |

### 3.6 Here Document / Here String

```
cmd << EOF
  content
EOF

cmd <<< "single line"
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| Here doc | `cat << EOF` | 📋 | parser 识别 token，executor 待实现 |
| Here string | `grep foo <<< "hello"` | 📋 | 同上 |

### 3.7 分组和子 Shell

```
{ cmd1; cmd2; }     # 当前 shell 中执行
(cmd1; cmd2)        # 子 shell 中执行
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 大括号分组 | `{ echo a; echo b; }` | 🔧 | 共享环境变量，执行后恢复 |
| 子 shell | `(cd /tmp && pwd)` | 🔧 | 隔离环境变量，执行后恢复 |
| 重定向 | `{ cmd1; cmd2; } > out` | 🔧 | 分组级重定向 |

### 3.8 后台执行

```
cmd &
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 后台 | `sleep 10 &` | 🔧 | 不等待完成，立即返回 exitCode=0 |

### 3.9 注释

```
# this is a comment
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 行注释 | `echo hello # comment` | 📋 | parser 需要跳过 `#` 后内容 |

---

## 四、Tier 2 — 变量和扩展（重要增强）

### 4.1 变量展开

这是对 AI Agent 最重要的增强——LLM 经常在命令中引用变量。

```
$VAR            → 环境变量值
${VAR}          → 环境变量值
$?              → 上一个命令的 exitCode
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| 简单变量 | `echo $HOME` | 📋 | 在参数展开阶段替换 |
| 大括号变量 | `echo ${HOME}/path` | 📋 | 同上 |
| 上一退出码 | `echo $?` | 📋 | `lastExitCode` |
| 未定义变量 | `echo $UNDEFINED` | 📋 | 替换为空字符串（bash 默认语义） |
| 默认值 | `echo ${VAR:-default}` | 📋 | VAR 未定义时使用 default |
| 替换值 | `echo ${VAR:+alternate}` | 📋 | VAR 已定义时使用 alternate |

**展开阶段**：在 AST 构建完成后、命令分发前，对所有 `SimpleCommand` 的 `command` 和 `args` 执行变量替换。

**展开规则**：
- 单引号内**不展开**：`echo '$HOME'` → 输出 `$HOME`
- 双引号内**展开**：`echo "$HOME"` → 输出实际值
- 无引号时**展开**：`echo $HOME` → 输出实际值

### 4.2 通配符展开

```
*.txt           → 匹配所有 .txt 文件
dir/*.java      → 匹配 dir 下所有 .java 文件
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| `*` | `ls *.java` | 📋 | 委托给 `IToolFileSystem.glob()` |
| `?` | `ls file?.txt` | 📋 | 单字符匹配 |
| `**` | `find . -name "**/*.java"` | 📋 | 递归匹配 |

**展开阶段**：在变量展开之后执行。每个包含 glob 字符的参数替换为匹配的文件列表。

### 4.3 Tilde 展开

```
~/path          → 用户 home 目录
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| `~` | `cd ~/project` | 📋 | 替换为配置的 home 目录 |
| `~/` | `cat ~/file.txt` | 📋 | 同上 |

---

## 五、Tier 3 — 高级语法（按需实现）

### 5.1 命令替换

```
$(command)      → 命令输出替换
`command`       → 同上（反引号形式）
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| `$()` | `echo $(pwd)` | ❌ P2 | 需要递归解析和执行 |
| `` ` `` `` | `` echo `pwd` `` | ❌ P2 | 同上 |

### 5.2 算术展开

```
$((expression)) → 算术计算结果
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| `$((expr))` | `echo $((1 + 2))` | ❌ P2 | 简单算术 |

### 5.3 控制流

```
if condition; then commands; fi
for var in list; do commands; done
while condition; do commands; done
case value in pattern) commands;; esac
```

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| `if/then/fi` | `if [ -f x ]; then echo yes; fi` | ❌ | LLM 很少生成复杂控制流 |
| `for/do/done` | `for f in *.txt; do cat $f; done` | ❌ | 可用管道+xargs 替代 |
| `while/do/done` | `while true; do ...; done` | ❌ | 危险（可能无限循环） |
| `case/esac` | `case $x in a) ...;; esac` | ❌ | 极少使用 |

### 5.4 函数

```
fname() { commands; }
```

| 语法 | 状态 | 说明 |
|------|------|------|
| 函数定义 | ❌ | LLM 不生成函数定义 |

### 5.5 其他

| 语法 | 示例 | 状态 | 说明 |
|------|------|------|------|
| Brace expansion | `echo {a,b,c}` | ❌ | 可用枚举替代 |
| Process substitution | `diff <(cmd1) <(cmd2)` | ❌ | 过于复杂 |
| Arrays | `${arr[@]}` | ❌ | 不需要 |

---

## 六、命令行为规范

### 6.1 Exit Code 约定

所有命令必须遵循 bash exit code 约定：

| Exit Code | 含义 |
|-----------|------|
| 0 | 成功 |
| 1 | 一般错误 |
| 2 | 用法错误（参数不对） |
| 126 | 命令不可执行（权限） |
| 127 | 命令未找到 |
| 128+N | 信号 N 导致终止 |
| 124 | 超时（与 timeout 命令一致） |

### 6.2 Stdout/Stderr 约定

- 正常输出写到 stdout
- 错误和诊断信息写到 stderr
- stdout 和 stderr 分开收集，传递回 toolkit 时分别放在 `<output>` 和 `<error>` 中

### 6.3 信号处理

nop-ai-shell 虚拟 shell 不支持真实信号。但以下行为有对应：

| 信号 | 模拟行为 |
|------|---------|
| SIGINT (Ctrl+C) | `ICancelToken.cancel()` → 所有命令检查并退出 |
| SIGTERM | 超时触发取消，等价于 SIGTERM |
| SIGKILL | 不模拟（无法被捕获） |
| SIGHUP | 不模拟 |

---

## 七、Parser 与语法的对应

### 7.1 已在 Parser 中支持的语法

`BashSyntaxParser` 已正确解析以下语法：

| 语法 | Parser 中的处理 |
|------|---------------|
| 简单命令 | `parseBaseCommand()` → SimpleCommand |
| 管道 | `parsePipeline()` → PipelineExpr |
| && / \|\| / ; | `parseExpression()` → LogicalExpr |
| () 子 shell | `parseSubshell()` → SubshellExpr |
| {} 分组 | `parseGroup()` → GroupExpr |
| & 后台 | `parsePrimary()` → BackgroundExpr |
| 重定向 | `parseRedirect()` / `parseRedirectWithFd()` → Redirect |
| 环境变量 | `parseEnvAssignment()` → EnvVar |
| 单/双引号 | `tryMatchQuoted()` → QUOTED_SINGLE/QUOTED_DOUBLE |

### 7.2 Parser 缺失的语法

| 语法 | 需要的改动 |
|------|-----------|
| 变量展开 `$VAR` | 在 executor 的展开阶段处理（不改 parser） |
| 通配符 `*` | 在 executor 的展开阶段处理（不改 parser） |
| 注释 `#` | Lexer 中跳过 `#` 到行尾 |
| 反引号 `` ` `` | Lexer 识别为新的 token type |
| Here doc 内容 | Parser 需要读取到 delimiter 行 |

### 7.3 需要修复的 Parser 问题

1. **`>&` vs `&>` 区分不清**：Lexer 的 `tryMatchOperators()` 对 `>&` 和 `&>` 的匹配顺序有问题（先匹配两个字符，但 `&>` 在 `twoChar` 分支中匹配为 MERGE，而 `>&` 应该是 FD_OUTPUT）
2. **`<<<` 匹配问题**：`twoChar` 分支中先匹配了 `<<`（HERE_DOC），三个字符的 `<<<` 需要先在 `threeChar` 分支匹配
3. **注释 `#`**：Lexer 不识别注释，`#` 会被当作命令名

---

## 八、LLM 常见命令模式参考

以下是从 OpenCode、Claude Code、nanobot、Codex CLI 等框架中观察到的 LLM 最常生成的 shell 命令模式：

### 8.1 文件查看（最高频）

```bash
cat file.txt
head -20 file.py
tail -50 file.java
wc -l src/**/*.java
```

### 8.2 文件搜索（最高频）

```bash
grep -r "pattern" src/
find . -name "*.java"
grep -rn "TODO" --include="*.java" .
```

### 8.3 文件操作（高频）

```bash
mkdir -p src/main/java/com/example
cp file.txt backup/
mv old_name.txt new_name.txt
rm -rf build/
```

### 8.4 构建和测试（高频）

```bash
mvn compile
mvn test -pl nop-core
npm install && npm test
```

### 8.5 Git 操作（高频）

```bash
git status
git diff
git log --oneline -10
git add -A && git commit -m "message"
```

### 8.6 管道组合（中频）

```bash
cat file | grep pattern | wc -l
find . -name "*.java" | xargs grep "class"
echo "content" > file.txt
```

### 8.7 条件执行（低频）

```bash
test -f file && echo "exists"
[ -d dir ] || mkdir dir
```

---

## 九、语法不支持时的行为

当 LLM 生成了不支持的语法时：

| 情况 | 行为 |
|------|------|
| 未注册命令 | exitCode=127, stderr="command not found: xxx" |
| 未支持的变量展开 | 原样传递（不替换），stderr 发警告 |
| 控制流关键字 (if/for/while) | exitCode=2, stderr="syntax error: unsupported construct" |
| 未支持的展开 ($(), $(()), {}) | exitCode=2, stderr="unsupported expansion: $()" |
| 解析错误 | BashSyntaxParser.ParseException, stderr 包含错误位置 |

---

## 十、拒绝了什么

### 拒绝：完整 bash 兼容

**方案**：实现 bash 的完整语法集（包括控制流、函数、数组、进程替换等）。

**拒绝理由**：LLM（特别是 coding agent）只生成 bash 的基础子集。完整兼容需要 15000+ 行代码（brush-shell 规模），收益不成比例。且完整 bash 兼容增加攻击面。

### 拒绝：控制流语句（if/for/while/case）

**方案**：解析和执行 `if/then/fi`、`for/do/done`、`while/do/done` 等控制流。

**拒绝理由**：LLM 极少生成复杂 shell 控制流。条件执行可用 `&&`/`||` 替代，循环可用管道 + `xargs` 替代。控制流引入无限循环风险（`while true`），在虚拟 shell 中难以安全控制。

### 拒绝：命令替换 `$()` 和反引号

**方案**：支持 `$(cmd)` 和 `` `cmd` `` 将命令输出嵌入参数。

**拒绝理由**：命令替换需要递归解析和执行，实现复杂度高。LLM 在 Agent 场景中通常分步执行而非嵌套命令替换。

### 拒绝：静默忽略不支持的语法

**方案**：遇到不支持的语法时不报错，尽量执行能识别的部分。

**拒绝理由**：静默忽略会导致 LLM 误以为命令成功执行，产生幻觉。明确错误（exitCode + stderr 描述）让 LLM 自行纠正策略。

---

## 十一、与 bash.tool.xml 的关系

nop-ai-shell 虚拟 shell 作为 `bash` tool 的替代 executor，使用相同的 tool schema：

```xml
<bash id="1" explanation="list files" timeoutMs="30000" workingDir="/workspace">
    <command>ls -la src/</command>
    <envs>
        <env name="HOME" value="/workspace"/>
    </envs>
</bash>
```

`ShellBashExecutor` 从 `<command>` 提取命令行字符串，传给 `ShellCommandExecutor`。返回格式与真实 `BashExecutor` 完全一致：

```xml
<tool-result id="1" status="success" exitCode="0">
    <output>file1.txt
file2.java
dir1</output>
</tool-result>
```

---

## 与其他文档的关系

- `nop-ai-shell-design.md` — nop-ai-shell 模块设计（本篇的姊妹篇）
- `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/parser/BashSyntaxParser.java` — parser 源码
- `nop-ai/nop-ai-shell/src/main/java/io/nop/ai/shell/model/BashLexer.java` — lexer 源码
- `nop-ai/nop-ai-shell/src/test/java/io/nop/ai/shell/parser/BashSyntaxParserTest.java` — parser 测试用例
