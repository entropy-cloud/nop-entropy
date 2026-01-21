# 常见错误和解决方法

## 代码生成相关

### 错误1：找不到生成的类

**错误信息**：
```
cannot find symbol: class UserBizModel
```

**解决方法**：
```bash
# 在xxx-meta模块上执行install
cd xxx-meta
mvn install    # ⭐ 关键步骤
cd ../xxx-service
mvn clean install
```

### 错误2：生成的代码不是最新的

**解决方法**：
```bash
# 按顺序清理并重新构建
cd xxx-codegen && mvn clean install
cd ../xxx-dao && mvn clean install
cd ../xxx-meta && mvn clean install    # ⭐ 关键步骤
cd ../xxx-service && mvn clean install
cd ../xxx-web && mvn clean install
cd ../xxx-app && mvn clean install
```

## Delta定制相关

### 错误3：Delta定制不生效

**解决方法**：
```bash
# 1. 检查Delta文件路径
ls xxx-delta/src/main/resources/_vfs/_delta/default/nop/auth/

# 2. 检查是否使用了x:extends
grep "x:extends" xxx-delta/src/main/resources/_vfs/_delta/default/nop/auth/app.orm.xml

# 3. 检查app模块的依赖
grep "xxx-delta" xxx-app/pom.xml

# 4. 重新构建
mvn clean install
```

### 错误4：Delta合并冲突

**解决方法**：
```bash
# 使用x:override="remove"删除重复定义
<bean id="myBean" x:override="remove" />
```

## IoC容器相关

### 错误5：找不到Bean

**解决方法**：
```bash
# 检查Bean名称
grep -r "bean id=\"myBean\"" src/main/resources/_vfs/

# 检查Bean是否注册
# 查看启动日志中的Bean注册信息

# 使用@Primary注解
<bean id="myBean" class="com.example.MyBean" primary="true" />
```

## ORM相关

### 错误6：实体类找不到

**解决方法**：
```bash
# 检查实体类是否存在
ls xxx-dao/src/main/java/com/example/myapp/dao/entity/User.java

# 检查ORM模型文件
cat myapp-codegen/src/main/resources/_vfs/model/myapp.orm.xml | grep "User"

# 重新生成代码
cd xxx-codegen && mvn clean install
cd ../xxx-dao && mvn clean install
```

## GraphQL相关

### 错误7：GraphQL Schema生成失败

**解决方法**：
```bash
# 检查BizModel注解
grep "@BizModel" xxx-service/src/main/java/com/example/myapp/service/biz/UserBizModel.java

# 检查方法签名
# 确保方法签名正确：
# @BizQuery: public User getUser(String userId)
# @BizMutation: public User createUser(User user)

# 重新构建
cd xxx-service && mvn clean install
```

## 相关文档

- [代码生成概念](../codegen/codegen-concepts.md)
- [跨模块代码生成](../codegen/cross-module-codegen.md)
- [Delta定制基础](../delta/delta-basics.md)

---

**文档版本**: 1.0
**最后更新**: 2026-01-21
