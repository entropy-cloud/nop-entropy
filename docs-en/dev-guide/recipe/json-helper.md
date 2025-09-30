# JSON Handling

## 1. Parse JSON Text

```javascript
JsonTool.parseNonStrict(text);

JsonTool.parse(text); // Parse standard JSON format

```

## 2. Parse JSON Files

```javascript
JsonTool.parseBeanFromResource(resource, beanType);
```

If the file extension is json, json5, or yaml, different parsers are used accordingly. By default, the result is a plain Map or List object. If beanType is set to a specific Java type, it attempts to convert the JSON object into the corresponding strongly-typed Java object.

For example

```
JsonTool.parseBeanFromResource(resource, Map.class);
JsonTool.parseBeanFromResource(resource, RuleModel.class);
```

## 3. Load JSON or YAML files and automatically apply Delta merge rules to process Delta merge operators such as `x:extends` and `x:gen-extends`.

```
JsonTool.loadDeltaBean(resource, beanType, new DeltaJsonOptions());
```

## 4. Convert Java objects to plain JSON objects

```
json = JsonTool.serializeToJson(bean);
```

## 5. Serialize Java objects to JSON text

```
json = JsonTool.serialize(bean, pretty);
```

The pretty parameter controls whether indentation is used when generating the text.

## 6. Serialize Java objects to YAML text

```
json = JsonTool.serializeToYaml(bean);
```

## 7. Serialize Java objects to JSON text and save to a file

```
ResourceHelper.writeJson(resource,obj);
```

<!-- SOURCE_MD5:cf7701191ba4f45f98956e84d7d11c92-->
