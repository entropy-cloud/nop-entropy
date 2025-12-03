# Nop Platform - Dataset Module (nop-dataset)

The nop-dataset module provides a comprehensive framework for working with tabular data sets, records, and data binding in the Nop Platform. It offers interfaces and implementations for representing, transforming, and manipulating structured data.

## Features

### Data Set Framework
- `IDataSet` interface for tabular data representation
- `IComplexDataSet` for nested/complex data structures
- Data set metadata (`IDataSetMeta`)
- Data field metadata (`IDataFieldMeta`)
- Transformed data sets for filtering and projection
- Limit data set for pagination

### Data Row Operations
- `IDataRow` interface for accessing row data
- Map-based data row implementation
- Single column row implementation
- Base data row with common functionality

### Field Mapping
- `IFieldMapper` for field-to-field transformations
- Default field mapper implementation
- Binder-based field mappers
- Binder map field mapper

### Data Binding
- `IDataParameterBinder` interface for data binding
- Auto-converting parameter binder
- Encoded data parameter binder
- Data parameters collection

### Record Processing
- `IRecordInput` and `IRecordOutput` for record I/O
- Record resource metadata
- Record splitter for chunking data
- Record tagger for adding metadata
- Row number record interface

### Row Mappers
- Various row mapper implementations:
  - Column array row mapper
  - Column map row mapper
  - Single column row mapper
  - List string row mapper
  - Smart row mapper for automatic type conversion
  - Detach row mapper for creating independent copies

## Installation

```xml
<dependency>
  <groupId>io.github.entropy-cloud</groupId>
  <artifactId>nop-dataset</artifactId>
  <version>2.0.0-SNAPSHOT</version>
</dependency>
```

## Usage Examples

### Creating and Using a Data Set

```java
import io.nop.dataset.*;
import io.nop.dataset.impl.*;
import io.nop.commons.type.StdSqlType;
import io.nop.commons.util.CollectionHelper;

// Create field metadata
List<IDataFieldMeta> fields = Arrays.asList(
    new BaseDataFieldMeta("id", StdSqlType.INTEGER, null),
    new BaseDataFieldMeta("name", StdSqlType.VARCHAR, null),
    new BaseDataFieldMeta("age", StdSqlType.INTEGER, null)
);

// Create data set metadata
IDataSetMeta meta = new BaseDataSetMeta(fields);

// Create data rows
List<IDataRow> rows = Arrays.asList(
    new MapDataRow(meta, false, Map.of("id", 1, "name", "John", "age", 30)),
    new MapDataRow(meta, false, Map.of("id", 2, "name", "Jane", "age", 25))
);

// Create data set
IDataSet dataSet = new BaseDataSet(rows, meta);

// Iterate through data set
for (IDataRow row : dataSet) {
    Integer id = row.getInt(0);
    String name = row.getString(1);
    Integer age = row.getInt(2);
    // Process row
}

// Get data set metadata
IDataSetMeta dataSetMeta = dataSet.getMeta();
int columnCount = dataSetMeta.getFieldCount();
```

### Using Field Mapper

```java
import io.nop.dataset.IFieldMapper;
import io.nop.dataset.impl.DefaultFieldMapper;

// Use the default field mapper (no field renaming)
IFieldMapper fieldMapper = DefaultFieldMapper.INSTANCE;

// Note: Field renaming functionality is provided by other classes like ProjectDataSetMeta
// which allows projecting specific fields from a dataset
```

### Using Row Mapper

```java
import io.nop.dataset.rowmapper.SingleBinderRowMapper;
import io.nop.dataset.binder.DataParameterBinders;

// Map to single column value using binder
SingleBinderRowMapper<Integer> idMapper = new SingleBinderRowMapper<>(DataParameterBinders.INT);

// Note: Row mapping functionality is typically used with IRecordInput/IRecordOutput interfaces
```

### Record Processing

```java
import io.nop.dataset.record.IRecordInput;
import io.nop.dataset.record.IRecordOutput;
import io.nop.dataset.impl.BaseDataSet;

// Example using IRecordInput with a dataset
BaseDataSet dataSet = // create dataset
IRecordInput input = dataSet;
IRecordResourceMeta meta = input.getMeta();

// Read records
IDataRow row;
for (IDataRow row : input) {
    // Process row record
    System.out.println("Row: " + row.toMap());
}

// Note: IRecordOutput implementations are typically specific to data formats
// (e.g., CSV, JSON) and may require additional configuration
```

### Data Binding

```java
import io.nop.dataset.binder.DataParameterBinders;
import io.nop.dataset.binder.IDataParameterBinder;

// Create parameter binders for SQL data types
IDataParameterBinder idBinder = DataParameterBinders.INT;
IDataParameterBinder nameBinder = DataParameterBinders.VARCHAR;

// IDataParameterBinder works with IDataParameters interface (implemented by IDataRow)
// Note: The bind method signature is different from what's shown in the simplified example
```

## Directory Structure

```
src/main/java/io/nop/dataset/
├── binder/            # Data binding interfaces and implementations
├── impl/              # Core implementations of dataset interfaces
├── record/            # Record processing interfaces and implementations
├── rowmapper/         # Row mapper implementations
├── IComplexDataSet.java     # Complex dataset interface
├── IDataFieldMeta.java      # Data field metadata interface
├── IDataRow.java            # Data row interface
├── IDataSet.java            # Dataset interface
├── IDataSetMeta.java        # Dataset metadata interface
├── IFieldMapper.java        # Field mapper interface
└── IRowMapper.java          # Row mapper interface
```


