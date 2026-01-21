# 差量化软件生产线

## 核心概念

Nop平台的代码生成器是**差量化软件生产线**，支持增量生成和Delta合并：

```
Result = FirstGeneration + AutoGenDelta + ManualDelta
```

## 数据驱动的代码生成器

`XCodeGenerator`是数据驱动的代码生成器，生成过程的所有逻辑控制由输入的模板文件来指定。

### 模板路径编码规则

1. **xgen后缀**：模板文件
   ```
   xxx.java.xgen --> xxx.java
   ```

2. **xrun后缀**：直接作为xpl模板代码运行
   ```
   xxx.xrun --> ignore
   ```

3. **@前缀**：内部使用文件
   ```
   @init.xrun --> 初始化文件
   ```

### 变量表达式

目录和文件名通过`{a.b.c}`形式的变量表达式来指定循环变量：

```
/nop/base/generator/test/{globalVar}/{var1}/sub/{var2.packagePath}/{var3}.java.xgen
```

### 开关变量

```
/src/{package.name}/{webEnabled}/{model.name}Controller.java.xgen
```

当`{webEnabled}`返回false或null时跳过该文件。

## 增量生成机制

代码生成器支持反复执行，自动生成和手工修改的部分会自动合并。

### 文件覆盖规则

1. **总是被覆盖**：
   - 以`_`为前缀的文件
   - `_gen`目录下的文件
   - 包含`__XGEN_FORCE_OVERRIDE__`字符串的文件

2. **增量生成**：
   - 非下划线前缀的文件，不会被覆盖

### 三明治架构

```
CustomClass extends _AutoGenClass extends BaseClass
```

## Maven集成

通过`exec-maven-plugin`插件执行`CodeGenTask`类：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.codehaus.mojo</groupId>
            <artifactId>exec-maven-plugin</artifactId>
        </plugin>
    </plugins>
</build>
```

- **precompile**：在compile阶段之前执行
- **postcompile**：在compile阶段之后执行

## 相关文档

- [跨模块代码生成](./cross-module-codegen.md)
- [Delta定制基础](../delta/delta-basics.md)

---

**文档版本**: 1.0
**最后更新**: 2026-01-21
