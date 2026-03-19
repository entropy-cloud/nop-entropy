---
name: nop-nodejs-backend
description: Node.js 后端服务技术架构设计。基于 NestJS + Prisma + SQLite 的原型开发规范，支持快速原型开发和未来迁移到 Java。触发词：Node.js 后端、NestJS、Prisma、后端架构、API 设计。
---

# Node.js 原型后端开发 Skill（面向 AI 生成与业务演示）

## 1. 引言
### 1.1 背景
本文档用于指导基于 Node.js 的业务原型后端开发，目标是**尽快做出一个可以演示真实业务流程的后端原型**，并与现有产品接口风格保持一致。它不是通用 Node.js 最佳实践文档，也不是生产架构规范，而是一个**面向 AI 生成代码、面向业务演示、面向后续 AI 迁移分析**的实现约束。

为降低 AI 生成歧义并提升开发效率，本文档采用如下固定约定：
- 所有 API 接口统一使用 HTTP POST 方法
- 所有请求参数全部置于 JSON Body 中
- 路径中不包含参数
- 数据库采用 **SQLite**，优先服务于原型快速开发
- **代码层（DTO、服务层）使用 camelCase 命名，数据库层遵循 snake_case 规范**，通过 Prisma 的 `@map` 指令实现映射

未来后端会由 AI 迁移为定制 Java 框架实现，因此这里的 Node.js 代码主要承担两类职责：
- 支撑原型阶段的真实业务演示
- 为后续 AI 分析业务逻辑、迁移实现提供清晰输入

### 1.2 目标
- 快速构建可演示真实业务的 Node.js 后端原型
- 与现有产品接口风格保持一致，降低前后端联调成本
- 使用 SQLite 作为数据库，无需额外安装数据库服务，优先提升原型开发效率
- 代码层统一使用 camelCase，与 TypeScript 风格一致，提升开发体验
- 数据库遵循 snake_case 规范，通过 Prisma `@map` 实现字段名映射
- 主键使用 UUID 字符串，保证接口层数据表达稳定
- 让业务逻辑足够清晰，便于后续 AI 理解并迁移到定制 Java 框架

### 1.3 适用范围
- 适用于单体型、可快速交付的业务原型后端
- 适用于接口规范已经由现有产品体系约束的场景
- 适用于需要让 AI 参与代码生成和后续迁移分析的场景

### 1.4 非目标
- 不追求生产可用性
- 不追求通用 REST 风格规范
- 不考虑高并发、高可用、复杂事务一致性
- 不考虑国际化、复杂权限模型、多租户等企业级能力
- 不为未来扩展预先引入额外抽象层

## 2. 技术选型（原型版）
### 2.1 核心框架（基于最新稳定版）
| 组件 | 选型 | 版本 | 说明 |
|------|------|------|------|
| 运行时 | Node.js | **22.x LTS** | 最新的长期支持版本 |
| Web框架 | NestJS | **11.x** | 模块化、TypeScript优先 |
| 数据库 | SQLite | 3.x | 嵌入式数据库，零配置 |
| ORM/数据层 | Prisma | **7.3.0** | 模型驱动，支持字段映射 |
| API文档 | @nestjs/swagger | **11.2.5** | 集成 Swagger UI |
| 验证 | class-validator | **0.15.0** | 声明式验证 |
| 类型转换 | class-transformer | 最新 | 配合验证使用 |
| 配置管理 | @nestjs/config | 最新 | 环境变量管理 |
| 日志 | NestJS 内置日志 | - | 开发阶段简洁日志 |
| 测试 | Jest | 29.x | 单元测试、e2e测试 |
| **包管理器** | **pnpm** | **8.x** | 高效磁盘利用、严格依赖管理，推荐使用 corepack 启用 |

## 3. 架构原则
- **模块化**：业务功能按模块划分。
- **契约先行**：API 设计基于 OpenAPI，并保持与现有产品接口风格兼容。
- **模型驱动**：数据库结构以 `schema.prisma` 为唯一事实来源。
- **分层清晰**：控制器 → 服务 → Prisma Client。
- **命名分离**：代码层使用 camelCase，数据库层使用 snake_case，通过 ORM 映射解耦。
- **原型优先**：优先降低实现复杂度，不引入与演示目标无关的工程抽象。
- **AI 友好**：代码应直白表达业务流程，减少框架技巧、隐式行为和过度封装。

### 3.1 AI 生成代码硬约束
- **接口风格固定**：只使用 HTTP POST；禁止生成 `GET`、`PUT`、`PATCH`、`DELETE` 风格接口。
- **参数位置固定**：所有参数都在 JSON Body 中；禁止使用路径参数和查询参数承载业务参数。
- **分层保持简单**：固定为 Controller、Service、Prisma Service；不要引入 repository、domain、assembler 等额外层次。
- **ORM 固定**：只使用 Prisma；不要引入 TypeORM、Sequelize、MikroORM 等替代方案。
- **字段命名固定**：DTO、Service、Prisma 模型属性统一使用 camelCase；数据库表和列使用 snake_case。
- **实现风格固定**：优先写清楚业务规则和处理流程，不为“未来扩展”预埋复杂抽象。

