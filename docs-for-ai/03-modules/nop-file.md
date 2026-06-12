# nop-file — 文件上传下载与存储

## 功能概览

nop-file 提供统一的文件上传/下载/存储能力：

- **REST API**：`POST /f/upload`、`GET /f/download/{fileId}`（引入 `nop-spring-file` 后自动可用）
- **存储后端**：本地文件系统（默认）、S3/OSS/MinIO（可选切换）
- **ORM 文件字段**：列声明 `stdDomain="file"` 后，组件/XMeta/FileStatus 全部自动生成
- **FileStatus**：前端查询实体时自动返回文件元信息（文件名、大小、预览链接等），批量加载无 N+1

## 快速开始

### 引入依赖

```xml
<!-- 文件上传下载（含 DAO + Service + BizModel） -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-file-service</artifactId>
</dependency>
<!-- Spring Boot REST 端点 /f/upload 和 /f/download -->
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-spring-file</artifactId>
</dependency>
```

OSS 存储额外引入：
```xml
<dependency>
    <groupId>io.github.entropy-cloud</groupId>
    <artifactId>nop-integration-oss</artifactId>
</dependency>
```

### 配置

**本地存储**（默认，零配置）：
```yaml
nop:
  file:
    store-dir: /data/nop-file  # 可选，默认 /nop/file
```

**OSS/S3/MinIO**：
```yaml
nop:
  file:
    store-impl: oss
  integration:
    oss:
      enabled: true
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      access-key: YOUR_ACCESS_KEY
      secret-key: YOUR_SECRET_KEY
      default-bucket-name: my-app-files
```

`nop-integration-oss` 基于 `aws-java-sdk-s3`，兼容阿里云 OSS、腾讯云 COS、MinIO 等所有 S3 协议存储。

### REST API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/f/upload` | 上传（multipart/form-data，字段名 `file`，可选参数 `bizObjName`、`fieldName`） |
| GET | `/f/download/{fileId}` | 下载 |
| POST | `/f/download/{fileId}` | 下载（POST 方式） |

上传响应：
```json
{
  "status": 0,
  "data": {
    "value": "/f/download/xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
    "filename": "原始文件名.pdf"
  }
}
```

GraphQL 操作：`NopFileStore__upload`（mutation）、`NopFileStore__download`（query）。

## 在 ORM 模型中声明文件字段

**只需在列上设置 `stdDomain="file"`**，后续组件、XMeta、前端 FileStatus 全部自动生成。

### 单文件字段

```xml
<!-- 方式 1：使用预定义 domain -->
<column code="AVATAR" name="avatar" displayName="头像" domain="image"
        stdDataType="string" stdSqlType="VARCHAR"/>

<!-- 方式 2：直接指定 stdDomain -->
<column code="CERT_FILE" name="certFile" displayName="证书文件" precision="200"
        stdDataType="string" stdSqlType="VARCHAR">
    <schema stdDomain="file"/>
</column>
```

预定义 domain（`default.orm.xml`）：`image`（precision=100, stdDomain=file）。应用可在自己的 ORM 中定义更多：
```xml
<domain name="file" precision="200" stdDomain="file" stdSqlType="VARCHAR"/>
```

### 多文件字段

```xml
<column code="ATTACHMENTS" name="attachments" displayName="附件" precision="500"
        stdDataType="string" stdSqlType="VARCHAR">
    <schema stdDomain="file-list"/>
</column>
```

### 自动生成的产物

以 `name="avatar"` + `stdDomain="file"` 为例：

| 层 | 生成物 | 说明 |
|----|--------|------|
| ORM | `avatarComponent`（OrmFileComponent） | 绑定到 AVATAR 列，自动管理 attach/detach |
| Entity | `getAvatarComponent()` | 生成在 `_XxxEntity.java` |
| XMeta | `avatarComponentFileStatus` | `mapToProp="avatarComponent.fileStatus"`，类型 `FileStatusBean` |
| View | 自动加入 GraphQL Selection | 字段 `stdDomain="file"` 或 `control="file"` 时自动将 FileStatus 字段加入查询 |

### FileStatusBean

前端查询实体时自动返回，包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `fileId` | String | 文件 ID |
| `name` | String | 原始文件名 |
| `size` | long | 文件大小（字节） |
| `fileSize` | String | 计算属性，人类可读大小（如 "1.5MB"） |
| `lastModified` | long | 最后修改时间戳 |
| `externalPath` | String | 外部访问 URL（基类为 null，OSS 子类可覆盖返回 CDN 链接） |
| `previewPath` | String | 预览 URL |

查询走 ORM 批量加载队列，多条记录的 FileStatus 合并为一次 SQL 查询，无 N+1 问题。

### OrmFileComponent 生命周期

ORM 文件组件在实体 flush/delete 时自动管理文件绑定：

- **实体保存/更新**：filePath 变更时自动 detach 旧文件 + attach 新文件
- **实体删除**：自动 detach 并删除文件记录；如果物理文件无其他引用则一并删除
- **实体复制**：`copyFile()` 创建新记录指向同一物理文件

前端使用：实体字段的 `stdDomain="file"` 或 `control="file"` 时，`XuiViewAnalyzer` 自动将 `{propName}ComponentFileStatus` 的所有字段加入 GraphQL Selection，前端无需手动配置。

