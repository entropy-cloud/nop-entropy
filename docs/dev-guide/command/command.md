# 命令行程序支持

Nop平台内置了命令行程序支持，只要注册ICommand接口的bean，就可以通过命令行指令直接调用。执行完指令后会直接推出应用

## 1. 注册[ICommand](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-kernel/nop-core/src/main/java/io/nop/core/command/ICommand.java)接口的实现类，bean的名称为nopCommand\_xxx

```
  <bean id="nopCommand_test" class="test.TestCommand" />
```

## 2. 通过nop-exec命令来调用对应命令

```
 java -jar app.jar nop-exec --command=test --myArg=a --myArg2=123
```

## 详细配置

1. 启用开关 nop.core.nop-command-executor.enabled 是否启用命令行处理功能，缺省启用

2. 将比较复杂的命令作为文件传入

```
jar -jar app.jar nop-exec --command=test.json
```

test.json中为json格式的命令文件，对应于CommandBean对象。

```json
{
  "command": "test",
  "params": {
    "myArg1": "a",
    "myArg2": 123
  }
}
```

3. 依次执行多个命令

```
java -jar app.jar nop-exec --command=test1.json --command=test2.json
```

任何一个命令的返回值不是0的时候都会中断执行。