## 4. 目录结构
```
project-root/
├── prisma/
│   ├── schema.prisma            # 数据模型定义（camelCase字段，snake_case列通过@map映射）
│   └── migrations/              # 迁移文件（可选）
├── src/
│   ├── main.ts
│   ├── app.module.ts
│   ├── config/
│   │   ├── env.validation.ts
│   │   └── database.config.ts
│   ├── common/
│   │   ├── filters/
│   │   ├── interceptors/
│   │   ├── interfaces/
│   │   │   ├── api-response.interface.ts
│   │   │   └── page-bean.interface.ts
│   │   └── ...
│   ├── modules/
│   │   └── users/
│   │       ├── dto/
│   │       │   ├── getUser.dto.ts
│   │       │   ├── createUser.dto.ts
│   │       │   ├── updateUser.dto.ts
│   │       │   ├── deleteUser.dto.ts
│   │       │   └── userPageQuery.dto.ts
│   │       ├── users.controller.ts
│   │       ├── users.service.ts
│   │       └── users.module.ts
│   └── shared/
│       └── prisma/
│           ├── prisma.service.ts
│           └── prisma.module.ts
├── test/
│   └── users.e2e-spec.ts
├── .env.example
├── package.json
└── ...
```

### 4.1 新增业务模块最小交付清单
新增一个业务模块时，至少生成以下内容：
- `src/modules/<module>/<module>.module.ts`
- `src/modules/<module>/<module>.controller.ts`
- `src/modules/<module>/<module>.service.ts`
- `src/modules/<module>/dto/create<Module>.dto.ts`
- `src/modules/<module>/dto/update<Module>.dto.ts`
- `src/modules/<module>/dto/get<Module>.dto.ts`
- `src/modules/<module>/dto/delete<Module>.dto.ts`
- `src/modules/<module>/dto/<module>PageQuery.dto.ts`
- 对应 Prisma 模型定义
- 至少一个 e2e 测试文件

如模块较简单，可根据实际情况减少 DTO 数量，但必须保持接口风格、命名风格和分页结构一致。

## 5. 数据库设计规范
### 5.1 命名规范
- **表名**：snake_case，复数形式，通过 `@@map` 指定。
- **列名**：snake_case，全小写（如 `full_name`、`is_active`）。
- **代码层字段名**：camelCase（如 `fullName`、`isActive`），通过 Prisma 的 `@map` 映射到底层列名。
- **主键**：统一命名为 `id`，类型为 `String`，使用 UUID 填充。
- **外键**：camelCase（如 `userId`），通过 `@map` 映射为 `user_id`。
- **时间字段**：`createdAt`、`updatedAt`，映射为 `created_at`、`updated_at`。

### 5.2 模型定义（Prisma schema）
```prisma
// prisma/schema.prisma
generator client {
  provider = "prisma-client-js"
}

datasource db {
  provider = "sqlite"
  url      = env("DATABASE_URL")
}

model users {
  id        String   @id @default(uuid()) @map("id")          // 列名 id
  email     String   @unique @map("email")                    // 列名 email
  fullName  String?  @map("full_name")                        // 列名 full_name
  isActive  Boolean  @default(true) @map("is_active")         // 列名 is_active
  createdAt DateTime @default(now()) @map("created_at")       // 列名 created_at
  updatedAt DateTime @updatedAt @map("updated_at")            // 列名 updated_at

  @@map("users")  // 表名 users (snake_case)
}
```
**说明**：
- 代码中访问模型属性时使用 `user.fullName`、`user.isActive`，而数据库列名分别为 `full_name`、`is_active`。
- Prisma 客户端自动处理映射，无需服务层额外转换。

### 5.3 自动同步数据库
原型开发阶段，允许使用 `prisma db push` 自动同步表结构，以减少迁移维护成本：
```typescript
// src/main.ts
if (process.env.NODE_ENV === 'development' && process.env.DATABASE_URL?.startsWith('file:')) {
  execSync('npx prisma db push --accept-data-loss', { stdio: 'inherit' });
}
```

该策略仅适用于本 skill 定义的原型开发场景，不用于生产环境。

## 6. API 设计规范
### 6.1 统一接口风格
- **所有接口使用 HTTP POST**，路径中不含参数（如 `/users/get`、`/users/create`）。这是产品特定要求，用于与现有技术体系兼容，并降低 AI 生成和迁移时的歧义。
- **请求参数全部放在 JSON Body**，包括 `id`、分页参数等。
- 无参数操作发送 `{}`。

### 6.2 响应格式
- 成功：`{ "status": 0, "data": ... }`
- 失败：`{ "status": 非0, "code": "...", "msg": "...", "errors": {...} }`
- 分页：`{ "status": 0, "data": PageBean }`

### 6.3 DTO 定义
DTO 使用 camelCase，与 Prisma 模型属性名一致（无需关心数据库列名）。

