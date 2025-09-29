# Nop Platform - Core Module (nop-core)

Project title derived from repository metadata and `pom.xml`.

This repository provides the core utilities for the Nop Platform, including JSON/YAML processing, tolerant XML parsing, resource and virtual file system abstractions, reflection-based bean tools, generic type utilities, graph utilities, i18n management, ZIP tools, and execution statistics.

## Features

- JSON and YAML
  - High-level JSON API with strict and non-strict parsing.
  - Parse strings into maps or typed beans (with optional location tracking for `JObject`).
  - JSON5 support: `.json5` files are parsed in non-strict mode.
  - Serialize Java objects to compact or pretty JSON and YAML.
  - Simple value parser for JSON-like literals.
  - Delta JSON loading for merging JSON structures (based on code in `lang/json/delta`).

- XML Parsing
  - Robust `XNodeParser` that supports:
    - Loose mode for AI-generated or slightly invalid XML (tolerant of unknown entities and certain malformed inputs).
    - Keeping comments and whitespace by configuration.
    - Fragment parsing and HTML-aware parsing (e.g., DOCTYPE detection).
    - CDATA handling and instruction/prolog parsing.
  - Outputs `XNode` trees that can be serialized back to XML.

- Reflection and Bean Tools
  - `BeanTool` for building/casting/copying beans using generic type metadata.
  - Property path accessors and complex property resolution (e.g., `a.b.c`, tag sets like `a.@data`).
  - Build beans from tree models and dynamic objects.
  - Field selection and model-based utilities.

- Generic Type Utilities
  - `GenericTypeHelper` for building parameterized types, list/set/map types, array types.
  - Type refinement against context types and variable resolution.

- Resource and Virtual File System
  - Global `VirtualFileSystem` access to abstract resources.
  - Multi-layer resource store for overlaying resource sources by priority.
  - ZIP resource tools (reading/writing zip/jar).

- Graph Utilities
  - Directed graph implementation with edge factories.
  - Construction from DTOs and export to Graphviz DOT (via helper).

- Internationalization (i18n)
  - `I18nMessageManager` for locale-aware message resolution.
  - Template rendering with i18n variable placeholders (e.g., `@i18n:a.b.c,e.f.g|default`).
  - Loading cached locale messages and registering runtime overrides.

- Execution Stats
  - Global statistics for JDBC SQL and RPC (client/server) with configurable caches.

- Native Image Support
  - GraalVM native-image configuration present under `META-INF/native-image`.

## Installation

Maven coordinates derived from `pom.xml`:

```xml
<dependency>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-core</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

Note:
- Version is inherited from the parent POM (derived from `pom.xml`).
- Exact versions may differ; use your repository or BOM/version management as appropriate.

## Usage

Below are minimal usage examples based on repository tests and source APIs.

### JSON

Strict and non-strict parsing and stringifying:

```java
import io.nop.core.lang.json.JsonTool;

// Non-strict parse of JSON5-like text
Object obj = JsonTool.parseNonStrict("{a:1, b:'text'}");
String json = JsonTool.stringify(obj); // -> {"a":1,"b":"text"}

// Strict parse/serialize round-trip
Object parsed = JsonTool.parse("{\"a\":1}");
String str = JsonTool.stringify(parsed); // -> {"a":1}
```

YAML serialization/parsing:

```java
import io.nop.core.lang.json.JsonTool;

String yaml = JsonTool.serializeToYaml(Map.of("a", null, "b", "null"));
// Example output:
// a: null
// b: 'null'

Object back = JsonTool.parseYaml(null, yaml);
String compact = JsonTool.serialize(back, false); // -> {"a":null,"b":"null"}
```

Parse into typed beans with generic type support:

```java
import io.nop.core.lang.json.JsonTool;
import io.nop.api.core.beans.ApiRequest;
import io.nop.core.type.PredefinedGenericTypes;
import io.nop.core.type.utils.GenericTypeHelper;

// Parse into ApiRequest<String>
String text = "{\"data\":\"s\"}";
ApiRequest<String> req = JsonTool.parseBeanFromText(
  text,
  GenericTypeHelper.buildRequestType(PredefinedGenericTypes.STRING_TYPE)
);
```

### XML

Tolerant parsing, comments, fragments, and CDATA:

```java
import io.nop.core.lang.xml.XNode;
import io.nop.core.lang.xml.parse.XNodeParser;

// Loose mode for AI-generated or slightly invalid XML
XNode node = XNodeParser.instance()
  .looseMode(true)
  .parseFromText(null, "<a><b>1</b></a>");

