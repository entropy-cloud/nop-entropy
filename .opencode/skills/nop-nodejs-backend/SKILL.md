---
name: nop-nodejs-backend
description: (opencode-project - Skill) Node.js 后端服务技术架构设计。基于 NestJS + Prisma + SQLite 的原型开发规范，支持快速原型开发和未来迁移到 Java。触发词：Node.js 后端、NestJS、Prisma、后端架构、API 设计。
---

# Node.js 后端服务技术架构设计文档（SQLite 原型版）

## 1. 引言
### 1.1 背景
本文档旨在为基于 Node.js 的企业级后端服务提供一套完整的技术架构设计，**特别适用于原型快速开发阶段**。项目严格遵循前后端分离原则，API 优先设计，确保与前端团队高效协作，并充分考虑**未来可将后端服务替换为 Java 等语言实现**的可能性。为提升开发效率，所有 API 接口统一使用 HTTP POST 方法，请求参数全部置于 JSON Body 中，路径中不包含任何参数；数据库采用 **SQLite**，启动时自动同步表结构；**代码层（DTO、服务层）使用 camelCase 命名，数据库层遵循 snake_case 规范**，通过 Prisma 的 `@map` 指令实现无缝映射。

### 1.2 目标
- 构建可维护、可扩展、高性能的 Node.js 后端服务原型
- 使用 SQLite 作为数据库，无需额外安装数据库服务，启动时自动同步表结构
- 代码层统一使用 camelCase，与 TypeScript 风格一致，提升开发体验
- 数据库严格遵循 snake_case 规范，符合 PostgreSQL 等生产数据库的最佳实践
- 通过 Prisma `@map` 实现字段名映射，消除服务层手动转换，保持代码简洁
- 主键使用 UUID 字符串，便于分布式系统扩展
- 确保 API 契约清晰、与语言无关，便于未来技术栈迁移

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
- **契约先行**：API 设计基于 OpenAPI。
- **模型驱动**：数据库结构以 `schema.prisma` 为唯一事实来源。
- **分层清晰**：控制器 → 服务 → Prisma Client。
- **命名分离**：代码层使用 camelCase，数据库层使用 snake_case，通过 ORM 映射解耦。
- **可替换性**：API 契约与语言无关。

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
原型开发阶段，启动时自动同步表结构（使用 `prisma db push`）：
```typescript
// src/main.ts
if (process.env.NODE_ENV === 'development' && process.env.DATABASE_URL?.startsWith('file:')) {
  execSync('npx prisma db push --accept-data-loss', { stdio: 'inherit' });
}
```

## 6. API 设计规范
### 6.1 统一接口风格
- **所有接口使用 HTTP POST**，路径中不含参数（如 `/users/get`、`/users/create`）。
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
由于 Prisma 模型属性名与 DTO 字段名一致，服务层可直接传递 DTO 对象，无需手动映射字段名（映射已在 schema 层通过 `@map` 处理）。

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

## 7. 异常处理与响应拦截
- 全局异常过滤器统一错误格式。
- 全局响应拦截器包装成功响应。

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

## 10. 部署与运维
原型阶段直接启动，数据库文件 `dev.db` 随代码部署。SQLite 不适用于高并发生产环境。

## 11. 未来替换为 Java 的注意事项
### 11.1 API 契约不变
所有 API 路径、请求/响应结构由 OpenAPI 文档定义，Java 后端可据此重新实现。

### 11.2 数据库迁移
- 当前使用 SQLite，但模型已通过 `@map` 将字段映射为 snake_case，符合生产数据库规范。
- 迁移到 PostgreSQL 时，只需修改 `provider` 和连接字符串，模型定义中的 `@map` 可保留（PostgreSQL 也支持 snake_case 列名）。
- Java 端可使用 JPA，通过 `@Column(name = "full_name")` 映射实体字段（camelCase）到数据库列（snake_case），与 Node.js 端思路一致。

### 11.3 主键类型
- Node.js 端使用 `String` 存储 UUID，Java 端可使用 `java.util.UUID` 或 `String`，序列化后均为字符串，兼容。

### 11.4 业务逻辑迁移
- 服务层可抽离为纯 TypeScript 类（不含 NestJS 装饰器），便于用 Java 重写。

## 12. 快速开始
```bash
git clone <repo>
# 启用 corepack（如未启用）
corepack enable
# 安装依赖
pnpm install
cp .env.example .env
pnpm run start:dev
# 访问 http://localhost:3000/api
```
