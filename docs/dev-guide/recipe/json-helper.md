# JSON处理

## 1. 解析JSON文本

```javascript
JsonTool.parseNonStrict(text);

JsonTool.parse(text); // 解析标准JSON格式

```

## 2. 解析JSON文件

```javascript
JsonTool.parseBeanFromResource(resource, beanType);
```

如果根据文件的后缀名是json、json5或者yaml，采用不同的解析器去解析。返回结果缺省为普通Map或者List对象。如果设置了beanType为具体的Java类型，
则尝试将Json对象转换为对应的强类型的Java对象。

例如

```
JsonTool.parseBeanFromResource(resource, Map.class);
JsonTool.parseBeanFromResource(resource, RuleModel.class);
```

## 3. 加载JSON或者Yaml文件，并自动按照Delta合并规则处理其中的`x:extends`和`x:gen-extends`等差量合并算子。

```
JsonTool.loadDeltaBean(resource, beanType, new DeltaJsonOptions());
```

## 4. 将Java对象转换为普通JSON对象

```
json = JsonTool.serializeToJson(bean);
```

## 5. 将Java对象序列化为JSON文本

```
json = JsonTool.serialize(bean, pretty);
```

pretty参数控制生成文本时是否使用缩进。

## 6. 将Java对象序列化为Yaml文本

```
json = JsonTool.serializeToYaml(bean);
```

## 7. 将Java对象序列化为JSON文本，并保存到文件中

```
ResourceHelper.writeJson(resource,obj);
```
