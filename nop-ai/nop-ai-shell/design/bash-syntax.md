# Bash命令行模型设计文档

## 1. 概述

本文档定义Bash命令行解析的Java模型对象。该模型专注于**语法结构表示**，不涉及执行逻辑、环境管理或具体解析过程。模型采用**表达式树**结构，准确反映Bash命令的嵌套关系和运算符优先级。

## 2. 核心设计原则

1. **树形结构**：命令行是嵌套的表达式树，不是线性序列
2. **组合模式**：所有元素实现统一的表达式接口
3. **类型安全**：使用具体类而非`List<Object>`混合类型
4. **完整性**：支持Bash主要语法元素
5. **不可变性**：所有模型对象不可变，线程安全
6. **原始性**：存储原始字符串，不进行语义展开

## 3. 运算符优先级（从高到低）

| 优先级 | 运算符 | 说明 |
|--------|--------|------|
| 1 | `()`, `{}` | 括号和大括号分组 |
| 2 | 重定向 (`>`, `<`, `>>`, `2>&1`等) | 绑定到当前命令 |
| 3 | 管道 (`\|`) | 从左到右结合 |
| 4 | 逻辑与 (`&&`) | 从左到右结合 |
| 5 | 逻辑或 (`\|\|`) | 从左到右结合 |
| 6 | 顺序执行 (`;`) | 最低优先级 |
| 7 | 后台运行 (`&`) | 最低优先级 |

## 4. 核心接口与类

### 4.1 CommandExpression (表达式接口)

所有命令行元素的基接口。

```java
/**
 * 命令行表达式基接口
 * 所有命令行元素都实现此接口，形成表达式树
 */
public interface CommandExpression {
    
    /**
     * 返回表达式的字符串表示
     * 应能重建原始命令行（可能格式化不同）
     */
    String toString();
    
    /**
     * 接受访问者遍历
     */
    <T> T accept(CommandVisitor<T> visitor);
}
```

### 4.2 CommandVisitor (访问者接口)

```java
/**
 * 访问者模式接口，用于遍历表达式树
 */
public interface CommandVisitor<T> {
    T visit(SimpleCommand cmd);
    T visit(PipelineExpr pipe);
    T visit(LogicalExpr logical);
    T visit(GroupExpr group);
    T visit(SubshellExpr subshell);
    T visit(BackgroundExpr background);
}
```

## 5. 命令与参数

### 5.1 SimpleCommand (简单命令)

```java
/**
 * 简单命令 - 原子执行单元
 * 存储原始字符串，不进行变量展开或通配符扩展
 * 
 * 示例: 
 * - ls -l *.txt           -> command="ls", args=["-l", "*.txt"]
 * - VAR=value echo "test" -> envVars={"VAR":"value"}, command="echo", args=["\"test\""]
 * - cmd > out.txt 2>&1    -> command="cmd", redirects=[Redirect(">","out.txt"), Redirect("2>&1","1")]
 */
public final class SimpleCommand implements CommandExpression {
    
    // 命令特有环境变量（只影响该命令）
    // 存储EnvVar对象，包含类型和展开控制信息
    private final List<EnvVar> envVars;
    
    // 命令名称（原始字符串，可能包含变量如"$MYCMD"）
    private final String command;
    
    // 命令参数列表（原始字符串，包含引号、变量、通配符等）
    private final List<String> args;
    
    // 该命令的重定向列表（一个命令可以有多个重定向）
    private final List<Redirect> redirects;
    
    // 私有构造方法
    private SimpleCommand(String command, 
                         List<String> args,
                         List<EnvVar> envVars,
                         List<Redirect> redirects) {
        this.command = command;
        this.args = List.copyOf(args);
        this.envVars = List.copyOf(envVars);
        this.redirects = List.copyOf(redirects);
    }
    
    // Builder模式
    public static Builder builder(String command) {
        return new Builder(command);
    }
    
    public static class Builder {
        private final String command;
        private final List<String> args = new ArrayList<>();
        private final List<EnvVar> envVars = new ArrayList<>();
        private final List<Redirect> redirects = new ArrayList<>();
        
        public Builder(String command) {
            this.command = Objects.requireNonNull(command, "Command cannot be null");
        }
        
        public Builder arg(String arg) {
            args.add(arg);
            return this;
        }
        
        public Builder args(String... args) {
            Collections.addAll(this.args, args);
            return this;
        }
        
        public Builder envVar(String key, String value) {
            envVars.add(EnvVar.local(key, value));
            return this;
        }
        
        public Builder envVar(EnvVar envVar) {
            envVars.add(envVar);
            return this;
        }
        
        public Builder redirect(Redirect redirect) {
            redirects.add(redirect);
            return this;
        }
        
        public SimpleCommand build() {
            return new SimpleCommand(command, args, envVars, redirects);
        }
    }
    
    // Getters
    public String command() { return command; }
    public List<String> args() { return args; }
    public List<EnvVar> envVars() { return envVars; }
    public List<Redirect> redirects() { return redirects; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // 环境变量
        for (EnvVar envVar : envVars) {
            if (envVar.type() == EnvVar.Type.EXPORT) {
                sb.append("export ");
            }
            sb.append(envVar.name()).append("=").append(envVar.value()).append(" ");
        }
        
        // 命令名
        sb.append(command);
        
        // 参数
        for (String arg : args) {
            sb.append(" ").append(arg);
        }
        
        // 重定向
        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }
        
        return sb.toString().trim();
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

## 6. 表达式组合

### 6.1 PipelineExpr (管道表达式)

```java
/**
 * 管道表达式 - 连接多个命令
 * 优先级：高于逻辑运算符，低于括号
 * 
 * 示例: 
 * - cmd1 | cmd2 | cmd3
 * - (cmd1 && cmd2) | cmd3
 */
