# Nop Platform - XDef Schema Definitions (nop-xdefs)

The nop-xdefs module contains all the XDef (XDSL Definition) schema files used by the Nop Platform. XDef is the Nop Platform's own schema definition language, similar to XML Schema but with enhanced features for defining configuration files, data models, and other structured documents.

## Overview

XDef schema files define the structure, validation rules, and metadata for various configuration files and data models used throughout the Nop Platform. These schemas enable:

- **Validation**: Ensuring configuration files conform to expected structure
- **Auto-completion**: Providing IDE support for configuration editing
- **Documentation**: Serving as living documentation for platform configuration
- **Code Generation**: Enabling automatic code generation from schema definitions
- **Type Safety**: Ensuring type-safe access to configuration data

## Schema Categories

### Core Schemas
- **xdef.xdef**: Definition of the XDef language itself
- **xdsl.xdef**: XDSL (XLang Domain Specific Language) core syntax and functionality
- **schema.xdef**: Core schema definitions

### API and Web
- **api.xdef**: API definition schema
- **gateway.xdef**: API gateway configuration

### Authentication and Authorization
- **action-auth.xdef**: Action-based authorization rules
- **data-auth.xdef**: Data-based authorization rules

### AI and LLM Integration
- **chat-options.xdef**: Chat model options
- **llm.xdef**: Large Language Model configuration
- **prompt.xdef**: Prompt template definitions

### Business Logic
- **xbiz.xdef**: Business logic definitions
- **state-machine.xdef**: State machine configurations

### Database and ORM
- **orm.xdef**: ORM (Object-Relational Mapping) configuration
- **entity.xdef**: Entity model definitions
- **view-entity.xdef**: View entity definitions
- **dialect.xdef**: Database dialect configurations
- **sql-lib.xdef**: SQL library definitions

### Data Processing
- **query.xdef**: Query definitions
- **filter.xdef**: Filter expressions
- **order-by.xdef**: Sorting rules
- **group-by.xdef**: Grouping rules

### Excel and Office Integration
- **workbook.xdef**: Excel workbook configuration
- **style.xdef**: Excel cell styles
- **sheet-protection.xdef**: Sheet protection settings
- **page-setup.xdef**: Page setup configurations

### Language and Code Generation
- **class.xdef**: Class definitions for code generation
- **method.xdef**: Method definitions for code generation
- **compilation-unit.xdef**: Compilation unit definitions

### Message and Communication
- **message.xdef**: Message definitions

### Record Processing
- **record-definitions.xdef**: Record structure definitions
- **record-mapping.xdef**: Record mapping configurations
- **record-file.xdef**: Record file formats

### Registry and Configuration
- **registry.xdef**: Registry configuration
- **register-model.xdef**: Model registration configuration

### Task and Batch Processing
- **task.xdef**: Task flow definitions
- **batch.xdef**: Batch processing configurations

### UI Components
- **xuc.xdef**: UI component definitions
- **form.xdef**: Form configurations
- **grid.xdef**: Grid component definitions
- **action.xdef**: UI action definitions

### Workflow
- **wf.xdef**: Workflow definitions
- **assignment.xdef**: Task assignment rules

## Usage

These XDef schemas are automatically loaded by the Nop Platform and used for:

1. **Configuration Validation**: When configuration files are loaded, they are validated against their respective XDef schemas
2. **Code Generation**: Schemas are used to generate Java classes, XML handlers, and other artifacts
3. **IDE Support**: IDE plugins use these schemas to provide auto-completion and validation
4. **Documentation**: Schemas serve as the definitive reference for configuration options

## Directory Structure

```
src/main/resources/_vfs/nop/schema/
├── ai/                # AI and LLM-related schemas
├── biz/               # Business logic schemas
├── db/                # Database-related schemas
├── datav/             # Data visualization schemas
├── designer/          # Designer-related schemas
├── excel/             # Excel integration schemas
├── lang/              # Language and code generation schemas
├── office/            # Office integration schemas
├── orm/               # ORM and database mapping schemas
├── query/             # Query and filtering schemas
├── record/            # Record processing schemas
├── schema/            # Core schema definitions
├── stream/            # Stream processing schemas
├── task/              # Task and batch processing schemas
├── wf/                # Workflow schemas
├── xui/               # UI component schemas
└── *.xdef             # Top-level schema files
```

## Extending Schemas

The Nop Platform allows extending existing XDef schemas using the `x:extends` attribute:

```xml
<xdef xmlns="http://www.x-def.com/xdef"
      x:extends="class:xdef">
  <!-- Additional definitions -->
</xdef>
```

This enables customizing the platform's behavior by adding new configuration options or modifying existing ones.


