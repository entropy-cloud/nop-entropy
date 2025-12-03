# nop-commons

The nop-commons module is the core utility library for the nop platform, providing a rich set of tools and functional modules to support the entire platform.

## Features

### Core Functionality

nop-commons includes multiple functional modules covering various common utilities from data structures to concurrent programming:

- **Data Aggregation** (`aggregator/`): Supports various data aggregation operations
- **Batch Processing** (`batch/`): Provides batch processing queue implementation
- **Byte Operations** (`bytes/`): Byte and ByteBuffer related utilities
- **Cache System** (`cache/`): Complete cache framework implementation
- **Collection Tools** (`collections/`): Extended collection classes and manipulation utilities
- **Concurrent Programming** (`concurrent/`): Utilities and components for concurrent programming
- **Cryptography** (`crypto/`): Encryption and hashing related utilities
- **Diff Comparison** (`diff/`): Object difference comparison functionality
- **Environment Tools** (`env/`): Environment variables and system information utilities
- **Functional Programming** (`functional/`): Functional programming support
- **I/O Operations** (`io/`): I/O related utilities
- **Language Basics** (`lang/`): Language foundation classes and interfaces
- **Metrics Collection** (`metrics/`): System metrics collection functionality
- **Mutable Types** (`mutable/`): Mutable value type implementations
- **Data Partitioning** (`partition/`): Data partitioning functionality
- **Path Matching** (`path/`): Path matching and pattern matching
- **Object Pool** (`pool/`): Object pool framework implementation
- **Service Management** (`service/`): Service lifecycle management
- **Text Processing** (`text/`): Text processing and formatting utilities
- **Tuple Types** (`tuple/`): Tuple type implementations
- **Type System** (`type/`): Type related utilities
- **General Utilities** (`util/`): Other general utility classes

## Module Details

### 1. Data Aggregation (aggregator)

Provides implementations of various data aggregation operations, including:

- **CountAggregator**: Count aggregation
- **SumAggregator**: Sum aggregation
- **AverageAggregator**: Average aggregation
- **MinAggregator**: Minimum value aggregation
- **MaxAggregator**: Maximum value aggregation

**Usage Example**:

```java
IAggregator sumAggregator = new SumAggregator();
sumAggregator.update(10);
sumAggregator.update(20);
Object result = sumAggregator.getValue(); // 30
```

### 2. Cache System (cache)

Complete cache framework implementation supporting local and distributed caching:

- **LocalCache**: Local cache implementation
- **CaffeineCacheLoader**: Caffeine cache loader
- **GlobalCacheRegistry**: Global cache registry
- **ICacheFactory**: Cache factory interface

**Usage Example**:

```java
// Create a local cache
ICache<String, String> cache = LocalCache.newCache(name,config);

// Set cache item
cache.put("key", "value");

// Get cache item
String value = cache.get("key");

// Use cache loader
cache = new LocalCache<>(name, config, new ICacheLoader<String, String>() {
    @Override
    public String load(String key) {
        // Load data from data source
        return "loaded_" + key;
    }
});

// Auto-load cache item
String loadedValue = cache.get("new_key"); // Returns "loaded_new_key"
```

### 3. Collection Tools (collections)

Provides extended collection classes and manipulation utilities:

- **CaseInsensitiveMap**: Case-insensitive Map
- **KeyedList**: List that supports access by Key
- **FreezableList**: Freezable List implementation
- **IntHashMap**: Integer key HashMap implementation

**Usage Example**:

```java
// Create case-insensitive Map
Map<String, String> map = new CaseInsensitiveMap<>();
map.put("Key", "Value");
String value = map.get("key"); // Returns "Value"

// Create KeyedList
KeyedList<Person> people = new KeyedList<>(Person::getId);
people.add(new Person(1, "Alice"));
people.add(new Person(2, "Bob"));

Person alice = people.getByKey(1); // Returns Person with id 1
```

### 4. Concurrent Programming (concurrent)

Provides utilities and components for concurrent programming:

- **CycleSynchronizer**: Cycle synchronizer
- **QueueOverflowPolicy**: Queue overflow policy
- **RoundRobinSupplier**: Round-robin supplier

**Usage Example**:

```java
// Create cycle synchronizer
CycleSynchronizer sync = new CycleSynchronizer(3);

// Use in different threads
sync.await(); // Wait for other threads to reach synchronization point
```

### 5. Functional Programming (functional)

Provides functional programming support:

- **Lazy**: Lazy computation implementation
- **Seq**: Sequence operation utilities
- **Functionals**: Functional programming utility class

**Usage Example**:

```java
// Create lazy computation object
Lazy<String> lazyValue = Lazy.of(() -> {
    System.out.println("Computing value...");
    return "Lazy Value";
});

// Compute on first access
String value1 = lazyValue.get(); // Outputs "Computing value..." and returns "Lazy Value"

// Subsequent access uses cached value
String value2 = lazyValue.get(); // Directly returns "Lazy Value" without recomputing
```

### 6. Text Processing (text)