String xml = node.outerXml(false, false); // -> <a><b>1</b></a>
```

### Bean Tool

Complex property resolution:

```java
import io.nop.core.reflect.bean.BeanTool;

Map<String, Object> map = new HashMap<>();
map.put("a", List.of("data"));

Object result = BeanTool.getComplexProperty(map, "a.@data"); // -> true
```

Build/cast/copy beans (see tests for full examples):

```java
import io.nop.core.reflect.bean.BeanTool;
import io.nop.core.lang.json.JsonTool;
import io.nop.core.lang.json.JObject;

// Build a POJO from a parsed JSON object
JObject obj = JsonTool.parseBeanFromText("{a:1,b:[1,2,3],c:'3'}", JObject.class);
MyClass my = BeanTool.buildBean(obj, MyClass.class);
```

Note: `MyClass` is your POJO with getters/setters. For more examples, see tests under `src/test/java/io/nop/core/reflect/TestBeanTool.java`.

### I18n

Resolve i18n variables inside strings:

```java
import io.nop.core.i18n.I18nMessageManager;

// Load messages as needed (not shown), then:
String rendered = I18nMessageManager.instance().resolveI18nVar("en", "@i18n:a.b.c,e.f.g|Default");
// Tries keys a.b.c then e.f.g; falls back to "Default" if not found.
```

### Graph

Create a directed graph and add edges:

```java
import io.nop.core.model.graph.DefaultDirectedGraph;
import io.nop.core.model.graph.DefaultEdge;

DefaultDirectedGraph<String, DefaultEdge<String>> graph = DefaultDirectedGraph.create();
graph.addVertex("A");
graph.addVertex("B");
graph.addEdge("A", "B");

System.out.println(graph.toString()); // Deterministic vertex/edge listing
```

## Repository Overview

- `src/main/java/io/nop/core/lang/json/` — JSON/YAML parsing, serialization, delta merge helpers.
- `src/main/java/io/nop/core/lang/xml/` — XML/XNode models and tolerant parser.
- `src/main/java/io/nop/core/resource/` — Resource abstraction, virtual file system, stores (multi-layer, zip).
- `src/main/java/io/nop/core/reflect/` — Reflection utilities, bean models, bean copying, property access.
- `src/main/java/io/nop/core/type/` — Generic type system and utilities.
- `src/main/java/io/nop/core/model/graph/` — Directed graph utilities and Graphviz export.
- `src/main/java/io/nop/core/i18n/` — i18n message manager and loaders.
- `src/main/java/io/nop/core/resource/zip/` — ZIP/JAR IO using JDK tooling.
- `src/main/java/io/nop/core/stat/` — Global stats managers for JDBC/RPC.

Tests with usage examples:
- JSON: `src/test/java/io/nop/core/lang/json/TestJsonTool.java`
- XML: `src/test/java/io/nop/core/lang/xml/TestXNodeParser.java`
- BeanTool: `src/test/java/io/nop/core/reflect/TestBeanTool.java`
- Resources/Stores: `src/test/java/io/nop/core/resource/store/TestInMemoryResourceStore.java`
- Graph: `src/test/java/io/nop/core/model/graph/...`

## Contributing

Not provided in repository files. If you plan to contribute, consider opening an issue or PR in this repository and follow standard GitHub contribution practices.

## License

Apache License 2.0. See [LICENSE](LICENSE). License type derived from the `LICENSE` file and `pom.xml`.

## Sources

Files read to prepare this README:
- LICENSE
- pom.xml
- src/main/resources/META-INF/native-image/io.github.entropy-cloud.nop-core/native-image.properties
- src/main/java/io/nop/core/lang/json/JsonTool.java
- src/main/java/io/nop/core/lang/xml/parse/XNodeParser.java
- src/main/java/io/nop/core/resource/VirtualFileSystem.java
- src/main/java/io/nop/core/reflect/bean/BeanTool.java
- src/main/java/io/nop/core/type/utils/GenericTypeHelper.java
- src/main/java/io/nop/core/model/graph/DefaultDirectedGraph.java
- src/main/java/io/nop/core/i18n/I18nMessageManager.java
- src/main/java/io/nop/core/resource/store/MultiLayerResourceStore.java
- src/main/java/io/nop/core/resource/zip/JdkZipTool.java
- src/main/java/io/nop/core/stat/GlobalStatManager.java
- src/test/java/io/nop/core/lang/json/TestJsonTool.java
- src/test/java/io/nop/core/lang/xml/TestXNodeParser.java
- src/test/java/io/nop/core/resource/store/TestInMemoryResourceStore.java
- src/test/java/io/nop/core/reflect/TestBeanTool.java