# Nop Platform - Record Mapping (nop-record-mapping)

The nop-record-mapping module provides powerful record mapping capabilities for the Nop Platform, enabling seamless conversion between different data structures and formats. It supports complex mappings with transformation logic and can be used to map between database records, API responses, and internal data structures.

## Core Features

### Record Mapping Framework
- **Flexible Mapping Definition**: Define mappings between different record structures
- **Transformation Logic**: Support for custom transformation logic during mapping
- **Type Conversion**: Automatic type conversion between compatible types
- **Nested Structures**: Handle complex nested data structures

### Model-Based Mappings
- **Declarative Configuration**: Define mappings using a declarative model
- **Reusable Mappings**: Create and reuse mapping definitions across the application


### Mapping Management
- **Centralized Registry**: Register and manage mappings in a central registry
- **Mapping Context**: Pass context information during mapping operations
- **Naming Convention**: Mappings are selected by name convention, e.g., `Md_to_OrmModel` for mapping from Markdown to ORM model
- **Automatic Reverse Mapping**: Generate reverse mappings (e.g., `OrmModel_to_Md`) automatically through metaprogramming
- **Delta Customization**: Allow fine-tuning of automatically generated configurations through delta customizations

### Markdown Integration
- **Markdown Processing**: Support for mapping to/from Markdown formats
- **Document Generation**: Generate structured documents from mapped data

## Installation

```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-record-mapping</artifactId>
    <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Basic Mapping

```java
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.RecordMappingManager;

import java.util.HashMap;
import java.util.Map;

public class BasicMappingExample {
    public static void main(String[] args) {
        // Get mapping manager instance
        IRecordMappingManager mappingManager = RecordMappingManager.instance();
        
        // Get mapping instance
        IRecordMapping mapping = mappingManager.getRecordMapping("user_to_profile");
        
        // Define source record
        Map<String, Object> source = new HashMap<>();
        source.put("first_name", "John");
        source.put("last_name", "Doe");
        source.put("email", "john.doe@example.com");
        
        // Create mapping context
        RecordMappingContext ctx = new RecordMappingContext();
        
        // Map to target structure
        Map<String, Object> target = (Map<String, Object>) mapping.map(source, ctx);
        
        System.out.println(target);
        // Output might be: {fullName=John Doe, email=john.doe@example.com}
    }
}

### Mapping with Context

```java
import io.nop.record_mapping.IRecordMapping;
import io.nop.record_mapping.IRecordMappingManager;
import io.nop.record_mapping.RecordMappingContext;
import io.nop.record_mapping.RecordMappingManager;

import java.util.HashMap;
import java.util.Map;

public class MappingWithContextExample {
    public static void main(String[] args) {
        IRecordMappingManager mappingManager = RecordMappingManager.instance();
        
        // Get mapping instance
        IRecordMapping mapping = mappingManager.getRecordMapping("price_to_order");
        
        Map<String, Object> source = new HashMap<>();
        source.put("price", 100.0);
        
        // Create mapping context with additional information
        RecordMappingContext context = new RecordMappingContext();
        // Use getEvalScope().setLocalValue() to add custom properties to the context
        context.getEvalScope().setLocalValue("discountRate", 0.1);
        context.getEvalScope().setLocalValue("currency", "USD")
        // Context properties are typically set via the mapping configuration
        
        // Map with context
        Map<String, Object> target = (Map<String, Object>) mapping.map(source, context);
        
        System.out.println(target);
        // Output might be: {price=100.0, currency=USD, tax=10.0, total=110.0}
    }
}


## Directory Structure

```
io.nop.record_mapping/
├── impl/              # Implementation classes
│   ├── ModelBasedRecordMapping.java
│   ├── RecordMappingManagerImpl.java
│   └── RecordMappingTool.java
├── md/                # Markdown integration
├── model/             # Mapping model definitions
├── utils/             # Utility classes
├── IRecordMapping.java
├── IRecordMappingManager.java
├── RecordMappingConstants.java
├── RecordMappingContext.java
├── RecordMappingErrors.java
└── RecordMappingManager.java
```

## Key Classes

### Core Interfaces
- **IRecordMapping**: Interface for record mapping implementations
- **IRecordMappingManager**: Interface for managing record mappings

### Core Classes
- **RecordMappingManager**: Main entry point for record mapping functionality
- **RecordMappingContext**: Context for mapping operations
- **RecordMappingConstants**: Constants used in the mapping framework

### Implementation Classes
- **ModelBasedRecordMapping**: Implementation of record mapping based on models
- **RecordMappingManagerImpl**: Implementation of the mapping manager
- **RecordMappingTool**: Utility tools for record mapping operations

## Dependencies

- **nop-xlang**: Nop expression language support
- **nop-markdown**: Markdown processing integration

## Mapping Models

Record mappings are defined using model classes that specify:

- **Field Mappings**: Rules for mapping individual fields
- **Transformation Logic**: Custom logic to apply during mapping
- **Conditional Mappings**: Mappings that apply only under certain conditions

## Markdown Integration

The module provides integration with Markdown format, enabling:

- Mapping structured data to Markdown documents
- Extracting structured data from Markdown
- Generating formatted reports using mapped data


