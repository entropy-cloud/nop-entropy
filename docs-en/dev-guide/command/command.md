# Command-line Program Support

The Nop platform has built-in support for command-line programs. As long as you register a bean that implements the ICommand interface, you can invoke it directly via command-line instructions. The application will exit immediately after the command finishes.

## 1. Register an implementation class of the [ICommand](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/command/ICommand.java) interface, with the bean name nopCommand\_xxx

```
  <bean id="nopCommand_test" class="test.TestCommand" />
```

## 2. Invoke the corresponding command via the nop-exec command

```
 java -jar app.jar nop-exec --command=test --myArg=a --myArg2=123
```

## Detailed Configuration

1. Toggle nop.core.nop-command-executor.enabled to control whether the command-line processing feature is enabled; enabled by default.

2. Pass more complex commands as files

```
jar -jar app.jar nop-exec --command=test.json
```

test.json is a JSON-formatted command file corresponding to a CommandBean object.

```json
{
  "command": "test",
  "params": {
    "myArg1": "a",
    "myArg2": 123
  }
}
```

3. Execute multiple commands sequentially

```
java -jar app.jar nop-exec --command=test1.json --command=test2.json
```

Execution will be interrupted if any command returns a non-zero value.
<!-- SOURCE_MD5:3801a40140a6f5115b3dd4f32210727c-->
