# JsonTool 使用指南

## 概述

JsonTool是Nop平台提供的JSON处理工具类，用于统一处理JSON解析、生成、转换等操作，替代第三方JSON库。

## 核心功能

### 1. JSON解析
- `parse(jsonStr)`：解析标准JSON字符串
- `parseNonStrict(jsonStr)`：解析JSON5字符串，会忽略注释
- `parseYaml(yamlStr)`：解析YAML字符串为对象
- `parseBeanFromText(jsonStr, beanClass)`：从JSON字符串解析Bean对象
- `parseBeanFromYaml(yamlStr, beanClass)`：从YAML字符串解析Bean对象
- `parseBeanFromResource(IResource, beanClass)`：从IResource对象加载JSON或YAML文件并解析为Bean对象

### 2. JSON生成
- `serialize(obj, pretty)`：对象序列化为JSON字符串。pretty为true时会格式化输出。
- `stringify(obj)`：将对象转换为JSON字符串, 与`serialize(obj, false)`相同
- `serializeToYaml(obj)`：对象转YAML字符串

### 3. 资源加载
- `loadBean(resourcePath,beanClass)`：自动根据文件扩展名（.json/.json5/.yaml/.yml） 决定使用哪种解析器，并自动转换为beanClass指定的JavaBean类型。
- `loadDeltaBean(resourcePath,beanClass)`，**Delta合并加载**：
   - 根据`nop.core.vfs.delta-layer-ids`配置查找对应的 `_delta/{deltaId}` 目录下的resourcePath文件
   - 执行Delta合并算法，按照deltaId的优先级顺序合并多个delta层
   - 将合并后的JSON对象解析为Bean对象

### 4. 对象转换
- `jsonObjectToBean(json, beanClass)`：Map/List转Bean
- `beanToJsonObject(obj)`：Bean转标准JSON对象（Map/List）

## 解析结果类型

- `parse()`系列方法：返回标准Java类型（Map<String, Object>、List<Object>、String等）
- `parseBeanFromText()`系列方法：返回指定的Bean类型
- 推荐优先使用类型安全的`parseBeanFromText()`方法

## 示例代码

```java
import io.nop.core.lang.json.JsonTool;

// 解析JSON
String jsonStr = "{\"name\":\"test\",\"age\":20}";
Object jsonObj = JsonTool.parse(jsonStr); // 标准JSON对象

// parse()返回通用JSON对象，可能是Map、List或基本类型
Map<String, Object> map = JsonTool.parseBeanFromText(jsonStr, Map.class); 

// 生成JSON
String formattedJson = JsonTool.serialize(map, true); // 格式化的JSON字符串
String formattedYaml = JsonTool.serializeToYaml(map); 

// 从指定虚拟路径加载
Map<String, Object> config = JsonTool.loadBean("/main/config.json",Map.class);

// 对象转换
User user = JsonTool.parseBeanFromText(jsonStr, User.class); // JSON转User对象
String userJson = JsonTool.stringify(user); // User对象转JSON


// Delta合并示例
// 假设当前deltaId列表为 ["test", "default"]
// 则会自动合并以下文件：
// 1. _vfs/main/config.json (基础层)
// 2. _vfs/_delta/default/main/config.json (低优先级delta)
// 3. _vfs/_delta/test/main/config.json (高优先级delta)
// 合并顺序：基础层 → default → test（优先级递增，后面的覆盖前面的）
AppConfig config = JsonTool.loadDeltaBean("/main/config.json", AppConfig.class);
System.out.println("合并后的配置：" + JsonTool.serialize(config, true));
```

## 最佳实践

1. **优先使用类型安全方法**：对于已知类型的对象，优先使用 `parseBeanFromText`、`loadBean` 等方法，避免使用 `parse()` 后手动类型转换
2. **统一使用JsonTool**：所有JSON/YAML操作都应通过JsonTool进行，保持代码一致性
3. **生产环境优化**：调试时使用`serialize(obj, true)`格式化输出，生产环境使用`stringify(obj)`紧凑输出

## 注意事项

- 线程安全：所有方法都是线程安全的
- 多种格式：同时支持JSON、JSON5和YAML格式
- 资源协议：支持Nop平台内置的IResource接口和虚拟文件系统。_vfs是Nop平台的虚拟文件系统根路径，在开发时通常映射到项目的src/main/resources/_vfs目录
- Delta合并: Delta合并是Nop平台独有的可逆计算功能，更多背景参见[模块结构与代码生成指南](../../03-development-guide/project-structure.md)

## 兼容性说明

- **平台独立性**：Nop平台使用自研的JSON解析器，不依赖Jackson等第三方库
- **注解兼容**：可以使用Jackson的部分注解（`@JsonIgnore`、`@JsonInclude`），JsonTool会正确处理。注意：目前注解需要直接标注在getter/setter方法上，暂不支持类级别的注解。
- **统一入口**：为保证与Nop平台特性（如Delta合并）完全兼容，所有JSON/YAML操作都应通过JsonTool进行