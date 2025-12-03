# Nop Platform - Markdown Module (nop-markdown)

The nop-markdown module provides comprehensive tools for processing Markdown documents in the Nop Platform. It offers functionality for parsing, generating, and manipulating Markdown content, with a focus on structured document processing.

## Features

### Markdown Tool Interface
- Unified `IMarkdownTool` interface for Markdown operations
- Default implementation with extensive functionality

### Markdown Model
- `MarkdownDocument` - Representation of a complete Markdown document
- `MarkdownSection` - Structured sections within documents
- `MarkdownListItem` - List items with hierarchy support
- `MarkdownNode` - Base class for all Markdown elements

### Simple Markdown Implementation
- Markdown document parser with section detection
- Code block parsing and handling
- List parsing (ordered and unordered)
- Section header parsing and processing
- Document loading from resources
- Document merging capabilities

### Table Processing
- Markdown table parsing and model (`MarkdownTable`)
- Conversion from data tables to Markdown format
- Table view to Markdown table converter

### Utility Functions
- Markdown helper functions
- Table-specific helper methods
- Token count utilities
- Tree thinning for large documents

## Installation

```xml
<dependency>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-markdown</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Parsing Markdown Documents

```java
import io.nop.markdown.IMarkdownTool;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;
import io.nop.markdown.utils.MarkdownTool;
import io.nop.api.core.util.SourceLocation;
import java.util.List;

// Get markdown tool instance
IMarkdownTool markdownTool = MarkdownTool.instance();

// Parse markdown text
String markdownText = "# Heading\n\nThis is a paragraph.\n\n- List item 1\n- List item 2";
MarkdownDocument document = markdownTool.parseFromText(loc, markdownText);

// Access document sections
List<MarkdownSection> sections = document.getRootSection().getChildren();
for (MarkdownSection section : sections) {
    String title = section.getTitle();
    int level = section.getLevel();
    String content = section.getText();
    // Process section
}
```

### Creating Markdown Documents Programmatically

```java
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;

// Create document with sections
MarkdownDocument document = new MarkdownDocument();

MarkdownSection section1 = new MarkdownSection(1, "Introduction");
section1.setText("This is the introduction section.");
document.addSection(section1);

MarkdownSection section2 = new MarkdownSection(2, "Details");
section2.setText("These are the details.");
section1.addChild(section2);
```

### Working with Tables

```java
import io.nop.markdown.table.MarkdownTable;
import io.nop.markdown.table.MarkdownTableParser;

// Parse markdown table
String tableMarkdown = "| Name | Age |\n|------|-----|\n| John | 30  |\n| Jane | 25  |";
MarkdownTable table = MarkdownTableParser.parse(loc, tableMarkdown);

// Access table data
int rowCount = table.getRowCount();
int columnCount = table.getColCount();
String cellValue = table.getCellText(0, 0); // "John"

// Generate table markdown
String generatedMarkdown = table.toMarkdown();
```

### Converting Data to Markdown Table

```java
import io.nop.markdown.table.TableToMarkdownConverter;
import io.nop.core.model.table.impl.BaseTable;
import io.nop.core.model.table.impl.BaseRow;
import io.nop.core.model.table.impl.BaseCell;
import java.util.Arrays;

// Create table data
BaseTable table = new BaseTable();
BaseRow headerRow = new BaseRow();
headerRow.addCell(new BaseCell("ID"));
headerRow.addCell(new BaseCell("Name"));
headerRow.addCell(new BaseCell("Email"));
table.addRow(headerRow);

BaseRow row1 = new BaseRow();
row1.addCell(new BaseCell(1));
row1.addCell(new BaseCell("John Doe"));
row1.addCell(new BaseCell("john@example.com"));
table.addRow(row1);

BaseRow row2 = new BaseRow();
row2.addCell(new BaseCell(2));
row2.addCell(new BaseCell("Jane Smith"));
row2.addCell(new BaseCell("jane@example.com"));
table.addRow(row2);

// Convert to markdown table
String markdownTable = TableToMarkdownConverter.INSTANCE.convertToMarkdown(table);
```

### Loading Markdown from Resources

```java
import io.nop.markdown.simple.MarkdownDocumentLoader;
import io.nop.markdown.model.MarkdownDocument;

// Load markdown document from resource
MarkdownDocument document = MarkdownDocumentLoader.loadFromResource("/path/to/document.md");
```

### Merging Markdown Sections

```java
import io.nop.markdown.simple.MarkdownSectionMerger;
import io.nop.markdown.model.MarkdownDocument;
import io.nop.markdown.model.MarkdownSection;

// Merge sections from two documents
MarkdownDocument targetDoc = ...;
MarkdownDocument sourceDoc = ...;

MarkdownSectionMerger merger = new MarkdownSectionMerger();
merger.merge(targetDoc.getRootSection(), sourceDoc.getRootSection());
```

## Directory Structure

```
src/main/java/io/nop/markdown/
├── model/          # Markdown document model classes
├── simple/         # Simple Markdown tool implementation
├── table/          # Table processing utilities
├── utils/          # Helper functions
├── IMarkdownTool.java        # Main Markdown tool interface
├── MarkdownConstants.java    # Constant definitions
└── MarkdownErrors.java       # Error code definitions
```

## Dependencies

- **nop-core**: Core utilities from the Nop Platform


