# DslModelHelper 使用指南

## 概述

DslModelHelper 用于加载、解析、转换和保存 XDSL 模型文件。

## 核心方法

| 类别 | 方法 | 说明 |
|------|------|------|
| 加载 | `loadDslModelFromPath(String path)` | 从虚拟文件系统路径加载模型 |
| 加载 | `loadDslModel(IResource resource)` | 从资源对象加载模型 |
| 保存 | `saveDslModel(String xdefPath, Object model, IResource resource)` | 保存模型到资源文件 |
| 转换 | `dslModelToXNode(String xdefPath, Object model)` | 模型对象转 XML 节点 |
| 转换 | `dslNodeToJson(IXDefNode defNode, XNode node)` | XML 节点转 JSON 对象 |

## 基本使用

### 1. 加载模型

#### 从路径加载

```javascript
import io.nop.xlang.xdsl.DslModelHelper;

let xdslPath = "/nop/demo/app.action-auth.xml";
let authModel = DslModelHelper.loadDslModelFromPath(xdslPath);
```

#### 从资源加载

```javascript
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdsl.DslModelHelper;

let xdslPath = "/nop/demo/app.action-auth.xml";
let resource = ResourceHelper.resolveRelativeResource(xdslPath);
let authModel = DslModelHelper.loadDslModel(resource);
```

### 2. 保存模型

```javascript
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdsl.DslModelHelper;

let xdslPath = "/nop/demo/app.action-auth.xml";
let xdefPath = "/nop/schema/action-auth.xdef";
let resource = ResourceHelper.resolveRelativeResource(xdslPath);
let authModel = DslModelHelper.loadDslModel(resource);

// 修改模型
authModel.displayName = "新的显示名称";

// 保存回文件
DslModelHelper.saveDslModel(xdefPath, authModel, resource);
```

### 3. 模型转换

#### 模型转 XML 节点

```javascript
import io.nop.core.resource.IResource;
import io.nop.core.resource.ResourceHelper;
import io.nop.xlang.xdsl.DslModelHelper;

let xdslPath = "/nop/demo/app.action-auth.xml";
let resource = ResourceHelper.resolveRelativeResource(xdslPath);
let authModel = DslModelHelper.loadDslModel(resource);

// 转换为 XML 节点
let xdefPath = "/nop/schema/action-auth.xdef";
let xmlNode = DslModelHelper.dslModelToXNode(xdefPath, authModel);
```

```

## 注意事项

1. **模型访问**：返回的模型对象是动态对象，通过属性名直接访问
2. **异常处理**：加载失败会抛出异常，适当处理
3. **性能优化**：避免重复加载，可缓存模型对象
4. **XDef 路径**：`saveDslModel` 和 `dslModelToXNode` 需要指定 xdef 路径

## 相关文档

- **ResourceHelper**: `docs-for-ai/06-utilities/ResourceHelper.md`
- **XScript**: `docs-for-ai/05-xlang/xscript.md`
- **XNode**: `docs-for-ai/06-utilities/XNode.md`
