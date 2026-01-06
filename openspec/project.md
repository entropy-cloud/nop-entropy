# Nop Platform 2.0 - 项目上下文

## Purpose

Nop Platform 2.0是一个基于可逆计算原理构建的下一代低代码开发平台。该项目采用面向语言编程范式，从零开始设计了GraphQL引擎、ORM引擎、工作流引擎、报表引擎、规则引擎等完整组件。平台的目标是探索下一代软件生产技术，为AI时代的软件智能生产奠定技术基础。

**核心特性：**
- 可逆计算理论支撑
- 模型驱动的代码生成
- 差量化定制能力
- 云原生架构设计
- GraalVM原生编译支持

## Tech Stack

### 后端技术栈
- **语言**: Java 17+
- **构建工具**: Maven 3.9.3+
- **运行框架**: Quarkus 3.29.2（主要）、Spring（可选）、Solon（可选）
- **数据库**: H2（开发/测试）、MySQL（生产）、PostgreSQL（生产）
- **ORM**: 自研ORM引擎（不使用JPA/Hibernate）
- **API层**: GraphQL（主要）、REST、gRPC
- **依赖注入**: 自研IoC容器（不使用Spring IoC）
- **配置管理**: 自研Config中心
- **脚本语言**: XLang（自研，包含XScript、Xpl模板）

### 前端技术栈
- **框架**: Vue.js
- **UI库**: 百度AMIS框架（JSON配置驱动）
- **构建工具**: Webpack/Vite
- **状态管理**: Vuex

### 开发工具
- **IDE**: IntelliJ IDEA（推荐）
- **插件**: nop-idea-plugin（语法提示、文件跳转、断点调试）
- **代码生成**: nop-codegen（基于Excel模型）
- **CLI工具**: nop-cli

### AI集成
- **OpenSpec**: 规范驱动的开发工作流
- **OpenCode**: AI编码助手集成
- **AIGC**: 与大模型集成（nop-ai模块，开发中）

## Project Conventions

### Code Style

#### Java代码规范
- **编码规范**: 遵循阿里巴巴Java开发手册
- **包命名**: `io.nop.{module}.{submodule}`
- **类命名**: PascalCase，如`UserService`, `GraphQLService`
- **方法命名**: camelCase，动词开头，如`getUserById`, `createUser`
- **常量命名**: UPPER_SNAKE_CASE，如`MAX_RETRY_COUNT`
- **接口命名**: 以`I`开头或使用动词形式，如`IUserService`, `UserService`
- **测试类命名**: `{ClassName}Test`

#### XML/XDef规范
- **文件编码**: UTF-8
- **根元素**: 对应XDef元模型定义
- **属性命名**: camelCase
- **元素嵌套**: 遵循XDef元模型结构

#### JavaScript/JSON规范
- **JSON格式**: 2空格缩进
- **属性命名**: camelCase（JavaScript）
- **组件命名**: PascalCase（Vue组件）

### Architecture Patterns

#### 可逆计算原理
Nop平台基于可逆计算理论，核心模式为：
```
Original Product × Δ1 × Δ2 × ... = Final Product
```

**Delta模式**：
- Δ1：产品通用定制
- Δ2：客户专属定制
- 不修改原始产品代码，通过Delta叠加实现定制

#### 模块依赖层次
```
nop-commons (基础工具)
    ↓
nop-core (虚拟文件系统、反射、XML解析)
    ↓
nop-xlang (XLang语言引擎)
    ↓
nop-ioc (依赖注入容器)
    ↓
业务模块层
```

#### 虚拟文件系统(VFS)
- 资源路径模式：`classpath:/`, `file:/`, `http:/`
- Delta文件自动合并：`.delta.xml`自动叠加到基础文件
- 多层资源搜索：系统→应用→定制→运行时

#### 模型驱动开发(MDD)
- Excel模型定义业务对象
- XDef元模型定义DSL语言结构
- 代码生成器生成代码和文档
- 运行时动态加载模型

### Testing Strategy

#### 测试分层
1. **单元测试**: 单个类/方法测试
2. **集成测试**: 模块间交互测试
3. **端到端测试**: 完整业务流程测试

#### 测试框架
- **单元测试**: JUnit 5
- **Mock框架**: Mockito
- **覆盖率**: JaCoCo（目标：70%以上）
- **自动化测试**: nop-autotest（录制回放机制）