示例：`createUser.dto.ts`
```typescript
import { ApiProperty } from '@nestjs/swagger';
import { IsEmail, IsString, MinLength, IsOptional, IsBoolean } from 'class-validator';

export class CreateUserDto {
  @ApiProperty()
  @IsEmail()
  email: string;

  @ApiProperty({ required: false })
  @IsOptional()
  @IsString()
  @MinLength(2)
  fullName?: string;

  @ApiProperty({ required: false, default: true })
  @IsOptional()
  @IsBoolean()
  isActive?: boolean;
}
```

示例：`getUser.dto.ts`
```typescript
import { ApiProperty } from '@nestjs/swagger';
import { IsUUID } from 'class-validator';

export class GetUserDto {
  @ApiProperty()
  @IsUUID()
  id: string;
}
```

### 6.4 控制器示例
```typescript
@Controller('users')
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Post('get')
  async get(@Body() dto: GetUserDto) {
    const user = await this.usersService.findOne(dto.id);
    if (!user) throw new BusinessException('USER_NOT_FOUND', '用户不存在');
    return user;  // 拦截器自动包装
  }

  @Post('create')
  async create(@Body() dto: CreateUserDto) {
    return this.usersService.create(dto);
  }

  @Post('page')
  async page(@Body() query: UserPageQueryDto) {
    return this.usersService.findPage(query);
  }
}
```

### 6.5 服务层实现
由于 Prisma 模型属性名与 DTO 字段名一致，服务层可直接传递 DTO 对象，无需手动映射字段名（映射已在 schema 层通过 `@map` 处理）。对于原型项目，这是有意保留的简化策略。

```typescript
@Injectable()
export class UsersService {
  constructor(private prisma: PrismaService) {}

  async create(dto: CreateUserDto) {
    return this.prisma.users.create({
      data: dto,  // dto 字段名与模型属性名一致
    });
  }

  async findOne(id: string) {
    return this.prisma.users.findUnique({ where: { id } });
  }

  async findPage(query: UserPageQueryDto): Promise<PageBean<UserResponseDto>> {
    const { page, pageSize, filters, sort } = query;
    const offset = (page - 1) * pageSize;
    const limit = pageSize;

    const [items, total] = await Promise.all([
      this.prisma.users.findMany({
        skip: offset,
        take: limit,
        where: this.buildWhere(filters),
        orderBy: this.buildOrderBy(sort),
      }),
      this.prisma.users.count({ where: this.buildWhere(filters) }),
    ]);

    return {
      items: items.map(user => new UserResponseDto(user)),
      total,
      offset,
      limit,
      hasPrev: offset > 0,
      hasNext: offset + limit < total,
    };
  }
}
```

**注意**：`UserResponseDto` 的字段也应使用 camelCase，与模型一致，可直接通过构造函数赋值。

### 6.6 AI 编码注意事项
- 不要为了“更标准”而改写为 RESTful 风格接口。
- 不要将简单业务逻辑拆成过多辅助类。
- 不要为了迁移 Java 而模拟 Spring、MyBatis 等常见 Java 技术栈分层。
- 代码重点是让业务规则易读、易测、易被 AI 理解，而不是追求架构教科书式完整性。

## 7. 异常处理与响应拦截
- 全局异常过滤器统一错误格式。
- 全局响应拦截器包装成功响应。
- 错误处理保持简单直接，以便前端联调和 AI 理解业务分支。

## 8. 配置管理
`.env.example`：
```
DATABASE_URL="file:./dev.db"
PORT=3000
NODE_ENV=development
```

## 9. 测试策略
- **单元测试**：模拟 PrismaService。
- **e2e测试**：使用独立测试数据库，测试前运行 `prisma db push` 重置。
- 测试目标是验证关键业务流程可演示，不追求生产级覆盖率。

## 10. 部署与运维
原型阶段直接启动，数据库文件 `dev.db` 随代码部署。该后端仅用于开发和演示真实业务原型，不要求具备生产部署能力。

## 11. 未来替换为 Java 的注意事项
### 11.1 API 契约不变
所有 API 路径、请求/响应结构由 OpenAPI 文档定义，Java 后端可据此重新实现。

### 11.2 数据库迁移
- 当前使用 SQLite，但模型已通过 `@map` 将字段映射为 snake_case，符合生产数据库规范。
- 如需切换到 PostgreSQL，可修改 `provider` 和连接字符串，模型定义中的 `@map` 可继续保留。

### 11.3 主键类型
- Node.js 端使用 `String` 存储 UUID，后续迁移时只需保证接口层继续以字符串形式传递即可。

### 11.4 业务逻辑迁移
- Node.js 原型代码不是 Java 实现模板，不要求代码结构一一对应。
- 更重要的是让业务规则、校验逻辑、分页逻辑、状态变化逻辑表达清楚，便于 AI 后续分析和迁移。
- 避免过多依赖框架黑盒行为，避免把关键业务语义藏在复杂配置中。

## 12. 快速开始
```bash
git clone <repo>
# 启用 corepack（如未启用）
corepack enable
# 安装依赖
pnpm install
# 复制 .env.example 为 .env
# Windows PowerShell: Copy-Item .env.example .env
# macOS/Linux: cp .env.example .env
pnpm run start:dev
# 访问 http://localhost:3000/api
```