Provides text processing and formatting utilities:

- **CodeBuilder**: Code builder
- **EditDistance**: Edit distance calculation
- **SimpleTextTemplate**: Simple text template
- **SourceCodeBlock**: Source code block

**Usage Example**:

```java
// Use CodeBuilder to construct code
CodeBuilder cb = new CodeBuilder();
cb.append("public class Test {");
cb.indent();
cb.append("public static void main(String[] args) {");
cb.indent();
cb.append("System.out.println(\"Hello World\");");
cb.outdent();
cb.append("}");
cb.outdent();
cb.append("}");

String code = cb.toString();
System.out.println(code);
```

### 7. Object Pool (pool)

Provides object pool framework implementation:

- **IPool**: Object pool interface
- **PoolConfig**: Pool configuration class
- **PoolStats**: Pool statistics information

**Usage Example**:

```java
// Create object pool configuration
PoolConfig config = new PoolConfig();
config.setMaxActive(10);  // Maximum number of active objects
config.setMinIdle(2);     // Minimum number of idle objects
config.setMaxIdle(5);     // Maximum number of idle objects
config.setWaitTimeout(1000); // Wait time when no objects are available

// Define MyObject implementing IPooledObject
class MyObject implements IPooledObject {
    public void doSomething() {
        // Implementation
        System.out.println("MyObject is doing something...");
    }
    
    @Override
    public boolean checkValid() {
        // Check object validity before borrowing from pool
        return true;
    }
    
    @Override
    public void destroy() {
        // Release resources when object is destroyed
        System.out.println("MyObject is being destroyed...");
    }
}

// Note: IPool interface is defined but concrete implementations may be in other modules
// For example usage, you would typically use an implementation like:
// IPool<MyObject> pool = new SomePoolImplementation<>(config);

// Example of how you might use the pool:
// IPool<MyObject> pool = ...; // Get a pool instance
// 
// // Acquire object (implementation-specific)
// MyObject obj = pool.acquire(new PoolAcquireOptions());
// try {
//     // Use object
//     obj.doSomething();
// } finally {
//     // Release object back to pool
//     pool.release(obj, false);
// }
```

### 8. Service Management (service)

Provides service lifecycle management functionality:

- **ILifeCycle**: Lifecycle interface
- **LifeCycleSupport**: Lifecycle support class
- **ServiceStatus**: Service status enumeration
- **ShutdownHook**: Shutdown hook implementation

**Usage Example**:

```java
// Inherit from LifeCycleSupport to get lifecycle management
public class MyService extends LifeCycleSupport {
    
    @Override
    protected void doStart() {
        // Initialize and start service
        System.out.println("MyService is starting...");
        // Put your service startup logic here
    }
    
    @Override
    protected void doStop() {
        // Stop service
        System.out.println("MyService is stopping...");
        // Put your service shutdown logic here
    }
}

// Use service
MyService service = new MyService();
service.start(); // Automatically handles status transitions
// ... Use service ...
service.stop();  // Automatically handles status transitions
```

## Core APIs

### CommonConfigs

Configuration class providing configuration items for the nop-commons module.

### CommonConstants

Constant class defining constants used by the nop-commons module.

### CommonErrors

Error code definition class defining error codes used by the nop-commons module.

## Dependencies

Main dependencies of the nop-commons module:

- **nop-api-core**: nop platform core API
- **jakarta.inject-api**: Jakarta dependency injection API
- **slf4j-api**: SLF4J logging API
- **logback-core/logback-classic**: Logback logging implementation
- **micrometer-registry-prometheus/micrometer-core**: Metrics collection
- **caffeine**: Caffeine cache library
- **cache-api**: JCache API
- **guava**: Guava utility library

## Usage Examples

### Byte Operations

```java
// Use ByteString
ByteString bytes = ByteString.copyFromUtf8("Hello");
byte[] byteArray = bytes.toByteArray();

// Use FastByteBuffer
FastByteBuffer fbb = new FastByteBuffer();
fbb.append("Hello".getBytes());
fbb.append("World".getBytes());
byte[] combined = fbb.toByteArray();
```

### Collection Operations

```java
// Use ListFunctions
List<String> list = Arrays.asList("a", "b", "c");
List<String> upperCaseList = ListFunctions.map(list, String::toUpperCase);

// Use MapFunctions
Map<String, Integer> map = MapHelper.newHashMap(expectedSize);
```

### Text Processing

```java
// Use SimpleTextTemplate
SimpleTextTemplate template = new SimpleTextTemplate("Hello {{name}}");
String result = template.render(Map.of("name", "World")); // "Hello World!"

// Use EditDistance
int distance = EditDistance.levenshtein("kitten", "sitting"); // 3
```

## Summary

nop-commons is the core utility library of the nop platform, providing a rich set of tools and functional modules covering various common needs from data structures to concurrent programming. Its modular design allows each functionality to be used independently while working well together. The design of nop-commons focuses on performance and ease of use, providing a solid foundation for other modules of the nop platform.