#### 测试命名规范
```java
@Test
public void testUserService_createUser_success() {
    // 测试方法名格式: test{ClassName}_{methodName}_{scenario}
}
```

#### 录制回放测试
使用nop-autotest框架：
```java
// 第一遍运行：录制
@Test
public void录制测试数据() {
    AutotestHelper.run("test/case/user-service-test.xml");
}

// 后续运行：自动验证
```

### Git Workflow

#### 分支策略
```
main/master (稳定版本)
    ↓
develop (开发主线)
    ↓
feature/{feature-name} (功能分支)
    ↓
bugfix/{bug-name} (bug修复分支)
```

#### Commit规范
遵循Conventional Commits：
```
type(scope): subject

<body>

<footer>
```

**类型（type）**：
- `feat`: 新功能
- `fix`: bug修复
- `refactor`: 重构（不改变行为）
- `perf`: 性能优化
- `test`: 测试相关
- `docs`: 文档更新
- `style`: 代码格式（不影响功能）
- `chore`: 构建/工具相关

**示例**：
```
feat(orm): add batch insert support

Implement batch insert for multiple entities in a single SQL statement.
Optimized for 1000+ records per batch.

Closes #123
```

#### Pull Request流程
1. 从`develop`创建特性分支
2. 完成开发和测试
3. 提交PR到`develop`
4. Code Review
5. CI/CD自动检查
6. 合并后删除分支

## Domain Context

### 核心概念

#### 1. XDef (XML Definition)
元模型语言，用于定义DSL的结构和约束。
- 定义XML元素、属性
- 指定数据类型和验证规则
- 自动生成解析器和验证器

#### 2. XLang
平台核心语言，包含：
- **XScript**: 脚本语言（类似JavaScript）
- **Xpl**: 模板语言（类似JSP/Velocity）
- **XLib**: 函数库定义

#### 3. Delta机制
可逆计算的核心实现：
- `.delta.xml`文件描述变更
- 运行时自动合并Delta到基础模型
- 支持多层Delta叠加

#### 4. 虚拟文件系统(VFS)
抽象的文件访问层：
- 支持多种协议（classpath, file, http等）
- Delta文件自动合并
- 资源缓存和热重载

#### 5. 元编程
运行时代码生成和执行：
- 基于XDef元模型生成代码
- 动态Bean工厂
- AOP切面编程

### 主要模块说明

#### 核心框架
- **nop-core**: 基础工具类、虚拟文件系统、反射机制
- **nop-xlang**: XLang语言引擎
- **nop-ioc**: 依赖注入容器
- **nop-config**: 动态配置中心

#### 数据层
- **nop-dao**: JDBC抽象层、事务管理
- **nop-orm**: ORM引擎（不使用JPA）
- **nop-persistence**: 持久化扩展

#### 服务层
- **nop-graphql**: GraphQL引擎
- **nop-rpc**: 分布式RPC调用
- **nop-rest**: REST服务封装

#### 业务引擎
- **nop-wf**: 工作流引擎
- **nop-rule**: 规则引擎
- **nop-report**: 报表引擎
- **nop-batch**: 批处理引擎
- **nop-job**: 任务调度引擎

#### 权限和安全
- **nop-auth**: 认证授权
- **nop-sys**: 系统管理

#### 前端支持
- **nop-frontend-support**: 前端组件库
- **nop-xui**: XView视图模型

### 业务对象命名规范

#### 数据库表名
- 格式：`t_{业务域}_{实体名}`
- 示例：`t_sys_user`, `t_auth_role`

#### 字段名
- 格式：`column_name`（snake_case）
- 示例：`user_id`, `created_at`

#### Java实体类
- 格式：`{实体名}Entity`
- 示例：`UserEntity`, `RoleEntity`

#### GraphQL类型
- 格式：`{实体名}`
- 示例：`User`, `Role`

#### XView视图
- 格式：`{业务域}/{实体名}.view.xml`
- 示例：`sys/user.list.view.xml`

## Important Constraints

### 技术约束
1. **Java版本**: 必须使用Java 17+，不支持Java 8
2. **框架依赖**: 核心模块不依赖Spring、MyBatis等第三方框架
3. **数据库**: 主要支持MySQL和PostgreSQL，Oracle支持有限
4. **性能要求**: 查询响应时间< 1s，批处理吞吐量> 1000 records/s
5. **内存要求**: 单机运行至少2GB内存
6. **线程模型**: 使用Quarkus的Vert.x非阻塞模型

