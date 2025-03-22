# Command Line Support

The Nop platform provides built-in support for command line programs. By registering an implementation of the `ICommand` interface, you can directly invoke command line commands through the command line.

## 1. Registering [ICommand](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-core/src/main/java/io/nop/core/command/ICommand.java) Interface Implementation

The implementation class of the `ICommand` interface should be named as `nopCommand_{XXX}`.

```xml
<bean id="nopCommand_test" class="test.TestCommand" />
```

## 2. Invoking Commands Using `nop-exec`

You can invoke commands using the `nop-exec` command.

```bash
java -jar app.jar nop-exec --command=test --myArg=a --myArg2=123
```

## Detailed Configuration

### 1. Enabling the Command Handler Switch

Enable the command handler by setting `nop.core.nop-command-executor.enabled` to `true`. By default, this is enabled.

### 2. Passing Complex Commands as Files

You can pass complex commands as files using:

```bash
java -jar app.jar nop-exec --command=test.json
```

Here, `test.json` is a JSON file representing the command and its parameters, corresponding to the `CommandBean` object.

```json
{
  "command": "test",
  "params": {
    "myArg1": "a",
    "myArg2": 123
  }
}
```

### 3. Executing Multiple Commands Sequentially

You can execute multiple commands sequentially using:

```bash
java -jar app.jar nop-exec --command=test1.json --command=test2.json
```
