# 完整开发流程

## 开发流程图

```
1. 需求分析
   ↓
2. 数据模型设计（orm.xml）
   ↓
3. 代码生成（mvn install）
   ↓
4. 业务逻辑开发（BizModel）
   ↓
5. API测试（GraphQL）
   ↓
6. 前端页面开发（可选）
   ↓
7. 部署上线
```

## 步骤1：需求分析

### 输出

- 需求文档
- 实体清单
- 关系图

## 步骤2：数据模型设计

### ORM模型文件

`myapp-codegen/src/main/resources/_vfs/model/myapp.orm.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<orm x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     appName="myapp" defaultSchema="myapp">

    <entities>
        <entity name="User" className="com.example.myapp.dao.entity.User"
                displayName="用户" tableName="t_user">
            <columns>
                <column name="userId" stdDomain="string" primary="true"
                        displayName="用户ID" length="32" />
                <column name="userName" stdDomain="string" displayName="用户名"
                        length="100" />
                <column name="email" stdDomain="email" displayName="邮箱"
                        length="200" />
                <column name="status" stdDomain="int" displayName="状态"
                        defaultValue="1" />
            </columns>
        </entity>
    </entities>
</orm>
```

## 步骤3：代码生成

```bash
cd myapp-codegen
mvn clean install
cd ../myapp-dao
mvn clean install
cd ../myapp-meta
mvn clean install    # ⭐ 关键步骤
cd ../myapp-service
mvn clean install
cd ../myapp-web
mvn clean install
```

## 步骤4：业务逻辑开发

### BizModel

参见：[常见开发任务 - 新增变更API](./common-tasks.md#任务3新增变更api)

## 步骤5：API测试

### 启动应用

```bash
cd myapp-app
java -jar target/myapp-app-1.0.0-SNAPSHOT.jar
```

### 测试GraphQL API

```bash
# 查询用户（**开发环境链接**）
curl -X POST /graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { User_getUser(userId: \"test\") { userId userName email status } }"}'

# 创建用户（**开发环境链接**）
curl -X POST /graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "mutation { User_createUser(user: { userId: \"test\", userName: \"张三\", email: \"test@example.com\", status: 1 }) { userId userName email status }"}'
```

## 步骤6：前端页面开发（可选）

### XView模型

```xml
<?xml version="1.0" encoding="UTF-8"?>
<view x:schema="/nop/schema/xui/xview.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      id="UserView" displayName="用户管理">

    <grid id="list" displayName="用户列表">
        <columns>
            <column id="userId" displayName="用户ID" />
            <column id="userName" displayName="用户名" />
            <column id="email" displayName="邮箱" />
            <column id="status" displayName="状态" />
        </columns>
    </grid>

</view>
```

## 步骤7：部署上线

### 构建发布包

```bash
cd myapp-app
mvn clean package
```

### 部署到服务器

```bash
# 上传JAR包到服务器
scp target/myapp-app-1.0.0-SNAPSHOT.jar user@server:/opt/app/

# 启动应用
ssh user@server
cd /opt/app
java -jar myapp-app-1.0.0-SNAPSHOT.jar
```

## 相关文档

- [10分钟快速上手](../quickstart/10-min-quickstart.md)
- [常见开发任务](../quickstart/common-tasks.md)
- [代码生成概念](../codegen/codegen-concepts.md)

---