### 业务约束
1. **兼容性**: 向后兼容是必须的，breaking changes需要major版本更新
2. **事务一致性**: 跨服务事务使用TCC模式（nop-tcc模块）
3. **数据权限**: 支持行级数据权限控制
4. **审计日志**: 关键操作必须记录审计日志
5. **多租户**: 核心模块支持多租户架构

### 安全约束
1. **认证**: 支持JWT和SSO（Keycloak集成）
2. **授权**: 基于RBAC（角色-权限-资源）
3. **加密**: 敏感数据必须加密存储（使用nop-auth加密工具）
4. **SQL注入**: 严禁字符串拼接SQL，必须使用参数化查询
5. **XSS防护**: 前端输出必须转义（AMIS框架自动处理）

### 许可证约束
- **整体协议**: AGPL 3.0
- **中小企业豁免**: 可免费商用（保留版权信息）
- **Apache 2.0模块**:
  - nop-api-support
  - nop-commons
  - nop-core
  可作为第三方依赖使用

## External Dependencies

### 核心依赖
- **Quarkus**: 3.29.2（云原生微服务框架）
- **GraalVM**: 原生编译支持
- **Antlr**: 语法解析器
- **Log4j2**: 日志框架

### 数据库相关
- **HikariCP**: 数据库连接池
- **PostgreSQL Driver**: PostgreSQL支持
- **MySQL Connector**: MySQL支持

### 前端相关
- **Vue.js**: 2.x/3.x
- **AMIS**: 百度低代码UI框架
- **Webpack**: 前端构建工具

### 工具库
- **Apache Commons**: 工具类库
- **Jackson**: JSON处理
- **Joda-Time**: 日期时间处理

### 测试相关
- **JUnit 5**: 测试框架
- **Mockito**: Mock框架
- **JaCoCo**: 代码覆盖率

### 文档和规范
- **OpenSpec**: 规范驱动开发工作流
- **OpenCode**: AI编码助手

### 集成服务
- **Keycloak**: SSO单点登录
- **Kafka**: 消息队列（nop-message模块）
- **Redis**: 缓存（计划中）
- **Elasticsearch**: 搜索（nop-search模块）

## 开发最佳实践

### 代码生成优先
- 优先使用Excel模型生成代码
- 避免手写CRUD操作
- 使用nop-codegen生成标准代码结构

### Delta定制
- 定制化开发优先使用Delta文件
- 不修改基础产品Jar包代码
- Delta文件放在`_delta/`目录下

### 元模型设计
- 新增DSL先设计XDef元模型
- 元模型驱动代码生成
- 保持元模型简洁和一致

### 性能优化
- 使用虚拟文件系统缓存
- 批量操作使用批量API
- 查询使用索引优化

### 错误处理
- 使用统一的错误码（nop-core中的NopException）
- 异常信息国际化（i18n）
- 记录完整的错误堆栈（开发模式）

### 文档编写
- 代码注释使用JavaDoc格式
- API文档从GraphQL Schema自动生成
- 设计文档使用Markdown格式（docs/目录）

## AI助手使用指南

### 常见任务AI助手可以辅助
1. **代码生成**: 根据Excel模型生成实体、DAO、GraphQL服务
2. **Delta文件编写**: 创建定制化Delta配置
3. **测试用例生成**: 使用nop-autotest录制回放
4. **文档生成**: 生成API文档、用户手册
5. **代码审查**: 检查代码规范和最佳实践
6. **Bug诊断**: 分析错误日志和堆栈
7. **性能优化**: 识别性能瓶颈

### AI助手工作流
1. **理解上下文**: 阅读`openspec/project.md`和相关规格
2. **创建提案**: 对于大功能，先创建OpenSpec变更提案
3. **编写代码**: 按照tasks.md逐步实施
4. **验证结果**: 运行测试并确认任务完成
5. **更新状态**: 标记tasks.md中的完成项

### 上下文提示关键词
- "基于可逆计算原理"
- "使用Delta机制"
- "遵循XDef元模型"
- "不依赖Spring"
- "使用Quarkus框架"
- "模型驱动开发"

## 联系方式

- **官方文档**: https://nop-platform.github.io/
- **GitHub**: https://github.com/entropy-cloud/nop-entropy
- **Gitee**: https://gitee.com/canonical-entropy/nop-entropy
- **问题反馈**: 在GitHub/Gitee提交Issue
- **技术讨论**: 微信群（见README.md中的二维码）
