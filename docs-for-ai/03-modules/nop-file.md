# nop-file — 文件管理模块

## 功能概览

文件上传/下载管理。

- 文件上传与下载
- 文件记录追踪
- Hash 去重（相同文件只存一份）
- 业务对象关联（bizObjName + bizObjId + fieldName）
- 公开/私有访问控制

## 核心实体

| 实体 | 表名 | 用途 |
|------|------|------|
| NopFileRecord | `nop_file_record` | 文件记录 |

## 关键字段

**NopFileRecord**:
- `fileName`：原始文件名
- `filePath`：存储路径
- `fileExt`：扩展名
- `mimeType`：MIME 类型
- `fileLength`：文件大小
- `fileHash`：文件 Hash（用于去重）
- `bizObjName`：关联业务对象名
- `bizObjId`：关联业务对象 ID
- `fieldName`：关联字段名
- `originFileId`：原始文件 ID（去重引用）
- `isPublic`：是否公开访问

## 子模块

| 子模块 | 职责 |
|--------|------|
| `nop-file-api` | API DTO 与接口 |
| `nop-file-dao` | ORM 实体与 DAO |
| `nop-file-service` | 业务逻辑 |
| `nop-file-web` | Web 层与 AMIS 页面 |

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-file/model/nop-file.orm.xml` |

## 相关文档

- `../reusable-modules-overview.md`
