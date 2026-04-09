# 修改模型后重新生成

## 适用场景

- 你修改了 `model/*.orm.xml`
- 你修改了 xmeta/xbiz/page 相关源文件后，需要确认哪些生成步骤要重新执行

## AI 决策提示

- ✅ 最简单可靠：项目根目录执行 `mvn clean install`
- ✅ 如果要理解生成职责：`codegen` 刷新项目级产物，`meta` 生成 xmeta/i18n，`web` 基于 xmeta 生成页面
- ❌ 不要只改生成文件而不回到源模型

## 最小闭环

### 1. 修改源模型

优先修改：

- `model/{appName}.orm.xml`

### 2. 重新生成

```bash
mvn clean install
```

### 3. 如果需要按模块显式构建

```bash
cd {appName}-codegen && mvn install
cd ../{appName}-dao && mvn install
cd ../{appName}-meta && mvn install
cd ../{appName}-web && mvn install
cd ../{appName}-service && mvn install
cd ../{appName}-app && mvn install
```

### 4. 检查结果

- `*-dao`：ORM、Entity、`I*Biz`
- `*-meta`：XMeta、i18n
- `*-service`：BizModel、xbiz、beans
- `*-web`：view/page

## 常见坑

- ❌ 只重跑某一个模块，但没有刷新上游生成物
- ❌ 手改 `_gen/` 或 `_` 前缀文件
- ❌ 误以为 `*-meta` 会直接生成所有 service/web 代码

## 相关文档

- `03-development-guide/project-structure.md`
- `12-tasks/create-new-entity.md`
- `13-reference/source-anchors.md`