public final class PipelineExpr implements CommandExpression {
    
    // 管道中的命令序列（至少2个）
    private final List<CommandExpression> commands;
    
    // 私有构造方法
    private PipelineExpr(List<CommandExpression> commands) {
        if (commands.size() < 2) {
            throw new IllegalArgumentException("Pipeline must have at least 2 commands");
        }
        this.commands = List.copyOf(commands);
    }
    
    // Builder模式
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private final List<CommandExpression> commands = new ArrayList<>();
        
        public Builder command(CommandExpression cmd) {
            commands.add(cmd);
            return this;
        }
        
        public PipelineExpr build() {
            return new PipelineExpr(commands);
        }
    }
    
    // 工厂方法已移至Commands类
    
    // Getters
    public List<CommandExpression> commands() {
        return commands;
    }
    
    @Override
    public String toString() {
        return commands.stream()
            .map(CommandExpression::toString)
            .collect(Collectors.joining(" | "));
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

### 6.2 LogicalExpr (逻辑表达式)

```java
/**
 * 逻辑表达式 - 连接两个子表达式
 * 优先级：&& 高于 || 高于 ;
 * 
 * 示例: 
 * - cmd1 && cmd2      (逻辑与)
 * - cmd1 || cmd2      (逻辑或)
 * - cmd1 ; cmd2       (顺序执行)
 */
public final class LogicalExpr implements CommandExpression {
    
    public enum Operator {
        AND("&&", 3),      // 逻辑与（优先级3）
        OR("||", 2),       // 逻辑或（优先级2）
        SEMICOLON(";", 1); // 顺序执行（优先级1）
        
        private final String symbol;
        private final int precedence;
        
        Operator(String symbol, int precedence) { 
            this.symbol = symbol; 
            this.precedence = precedence;
        }
        
        public String symbol() { return symbol; }
        public int precedence() { return precedence; }
        
        public static Operator fromSymbol(String symbol) {
            return switch (symbol) {
                case "&&" -> AND;
                case "||" -> OR;
                case ";" -> SEMICOLON;
                default -> throw new IllegalArgumentException("Unknown operator: " + symbol);
            };
        }
    }
    
    private final CommandExpression left;
    private final Operator operator;
    private final CommandExpression right;
    
    public LogicalExpr(CommandExpression left, Operator operator, CommandExpression right) {
        this.left = Objects.requireNonNull(left, "Left expression cannot be null");
        this.operator = Objects.requireNonNull(operator, "Operator cannot be null");
        this.right = Objects.requireNonNull(right, "Right expression cannot be null");
    }
    
    // 工厂方法已移至Commands类
    
    // Getters
    public CommandExpression left() { return left; }
    public Operator operator() { return operator; }
    public CommandExpression right() { return right; }
    
    @Override
    public String toString() {
        String leftStr = formatOperand(left, operator, false);
        String rightStr = formatOperand(right, operator, true);
        return leftStr + " " + operator.symbol() + " " + rightStr;
    }
    
    // 根据优先级决定是否需要括号
    private String formatOperand(CommandExpression expr, Operator parentOp, boolean isRight) {
        if (expr instanceof LogicalExpr) {
            LogicalExpr logical = (LogicalExpr) expr;
            if (logical.operator.precedence() < parentOp.precedence() ||
                (isRight && logical.operator.precedence() == parentOp.precedence())) {
                return "(" + expr + ")";
            }
        }
        return expr.toString();
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

### 6.3 GroupExpr (大括号分组)

```java
/**
 * 大括号分组 - 在当前shell中执行命令组
 * 语法要求：{ 和 } 前后必须有空格，命令间用分号分隔
 * 
 * 示例: 
 * - { echo "start"; ls; echo "end"; }
 * - { cmd1 && cmd2; } > output.txt
 */
public final class GroupExpr implements CommandExpression {
    
    // 分组中的命令序列
    private final List<CommandExpression> commands;
    
    // 整个分组的重定向
    private final List<Redirect> redirects;
    
    public GroupExpr(List<CommandExpression> commands, List<Redirect> redirects) {
        if (commands.isEmpty()) {
            throw new IllegalArgumentException("Group must have at least one command");
        }
        this.commands = List.copyOf(commands);
        this.redirects = List.copyOf(redirects);
    }
    
    // 工厂方法已移至Commands类
    
    // Getters
    public List<CommandExpression> commands() { return commands; }
    public List<Redirect> redirects() { return redirects; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{ ");
        
        // 连接命令，最后一个命令的分号可选
        for (int i = 0; i < commands.size(); i++) {
            sb.append(commands.get(i));
            if (i < commands.size() - 1) {
                sb.append("; ");
            } else {
                sb.append(";");  // 可选，但加上更规范
            }
        }
        
        sb.append(" }");
        
        // 分组重定向
        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }
        
        return sb.toString();
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

### 6.4 SubshellExpr (子shell表达式)

```java
/**
 * 子shell表达式 - 在子shell中执行命令
 * 语法：圆括号不要求特殊空格
 * 
 * 示例: 
 * - (cd /tmp && ls)
 * - (cmd1; cmd2) | wc -l
 */
public final class SubshellExpr implements CommandExpression {
    
    // 子shell中的表达式
    private final CommandExpression inner;
    
    // 子shell的重定向
    private final List<Redirect> redirects;
    
    public SubshellExpr(CommandExpression inner, List<Redirect> redirects) {
        this.inner = Objects.requireNonNull(inner, "Inner expression cannot be null");
        this.redirects = List.copyOf(redirects);
    }
    
    // 工厂方法已移至Commands类
    
    // Getters
    public CommandExpression inner() { return inner; }
    public List<Redirect> redirects() { return redirects; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(inner).append(")");
        
        for (Redirect redirect : redirects) {
            sb.append(" ").append(redirect);
        }
        
        return sb.toString();
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

### 6.5 BackgroundExpr (后台运行表达式)

```java
/**
 * 后台运行表达式
 * 优先级：最低，与 ; 相同
 * 
 * 示例: 
 * - cmd1 &
 * - (sleep 10; echo "done") &
 */
public final class BackgroundExpr implements CommandExpression {
    
    private final CommandExpression inner;
    
    public BackgroundExpr(CommandExpression inner) {
        this.inner = Objects.requireNonNull(inner, "Inner expression cannot be null");
    }
    
    // 工厂方法已移至Commands类
    
    // Getters
    public CommandExpression inner() { return inner; }
    
    @Override
    public String toString() {
        String innerStr = inner.toString();
        // 如果内部表达式是逻辑表达式，可能需要括号
        if (inner instanceof LogicalExpr) {
            return "(" + innerStr + ") &";
        }
        return innerStr + " &";
    }
    
    @Override
    public <T> T accept(CommandVisitor<T> visitor) {
        return visitor.visit(this);
    }
}
```

## 7. 环境变量模型

### 7.1 EnvVar (环境变量)

```java
/**
 * 环境变量
 */
public final class EnvVar {

    public enum Type {
        LOCAL,      // VAR=value command（局部）
        EXPORT,     // export VAR=value（导出）
        TEMP,       // 临时变量
        SYSTEM      // 系统变量（只读）
    }

    private final String name;
    private final String value;
    private final Type type;
    private final boolean expand;  // 是否需要展开引用

    private EnvVar(String name, String value, Type type, boolean expand) {
        this.name = name;
        this.value = value;
        this.type = type;
        this.expand = expand;
    }

    public String name() {
        return name;
    }

    public String value() {
        return value;
    }

    public Type type() {
        return type;
    }

    public boolean expand() {
        return expand;
    }

    public static EnvVar local(String name, String value) {
        return new EnvVar(name, value, Type.LOCAL, false);
    }

    public static EnvVar export(String name, String value) {
        return new EnvVar(name, value, Type.EXPORT, false);
    }

    public static EnvVar expand(String name, String value) {
        return new EnvVar(name, value, Type.LOCAL, true);
    }
}
```

## 8. 重定向模型

### 8.1 Redirect (重定向)

```java
/**
 * 重定向 - I/O重定向操作
 * 绑定到单个命令或命令组
 */
public final class Redirect {
    
    // 源文件描述符（null表示默认：0 for <, 1 for >）
    private final Integer sourceFd;
    
    // 操作符类型
    private final Type type;
    
    // 目标：文件名或文件描述符（原始字符串）
    private final String target;
    
    public enum Type {
        OUTPUT(">"),           // 输出重定向（覆盖）
        APPEND(">>"),          // 输出重定向（追加）
        INPUT("<"),            // 输入重定向
        FD_OUTPUT(">&"),       // 文件描述符输出复制
        FD_INPUT("<&"),        // 文件描述符输入复制
        MERGE("&>"),           // 合并stdout和stderr（覆盖）
        MERGE_APPEND("&>>"),   // 合并stdout和stderr（追加）
        HERE_DOC("<<"),        // Here文档
        HERE_STRING("<<<");    // Here字符串
        
        private final String symbol;
        
        Type(String symbol) { 
            this.symbol = symbol; 
        }
        
        public String symbol() { 
            return symbol; 
        }
        
        public static Type fromSymbol(String symbol) {
            return switch (symbol) {
                case ">" -> OUTPUT;
                case ">>" -> APPEND;
                case "<" -> INPUT;
                case ">&" -> FD_OUTPUT;
                case "<&" -> FD_INPUT;
                case "&>" -> MERGE;
                case "&>>" -> MERGE_APPEND;
                case "<<" -> HERE_DOC;
                case "<<<" -> HERE_STRING;
                default -> throw new IllegalArgumentException("Unknown redirect symbol: " + symbol);
            };
        }
    }
    
    public Redirect(Integer sourceFd, Type type, String target) {
        this.sourceFd = sourceFd;
        this.type = Objects.requireNonNull(type, "Type cannot be null");
        this.target = Objects.requireNonNull(target, "Target cannot be null");
    }
    
    // 便捷工厂方法
    public static Redirect stdoutToFile(String file) {
        return new Redirect(null, Type.OUTPUT, file);
    }
    
    public static Redirect stdoutAppend(String file) {
        return new Redirect(null, Type.APPEND, file);
    }
    
    public static Redirect stdinFromFile(String file) {
        return new Redirect(null, Type.INPUT, file);
    }
    
    public static Redirect stderrToStdout() {
        return new Redirect(2, Type.FD_OUTPUT, "1");
    }
    
    public static Redirect mergeToFile(String file) {
        return new Redirect(null, Type.MERGE, file);
    }
    
    public static Redirect mergeAppend(String file) {
        return new Redirect(null, Type.MERGE_APPEND, file);
    }
    
    // 解析方法（示例）
    public static Redirect parse(String redirectStr) {
        // 简化解析逻辑
        if (redirectStr.startsWith("2>&")) {
            String fd = redirectStr.substring(3);
            return new Redirect(2, Type.FD_OUTPUT, fd);
        }
        // 其他解析逻辑...
        throw new IllegalArgumentException("Cannot parse: " + redirectStr);
    }
    
    // Getters
    public Integer sourceFd() { return sourceFd; }
    public Type type() { return type; }
    public String target() { return target; }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (sourceFd != null) {
            sb.append(sourceFd);
        }
        sb.append(type.symbol());
        sb.append(target);
        return sb.toString();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Redirect redirect = (Redirect) o;
        return Objects.equals(sourceFd, redirect.sourceFd) &&
               type == redirect.type &&
               target.equals(redirect.target);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sourceFd, type, target);
    }
}
```

## 8. 表达式树使用

CommandExpression接口及其实现类构成了完整的Bash命令行表达式树。在实际使用中，可以直接使用CommandExpression作为根节点，无需额外的包装类。

## 9. 统一工厂类

### 9.1 CommandFactory工厂类

```java
/**
 * 统一的命令行表达式工厂类
 * 支持静态导入，提供流畅的API
 */
public final class CommandFactory {
    
    // 禁止实例化
    private CommandFactory() {}
    
    // 简单命令
    public static SimpleCommand cmd(String command, String... args) {
        return SimpleCommand.builder(command)
            .args(args)
            .build();
    }
    
    // 带环境变量的命令
    public static SimpleCommand cmd(String command, Map<String, String> envVars, String... args) {
        SimpleCommand.Builder builder = SimpleCommand.builder(command);
        envVars.forEach((key, value) -> builder.envVar(EnvVar.local(key, value)));
        return builder.args(args).build();
    }
    
    // 带EnvVar对象的命令
    public static SimpleCommand cmd(String command, List<EnvVar> envVars, String... args) {
        SimpleCommand.Builder builder = SimpleCommand.builder(command);
        envVars.forEach(builder::envVar);
        return builder.args(args).build();
    }
    
    // 环境变量工厂方法
    public static EnvVar env(String name, String value) {
        return EnvVar.local(name, value);
    }
    
    public static EnvVar export(String name, String value) {
        return EnvVar.export(name, value);
    }
    
    public static EnvVar expand(String name, String value) {
        return EnvVar.expand(name, value);
    }
    
    // 逻辑与
    public static LogicalExpr and(CommandExpression left, CommandExpression right) {
        return LogicalExpr.and(left, right);
    }
    
    // 逻辑或
    public static LogicalExpr or(CommandExpression left, CommandExpression right) {
        return LogicalExpr.or(left, right);
    }
    
    // 顺序执行
    public static LogicalExpr sequence(CommandExpression left, CommandExpression right) {
        return LogicalExpr.sequence(left, right);
    }
    
    // 管道
    public static PipelineExpr pipeline(CommandExpression... commands) {
        PipelineExpr.Builder builder = PipelineExpr.builder();
        for (CommandExpression cmd : commands) {
            builder.command(cmd);
        }
        return builder.build();
    }
    
    // 分组
    public static GroupExpr group(CommandExpression... commands) {
        return GroupExpr.of(commands);
    }
    
    // 子shell
    public static SubshellExpr subshell(CommandExpression inner) {
        return SubshellExpr.of(inner);
    }
    
    // 后台运行
    public static BackgroundExpr background(CommandExpression inner) {
        return BackgroundExpr.of(inner);
    }
    
    // 重定向
    public static Redirect redirect(String type, String target) {
        return Redirect.of(type, target);
    }
    
    public static Redirect stdoutToFile(String file) {
        return Redirect.stdoutToFile(file);
    }
}
```

### 9.2 使用示例

```java
import static io.nop.ai.shell.model.CommandFactory.*;

// 简单示例：`VAR=value cmd1 && cmd2 | cmd3 > out.txt`
CommandExpression expr = and(
    // left: VAR=value cmd1
    cmd("cmd1", List.of(env("VAR", "value"))),
    
    // right: cmd2 | cmd3 > out.txt
    pipeline(
        cmd("cmd2"),
        cmd("cmd3").redirect(stdoutToFile("out.txt"))
    )
);

System.out.println(expr);  // 输出: VAR=value cmd1 && cmd2 | cmd3 > out.txt

// 复杂环境变量示例：`export PATH=$PATH:/new cmd`
CommandExpression expr2 = cmd("cmd", List.of(
    export("PATH", "$PATH:/new")
));

System.out.println(expr2);  // 输出: export PATH=$PATH:/new cmd
```

### 9.3 复杂示例：`(cmd1 && cmd2) || { echo "fail"; exit 1; }`

```java
import static io.nop.ai.shell.model.CommandFactory.*;

CommandExpression expr = or(
    // left: (cmd1 && cmd2)
    subshell(and(cmd("cmd1"), cmd("cmd2"))),
    
    // right: { echo "fail"; exit 1; }
    group(cmd("echo", "fail"), cmd("exit", "1"))
);

System.out.println(expr);  // 输出: (cmd1 && cmd2) || { echo "fail"; exit 1; }
```

### 9.4 后台运行示例：`cmd1 && cmd2 | cmd3 &`

```java
import static io.nop.ai.shell.model.CommandFactory.*;

CommandExpression expr = background(
    and(cmd("cmd1"), pipeline(cmd("cmd2"), cmd("cmd3")))
);

System.out.println(expr);  // 输出: cmd1 && cmd2 | cmd3 &
```

### 9.5 完整复杂示例：`{ echo "start"; } && (ls | grep txt) || echo "none" ; sleep 5 &`

```java
import static io.nop.ai.shell.model.CommandFactory.*;

CommandExpression expr = sequence(
    // left: { echo "start"; } && (ls | grep txt) || echo "none"
    or(
        and(
            group(cmd("echo", "start")),
            subshell(pipeline(cmd("ls"), cmd("grep", "txt")))
        ),
        cmd("echo", "none")
    ),
    
    // right: sleep 5 &
    background(cmd("sleep", "5"))
);

System.out.println(expr);
// 输出: { echo "start"; } && (ls | grep txt) || echo "none" ; sleep 5 &
```

## 10. 模型特性总结

### 10.1 结构完整性
- **树形嵌套**：准确表达运算符优先级和括号嵌套
- **作用域正确**：每个SimpleCommand有自己的环境变量和重定向
- **类型安全**：编译时类型检查，避免运行时错误
- **不可变性**：所有对象不可变，线程安全

### 10.2 语法覆盖

| Bash语法 | 模型对象 | 说明 |
|----------|----------|------|
| `cmd arg` | `SimpleCommand` | 简单命令 |
| `VAR=value cmd` | `SimpleCommand` with envVars | 命令特有环境变量 |
| `cmd1 \| cmd2` | `PipelineExpr` | 管道连接 |
| `cmd1 && cmd2` | `LogicalExpr` with AND | 逻辑与 |
| `cmd1 \|\| cmd2` | `LogicalExpr` with OR | 逻辑或 |
| `cmd1 ; cmd2` | `LogicalExpr` with SEMICOLON | 顺序执行 |
| `{ cmd1; cmd2; }` | `GroupExpr` | 大括号分组 |
| `(cmd1 && cmd2)` | `SubshellExpr` | 子shell表达式 |
| `cmd &` | `BackgroundExpr` | 后台运行 |
| `> file`, `2>&1`, `&>` | `Redirect` | 重定向操作 |

### 10.3 可扩展性
- **访问者模式**：便于遍历、分析和转换表达式树
- **接口设计**：易于添加新的表达式类型
- **Builder模式**：提供流畅的API构建复杂结构
- **工厂方法**：简化常见用例的创建

## 11. 使用建议

### 11.1 构建模型
```java
// 使用Builder模式
SimpleCommand cmd = SimpleCommand.builder("ls")
    .arg("-l")
    .arg("*.txt")
    .envVar("LC_ALL", "C")
    .redirect(Redirect.stdoutToFile("output.txt"))
    .redirect(Redirect.stderrToStdout())
    .build();

// 使用工厂方法组合
CommandExpression complex = LogicalExpr.and(
    cmd,
    BackgroundExpr.of(SimpleCommand.builder("sleep").arg("10").build())
);
```

### 11.2 遍历分析
```java
// 实现访问者
class CommandCounter implements CommandVisitor<Integer> {
    @Override
    public Integer visit(SimpleCommand cmd) {
        return 1;
    }
    
    @Override
    public Integer visit(PipelineExpr pipe) {
        return pipe.commands().stream()
            .mapToInt(cmd -> cmd.accept(this))
            .sum();
    }
    
    @Override 
    public Integer visit(LogicalExpr logical) {
        return logical.left().accept(this) + logical.right().accept(this);
    }
    
    // ... 其他visit方法
}

// 使用
int count = expr.accept(new CommandCounter());
```

### 11.3 验证与转换
- **语法验证**：可在Builder中添加验证逻辑
- **格式美化**：通过访问者重新生成格式化输出
- **结构转换**：将表达式树转换为其他表示形式

## 12. 注意事项

1. **不处理语义展开**：模型存储原始字符串，不展开变量、命令替换、通配符等
2. **不验证命令存在性**：不检查命令是否存在或可执行
3. **不处理执行环境**：不考虑环境变量继承、工作目录、信号处理等
4. **语法严格性**：某些bash灵活语法（如大括号可选分号）可能简化处理

此模型为Bash命令行语法分析提供了完整、类型安全的基础，适用于代码分析、格式化工具、IDE集成等场景。