## 在 BizModel 中编程式操作文件

```java
@BizModel("MyEntity")
public class MyEntityBizModel extends CrudBizModel<MyEntity> {

    @Inject
    IFileStore fileStore;

    @BizMutation
    public String uploadSomething(@RequestBean UploadRequestBean record, IServiceContext ctx) {
        String fileId = fileStore.saveFile(record, maxFileSize);
        String downloadLink = fileStore.getFileLink(fileId);  // "/f/download/{fileId}"
        return downloadLink;
    }

    @BizQuery
    public WebContentBean downloadFile(@Name("fileId") String fileId, IServiceContext ctx) {
        IFileRecord record = fileStore.getFile(fileId);
        return new WebContentBean(record.getMimeType(), record.getResource(), record.getFileName());
    }
}
```

Bean ID：`nopFileStore`（同时 alias 为 `nopOrmEntityFileStore`）。

## 配置项汇总

| 属性 | 默认值 | 说明 |
|------|--------|------|
| `nop.file.store-impl` | (空=local) | 存储后端: `local` 或 `oss` |
| `nop.file.store-dir` | `/nop/file` | 本地存储根目录 |
| `nop.file.upload.max-size` | `16777216` (16MB) | 最大上传文件大小 |
| `nop.file.upload.allowed-file-exts` | (空=全部) | 允许的扩展名白名单 |
| `nop.integration.oss.enabled` | `false` | 启用 OSS |
| `nop.integration.oss.endpoint` | - | S3 端点 URL |
| `nop.integration.oss.access-key` | - | Access Key |
| `nop.integration.oss.secret-key` | - | Secret Key |
| `nop.integration.oss.default-bucket-name` | `nop-file` | 默认 Bucket |
| `nop.integration.oss.path-style-access` | `true` | 路径风格访问（MinIO 必须为 true） |
| `nop.integration.oss.auto-create-bucket` | `false` | 自动创建 Bucket |
| `nop.integration.oss.custom-domain` | - | 自定义域名 |

## 核心实体

`nop_file_record` 表，逻辑删除，记录文件元数据和业务关联。主要字段：

- `fileId` (PK, UUID) — 文件 ID
- `fileName` — 原始文件名
- `filePath` — 存储路径（如 `/{bizObjName}/{YYYY}/{MM}/{DD}/{fileId}.{ext}`）
- `fileExt` / `mimeType` / `fileLength` — 扩展名、MIME、大小
- `bizObjName` + `bizObjId` + `fieldName` — 业务关联三元组（未关联时 bizObjId = `__TEMP__`）
- `originFileId` — 原始文件 ID（跟踪复制来源）
- `isPublic` — 是否公开访问

## 自定义 MIME 映射

通过 VFS 提供 `/nop/file/media-type.json`：
```json
{
  "docx": "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
}
```

## 分桶策略

`DaoResourceFileStore` 支持按业务实体分桶存储（覆盖 `getBucketName()`）。路径：
- 分桶：`bkt_{bucketName}/{bizObjName}/{YYYY}/{MM}/{DD}/{fileId}.{ext}`
- 不分桶：`/{bizObjName}/{YYYY}/{MM}/{DD}/{fileId}.{ext}`

## 注意事项

1. **不要手动编辑生成文件**：`NopFileRecord` 实体类、`OrmFileComponent` 组件、`FileStatus` xmeta prop 均由模型自动生成。
2. **跨实体文件操作**：BizModel 中注入 `IFileStore`（bean ID: `nopFileStore`），不要直接操作 `NopFileRecord` DAO。
3. **权限检查**：非公开文件下载会检查访问权限；公开文件（`isPublic=true`）可直接通过链接下载。
4. **临时文件**：上传未关联到实体的文件 `bizObjId = "__TEMP__"`，应用需定期清理。
5. **分块上传**：`ChunkFileUploadHandler` 已预留 DTO 和方法签名，但实现为空桩，暂不可用。

## 源码锚点

| 组件 | 路径 |
|------|------|
| ORM 模型 | `nop-file/model/nop-file.orm.xml` |
| IFileStore | `nop-biz-file-core/.../IFileStore.java` |
| DaoResourceFileStore | `nop-file-dao/.../DaoResourceFileStore.java` |
| NopFileStoreBizModel | `nop-biz-file-core/.../NopFileStoreBizModel.java` |
| SpringFileService | `nop-spring-file/.../SpringFileService.java` |
| OrmFileComponent | `nop-orm/.../OrmFileComponent.java` |
| OrmFileListComponent | `nop-orm/.../OrmFileListComponent.java` |
| FileStatusBean | `nop-api-core/.../FileStatusBean.java` |
| XuiViewAnalyzer | `nop-ui/.../XuiViewAnalyzer.java` |
| FileComponentSupport | `nop-orm/.../xlib/orm-gen.xlib` |
| OssFileServiceClient | `nop-integration-oss/.../OssFileServiceClient.java` |

## 相关文档

- `../reusable-modules-overview.md`
- `../02-core-guides/service-layer.md`
- `../02-core-guides/ioc-and-config.md`
