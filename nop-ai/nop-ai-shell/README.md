# Nop AI Shell

A command line execution framework with support for pipes and redirections, migrated from JLine3.

## Overview

This module provides a lightweight command execution engine that supports:

- Command line parsing with quotes and escape characters
- Command registration and lookup
- Pipe operators: `|`, `||`, `&&`
- Output redirection: `>`, `>>`
- Script engine integration for unregistered commands
- Non-interactive design (no terminal dependencies)

## Components

### Parser Layer
- `ParsedLine` - Interface for parsed command line results
- `Parser` - Interface for command line parsers
- `DefaultParser` - Default implementation with quote/escape support

### Command Registry
- `CommandRegistry` - Interface for registering and executing commands
- `CommandRegistry.CommandSession` - Command execution session with I/O streams
- `AbstractCommandRegistry` - Base implementation with alias support

### Script Engine
- `ScriptEngine` - Interface for script language integration (Groovy, JavaScript, etc.)

### Executor Core
- `CommandExecutor` - Main executor with pipe and redirection support
- `CommandData` - Data structure for parsed commands
- `ExecutionResult` - Result object with stdout/stderr/exit code
- `PipeType` - Enum for pipe operators

## Usage

### Basic Command Execution

```java
// Create parser, registry, and optional script engine
Parser parser = new DefaultParser();
CommandRegistry registry = new MyCommandRegistry();
ScriptEngine scriptEngine = null; // or new GroovyEngine()

// Create executor
CommandExecutor executor = new CommandExecutor(parser, registry, scriptEngine);

// Execute command
ExecutionResult result = executor.execute("echo hello world");

System.out.println("Exit Code: " + result.exitCode());
System.out.println("Output: " + result.stdout());
System.out.println("Error: " + result.stderr());
```

### Registering Commands

```java
public class MyCommandRegistry extends AbstractCommandRegistry {

    public MyCommandRegistry() {
        registerCommand("echo", "e");  // command with alias
        registerCommand("ls");
        registerCommand("pwd");
    }

    @Override
    public Object invoke(CommandSession session, String command, Object... args) {
        switch (command) {
            case "echo":
                for (Object arg : args) {
                    session.out().print(arg + " ");
                }
                session.out().println();
                return 0;

            default:
                session.err().println("Unknown command: " + command);
                return 1;
        }
    }
}
```

### Pipe Operations

```java
// Pipe output to another command
executor.execute("echo hello world | cat");

// AND operator (execute second only if first succeeds)
executor.execute("echo success && echo also-runs");

// OR operator (execute second only if first fails)
executor.execute("echo fails || echo fallback");
```

### Output Redirection

```java
// Overwrite file
executor.execute("echo test content > output.txt");

// Append to file
executor.execute("echo more content >> output.txt");
```

## Key Features Removed from JLine3

The following JLine3 features are **NOT** included (per requirements):

- ❌ Command completion (tab completion)
- ❌ History management
- ❌ Interactive terminal features
- ❌ Syntax highlighting
- ❌ Nano/Less editors
- ❌ Command aliases (simplified to basic registration)

## Key Features Retained from JLine3

- ✅ Command line parsing (quotes, escapes)
- ✅ Pipe operators (`|`, `||`, `&&`)
- ✅ Output redirection (`>`, `>>`)
- ✅ Command registry pattern
- ✅ Script engine interface

## Integration with Nop Platform

This module can be integrated with:
- `nop-ai-core` - For AI command implementations
- `nop-xlang` - For script language support
- `nop-core` - For base utilities and I/O handling

## License

Apache License 2.0 - see LICENSE file for details
