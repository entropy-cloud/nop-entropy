# 10分钟快速上手

## 步骤1：创建项目结构

### 1.1 父pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.example</groupId>
    <artifactId>myapp</artifactId>
    <version>1.0.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <modules>
        <module>myapp-codegen</module>
        <module>myapp-dao</module>
        <module>myapp-meta</module>
        <module>myapp-service</module>
        <module>myapp-web</module>
        <module>myapp-app</module>
    </modules>

    <properties>
        <maven.compiler.source>11</maven.compiler.source>
        <maven.compiler.target>11</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>io.github.entropy-cloud</groupId>
                <artifactId>nop-bom</artifactId>
                <version>2.0.0-SNAPSHOT</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
```

### 1.2 创建目录

```bash
mkdir -p myapp-codegen/postcompile
mkdir -p myapp-codegen/src/main/resources/_vfs/model
mkdir -p myapp-dao/src/main/java
mkdir -p myapp-dao/src/main/resources/_vfs
mkdir -p myapp-meta/src/main/resources/_vfs
mkdir -p myapp-service/src/main/java
mkdir -p myapp-service/src/main/resources/_vfs
mkdir -p myapp-web/src/main/resources/_vfs
mkdir -p myapp-app/src/main/java
mkdir -p myapp-app/src/main/resources/_vfs
```

## 步骤2：定义实体模型

### 2.1 ORM模型文件

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

## 步骤3：生成代码

### 3.1 codegen模块pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>myapp</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myapp-codegen</artifactId>

    <dependencies>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-orm</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.codehaus.mojo</groupId>
                <artifactId>exec-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 3.2 执行代码生成

```bash
cd myapp-codegen
mvn clean install
```

## 步骤4：创建Service层

### 4.1 BizModel

`myapp-service/src/main/java/com/example/myapp/service/biz/UserBizModel.java`:

```java
package com.example.myapp.service.biz;

import io.nop.api.core.annotations.biz.BizModel;
import io.nop.biz.crud.CrudBizModel;
import io.nop.biz.crud.EntityData;
import io.nop.dao.api.IDaoProvider;
import io.nop.core.context.IServiceContext;
import com.example.myapp.dao.entity.User;
import com.example.myapp.dao.api.IUserDao;

import jakarta.inject.Inject;

@BizModel("User")
public class UserBizModel extends CrudBizModel<User> {

    public UserBizModel() {
        setEntityName(User.class.getName());
    }

    // ✅ 继承 CrudBizModel 后，已经自动内置了完整的 CRUD 操作
    // 内置方法无需手动实现，包括：
    // - findPage(query, pageNo, pageSize)   // 分页查询
    // - get(Map id)                        // 单条查询
    // - save(Map data)                     // 保存
    // - update(Map data)                   // 更新
    // - delete(Map id)                      // 删除

    // ✅ 如需自定义业务逻辑，重写扩展点
    @Override
    protected void defaultPrepareSave(EntityData<User> entityData, IServiceContext context) {
        super.defaultPrepareSave(entityData, context);
        // 自定义逻辑：密码加密
        User user = entityData.getEntity();
        // user.setPassword(passwordEncoder.encode(user.getPassword()));
    }
}
```

### 4.2 service模块pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>myapp</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myapp-service</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>myapp-dao</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-biz</artifactId>
        </dependency>
    </dependencies>
</project>
```

## 步骤5：创建应用启动类

### 5.1 应用主类

`myapp-app/src/main/java/com/example/myapp/MyApp.java`:

```java
package com.example.myapp;

import io.nop.core.CoreConstants;
import io.nop.core.initialize.CoreInitialization;

public class MyApp {

    public static void main(String[] args) {
        System.setProperty(CoreConstants.CFG_CORE_MAX_INITIALIZE_LEVEL.getName(),
                CoreConstants.INITIALIZER_PRIORITY_ANALYZE);

        CoreInitialization.initialize();
        try {
            MyApp app = CoreInitialization.getApplicationInstance(MyApp.class);
            app.start();
        } finally {
            CoreInitialization.destroy();
        }
    }

    public void start() {
        System.out.println("Application started successfully!");
    }
}
```

### 5.2 app模块pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.example</groupId>
        <artifactId>myapp</artifactId>
        <version>1.0.0-SNAPSHOT</version>
    </parent>

    <artifactId>myapp-app</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.example</groupId>
            <artifactId>myapp-service</artifactId>
            <version>${project.version}</version>
        </dependency>
        <dependency>
            <groupId>io.github.entropy-cloud</groupId>
            <artifactId>nop-quarkus</artifactId>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-shade-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

## 步骤6：配置数据库

### 6.1 配置文件

`myapp-app/src/main/resources/_vfs/app.beans.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans x:schema="/nop/schema/beans.xdef" xmlns:x="/nop/schema/xdsl.xdef">

    <bean id="nopDataSource" class="com.zaxxer.hikari.HikariDataSource">
        <property name="jdbcUrl" value="jdbc:h2:mem:testdb" />
        <property name="username" value="sa" />
        <property name="password" value="" />
        <property name="driverClassName" value="org.h2.Driver" />
    </bean>

</beans>
```

## 步骤7：运行应用

```bash
cd ..
mvn clean install
cd myapp-app
java -jar target/myapp-app-1.0.0-SNAPSHOT.jar
```

## 步骤8：测试API

```bash
# 查询用户（**开发环境链接**）
curl -X POST /graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "query { User_getUser(userId: \"test\") { userId userName email status }"}'

# 创建用户（**开发环境链接**）
curl -X POST /graphql \
  -H "Content-Type: application/json" \
  -d '{"query": "mutation { User_createUser(user: { userId: \"test\", userName: \"张三\", email: \"test@example.com\", status: 1 }) { userId userName email status }"}'
```

## 下一步

- [常见开发任务](./common-tasks.md)
- [Delta定制基础](../delta/delta-basics.md)
- [代码生成概念](../codegen/codegen-concepts.md)

---

