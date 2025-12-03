# Nop Platform - Java Compiler Integration (nop-javac)

The nop-javac module provides Java compilation and dynamic class loading capabilities for the Nop Platform. It offers an abstraction layer over different Java compilers, allowing for seamless integration of dynamic compilation into the platform.

## Core Features

### Java Compilation
- **Dual Compiler Support**: Integration with both JDK compiler and Janino compiler
- **Programmatic API**: Simple API for compiling Java source code
- **Source Code Parsing**: Ability to parse Java source code without compilation
- **Error Handling**: Comprehensive error reporting during compilation

### Dynamic Class Loading
- **DynamicURLClassLoader**: Class loader that can dynamically load compiled classes
- **IDynamicClassLoader**: Interface for dynamic class loading implementations

## Supported Compilers

### JDK Compiler
- Uses the JDK's built-in `javac` compiler
- Full Java language support
- Generates standard Java class files
- Located in `io.nop.javac.jdk` package

### Janino Compiler
- Lightweight, in-memory Java compiler
- Faster compilation times
- Ideal for runtime code generation
- Located in `io.nop.javac.janino` package

## Installation

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-javac</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Basic Compilation

```java
import io.nop.javac.jdk.JdkJavaCompiler;
import io.nop.javac.jdk.JavaCompileResult;
import java.util.Collections;

public class CompilationExample {
    public static void main(String[] args) {
        // Create compiler instance
        JdkJavaCompiler compiler = new JdkJavaCompiler();
        
        // Compile a simple class
        String className = "com.example.HelloWorld";
        String sourceCode = "package com.example; public class HelloWorld { public String sayHello() { return \"Hello, World!\"; } }";
        
        // Compile the class (JdkJavaCompiler没有compileAndLoadClass方法)
        JavaCompileResult result = compiler.compile(className, sourceCode, Collections.emptyList());
        
        // Check compilation result
        if (result.isSuccess()) {
            System.out.println("编译成功：" + result.getGeneratedClassNames());
        } else {
            System.err.println(result.getErrorMessage());
        }
    }
}
```

### Using Janino Compiler

```java
import io.nop.javac.JavaCompileTool;
import io.nop.javac.IJavaCompileTool;
import io.nop.javac.JavaLibConfig;
import io.nop.javac.IDynamicClassLoader;
import java.io.File;

public class JaninoExample {
    public static void main(String[] args) {
        // Create Janino-based class loader
        IJavaCompileTool compileTool = JavaCompileTool.instance();
        JavaLibConfig config = new JavaLibConfig();
        
        // 设置源文件目录和缓存目录（Janino需要）
        File sourceDir = new File(".");
        File cacheDir = new File("./target/classes");
        config.addSourceDir(sourceDir);
        config.setCacheDir(cacheDir);
        
        // 创建动态类加载器
        IDynamicClassLoader classLoader = compileTool.createDynamicClassLoader(
            Thread.currentThread().getContextClassLoader(), config);
        
        // 注意：Janino通过ClassLoader自动编译和加载类
        System.out.println("Janino动态类加载器已创建");
    }
}
```

### Dynamic Class Loading

```java
import io.nop.javac.DynamicURLClassLoader;
import java.io.File;

public class DynamicLoadingExample {
    public static void main(String[] args) throws Exception {
        // Create dynamic class loader (注意构造函数参数)
        DynamicURLClassLoader classLoader = new DynamicURLClassLoader("dynamic-loader", 
            Thread.currentThread().getContextClassLoader());
        
        // 添加类路径
        classLoader.addClassesDir(new File("./target/classes"));
        
        // 使用JavaCompileTool创建Janino动态类加载器
        JavaCompileTool compileTool = JavaCompileTool.instance();
        
        System.out.println("动态类加载器已创建：" + classLoader);
    }
}
```

## Directory Structure

```
io.nop.javac/
├── janino/           # Janino compiler implementation
│   ├── JaninoClassLoader.java
│   ├── JaninoJavaFormatter.java
│   └── JaninoJavaParseResult.java
├── jdk/              # JDK compiler implementation
│   ├── JavaCompileResult.java
│   ├── JavaSourceCode.java
│   └── JdkJavaCompiler.java
├── DynamicURLClassLoader.java
├── IDynamicClassLoader.java
├── IJavaCompileTool.java
├── IJavaParseResult.java
├── IJavaSourceParser.java
├── JavaCompileTool.java
├── JavaCompilerErrors.java
└── JavaLibConfig.java
```

## Key Interfaces

### IJavaCompileTool
Main interface for Java compilation operations:
- `compileAndLoadClass()`: Compiles and loads a class
- `compile()`: Compiles source code without loading
- `parseSource()`: Parses source code without compiling

### IJavaSourceParser
Interface for parsing Java source code:
- `parse()`: Parses Java source code into a structured result

### IDynamicClassLoader
Interface for dynamic class loading:
- `addURL()`: Adds a URL to the classpath
- `loadClass()`: Loads a class dynamically

## Dependencies

- **nop-api-core**: Core API definitions
- **nop-commons**: Common utilities
- **janino**: Lightweight Java compiler


