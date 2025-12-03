# Nop Platform - Antlr4 Integration (nop-antlr4)

The nop-antlr4 module provides Antlr4 integration for the Nop Platform. It includes common utilities, tooling extensions, and model classes for working with Antlr4 parsers and grammars.

## Overview

This module is structured as a parent project with two main submodules:

1. **nop-antlr4-common**: Common utilities and base classes for Antlr4 parsing
2. **nop-antlr4-tool**: Tooling extensions and model classes for working with Antlr4 grammars

## Submodules

### nop-antlr4-common

This submodule provides common utilities and base classes for working with Antlr4 parsers:

- **AbstractAntlrLexer**: Base class for custom Antlr4 lexers
- **AbstractAntlrParser**: Base class for custom Antlr4 parsers
- **AbstractParseTreeParser**: Base class for parse tree processors
- **IParseTreeParser**: Interface for parse tree parsers
- **ParseTreeHelper**: Utility methods for working with parse trees
- **ParseTreeFormatter**: Formatter for parse trees
- **ParseTreeResult**: Result container for parse operations
- **AbstractASTPrinter**: Base class for AST printers

### nop-antlr4-tool

This submodule provides tooling extensions and model classes for working with Antlr4 grammars:

#### Loader Package
- **AntlrGrammarHelper**: Helper methods for working with Antlr4 grammars
- **AstGrammarBuilder**: Builder for AST grammars from Antlr4 definitions
- **CustomTool**: Custom Antlr4 tool with enhanced functionality
- **GrammarLoader**: Loader for Antlr4 grammar files

#### Model Package
- **AstGrammar**: Representation of an AST grammar
- **AstRule**: Rule definition in an AST grammar
- **GrammarElement**: Base class for grammar elements
- **GrammarElementKind**: Enum for grammar element types
- **SeqBlock**: Sequence of grammar elements
- **OrBlock**: Alternation of grammar elements
- **StarBlock**: Zero or more repetitions
- **PlusBlock**: One or more repetitions
- **OptionalBlock**: Optional element
- **SetBlock**: Character set
- **RuleRef**: Reference to another rule
- **TerminalNode**: Terminal symbol

## Usage

### Creating a Custom Parser

```java
import io.nop.antlr4.common.AbstractParseTreeParser;
import io.nop.antlr4.common.ParseTreeResult;
import io.nop.api.core.util.SourceLocation;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.TokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

// 1. 自定义ANTLR4解析器实现类（通常由ANTLR4工具生成）
public class MyAntlrParser extends AbstractAntlrParser {
    public MyAntlrParser(TokenStream input) {
        super(input);
    }
    
    // 具体的解析方法（由ANTLR4工具生成）
    public ParseTree myRule() {
        // 解析逻辑
        return null;
    }
}

// 2. 解析树处理器
public class MyCustomParser extends AbstractParseTreeParser {
    @Override
    protected ParseTreeResult doParse(CharStream stream) {
        // 初始化词法分析器（假设已创建）
        MyAntlrLexer lexer = new MyAntlrLexer(stream);
        TokenStream tokens = new CommonTokenStream(lexer);
        
        // 初始化语法分析器
        MyAntlrParser parser = new MyAntlrParser(tokens);
        
        // 执行解析
        ParseTree tree = parser.myRule();
        
        // 返回解析结果
        return new ParseTreeResult(parser, baseLocation, tree);
    }
}

// 3. 使用解析器
public class ParserUsageExample {
    public static void main(String[] args) {
        MyCustomParser parser = new MyCustomParser();
        ParseTreeResult result = parser.parseFromText(SourceLocation.fromPath("test"), "SELECT * FROM table");
        if (result != null) {
            ParseTree tree = result.getParseTree();
            String treeStr = result.toStringTree();
            System.out.println("Parse tree:");
            System.out.println(treeStr);
        }
    }
}
```

### Working with Grammar Models

```java
import io.nop.antlr4.tool.loader.GrammarLoader;
import io.nop.antlr4.tool.model.AstGrammar;
import io.nop.antlr4.tool.model.AstRule;

import java.io.File;

public class GrammarExample {
    public static void main(String[] args) {
        // Load a grammar file
        GrammarLoader loader = new GrammarLoader();
        
        // 设置源目录和库目录
        File grammarDir = new File("/path/to/grammar");
        loader.setSourceDir(grammarDir);
        loader.setLibDir(grammarDir);
        
        // 加载AST语法模型
        AstGrammar grammar = loader.loadAstGrammar("MyGrammar.g4");
        
        // Access grammar rules
        System.out.println("Grammar rules:");
        grammar.getRules().forEach(rule -> {
            System.out.println("- " + rule.getAltLabelOrRuleName());
        });
    }
}
```

## Dependencies

- **antlr4-runtime**: Antlr4 runtime library
- **nop-core**: Core platform utilities

## Build Configuration

The module uses the Antlr4 Maven plugin to generate parser code from grammar files:

```xml
<plugin>
    <groupId>org.antlr</groupId>
    <artifactId>antlr4-maven-plugin</artifactId>
    <version>${antlr.plugin.version}</version>
    <executions>
        <execution>
            <id>antlr</id>
            <configuration>
                <listener>true</listener>
                <visitor>true</visitor>
            </configuration>
            <goals>
                <goal>antlr4</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

## Directory Structure

```
nop-antlr4/
├── nop-antlr4-common/         # Common Antlr4 utilities
│   └── src/main/java/io/nop/antlr4/common/  # Base classes and utilities
├── nop-antlr4-tool/           # Antlr4 tool extensions
│   ├── src/main/java/io/nop/antlr4/tool/loader/   # Grammar loaders
│   └── src/main/java/io/nop/antlr4/tool/model/    # Grammar models
└── pom.xml                    # Parent POM with build configuration
```


