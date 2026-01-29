# 代码风格检查配置

## Checkstyle配置说明

Nop平台使用Checkstyle进行代码风格检查，确保代码质量和一致性。

### 主要检查规则

#### 1. 命名规范
- **类名**：使用大驼峰命名法，如`UserService`
- **方法名**：使用小驼峰命名法，如`getUserById`
- **变量名**：使用小驼峰命名法，如`userName`
- **常量名**：使用大写字母和下划线，如`MAX_COUNT`

#### 2. 代码格式
- **缩进**：使用4个空格进行缩进
- **行长度**：单行不超过120个字符
- **空行**：类和方法之间使用空行分隔
- **大括号**：使用K&R风格的大括号格式

#### 3. 导入规范
- **禁止通配符导入**：必须明确导入每个类
- **导入顺序**：按标准库、第三方库、项目库的顺序组织
- **静态导入**：仅在必要时使用静态导入

#### 4. 注释规范
- **类注释**：每个类必须有Javadoc注释
- **方法注释**：公有方法必须有Javadoc注释
- **行内注释**：复杂逻辑需要行内注释说明

### 配置示例

```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
          "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
          "configuration_1_3.dtd">

<module name="Checker">
    <!-- 文件编码检查 -->
    <module name="FileTabCharacter"/>
    
    <!-- 树结构检查 -->
    <module name="TreeWalker">
        <!-- 命名检查 -->
        <module name="TypeName"/>
        <module name="MethodName"/>
        <module name="ParameterName"/>
        
        <!-- 导入检查 -->
        <module name="AvoidStarImport"/>
        <module name="RedundantImport"/>
        
        <!-- 代码格式检查 -->
        <module name="EmptyLineSeparator"/>
        <module name="MethodLength"/>
        <module name="ParameterNumber"/>
    </module>
</module>
```

### 常见问题解决

#### 1. 导入顺序问题
```java
// 错误示例
import java.util.List;
import io.nop.api.core.annotations.biz.BizModel;

// 正确示例
import io.nop.api.core.annotations.biz.BizModel;
import java.util.List;
```

#### 2. 命名规范问题
```java
// 错误示例
public class user_service {
    private String USER_NAME;
}

// 正确示例
public class UserService {
    private String userName;
}
```

#### 3. 代码格式问题
```java
// 错误示例
public void processData(String data){if(data!=null){System.out.println(data);}}

// 正确示例
public void processData(String data) {
    if (data != null) {
        System.out.println(data);
    }
}
```

### 检查执行

#### Maven插件配置
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-checkstyle-plugin</artifactId>
    <version>3.2.0</version>
    <configuration>
        <configLocation>checkstyle.xml</configLocation>
        <includeTestSourceDirectory>true</includeTestSourceDirectory>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

#### 命令行执行
```bash
# 检查代码风格
mvn checkstyle:check

# 生成检查报告
mvn checkstyle:checkstyle
```

### 最佳实践

1. **持续集成**：在CI/CD流水线中集成代码风格检查
2. **预提交检查**：使用Git钩子在提交前进行检查
3. **IDE集成**：配置IDE实时显示代码风格问题
4. **团队统一**：确保团队成员使用相同的配置

### 相关文档

- [代码风格指南](../best-practices/code-style.md)
- [开发规范指南](./dev-standards.md)
- [异常处理指南](../getting-started/core/exception-guide.md)