# JSON Processing

## 1. Parsing JSON Text

```javascript
JsonTool.parseNonStrict(text);

JsonTool.parse(text); // Parsing standard JSON format
```

## 2. Parsing JSON Files

```javascript
JsonTool.parseBeanFromResource(resource, beanType);
```

If based on the file's extension is json、json5或者yaml，use different parsers to parse it. The default result is either a regular Map or List object. If beanType is set to a specific Java type, attempt to convert the Json object into the corresponding strongly typed Java object.

For example:

```javascript
JsonTool.parseBeanFromResource(resource, Map.class);
JsonTool.parseBeanFromResource(resource, RuleModel.class);
```

## 3. Loading JSON or Yaml Files and Applying Delta Merge Rules

```javascript
JsonTool.loadDeltaBean(resource, beanType, new DeltaJsonOptions());
```

## 4. Converting Java Objects to Regular JSON Objects

```javascript
json = JsonTool.serializeToJson(bean);
```

## 5. Serializing Java Objects to JSON Text

```javascript
json = JsonTool.serialize(bean, pretty);
```

The `pretty` parameter controls whether the generated text uses indentation.

## 6. Serializing Java Objects to Yaml Text

```javascript
json = JsonTool.serializeToYaml(bean);
```

## 7. Serializing Java Objects to JSON Text and Saving to File

```javascript
ResourceHelper.writeJson(resource, obj);
```
