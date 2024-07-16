# GraphQL引擎的上传下载扩展

标准的GraphQL引擎只支持JSON格式的输入输出。为了支持文件上传下载，NopGraphQL在接口层增加了一些扩展约定。

1. 文件上传信息被转换为UploadRequestBean对象，在GraphQL引擎内部只要针对UploadRequestBean进行编程即可。相当于是在JSON序列化协议的基础上增加一个自动的针对上传文件的序列化机制。
   目前缺省情况下/f/upload这个端点会自动解析上传文件并调用GraphQL引擎。
2. GraphQL引擎可以返回WebContentBean来表示下载资源文件。Web框架调用GraphQL引擎发现返回结果是WebContentBean之后，会自动从中读取到Resource对象，并设置Content-Type和Content-Disposition等header配置。
   目前缺省情况下/p/{bizObjName}\_\_{bizAction}以及/f/download/{fileId}这两种调用形式会自动识别WebContentBean

```java

@BizModel("NopFileStore")
public class NopFileStoreBizModel {
    ...
    @BizMutation
    public UploadResponseBean upload(@RequestBean UploadRequestBean record, IServiceContext context) {
        checkMaxSize(record.getLength());
        checkFileExt(record.getFileExt());
        checkBizObjName(record.getBizObjName());

        String fileId = fileStore.saveFile(record, maxFileSize);

        UploadResponseBean ret = new UploadResponseBean();
        ret.setValue(fileStore.getFileLink(fileId));
        ret.setFilename(record.getFileName());
        return ret;
    }

    @BizQuery
    public WebContentBean download(@Name("fileId") String fileId,
                                   @Name("contentType") String contentType) {
        IFileRecord record = fileStore.getFile(fileId);
        if (StringHelper.isEmpty(contentType))
            contentType = MediaType.APPLICATION_OCTET_STREAM;

        return new WebContentBean(contentType, record.getResource(), record.getFileName());
    }

    protected IFileRecord loadFileRecord(String fileId, IServiceContext ctx) {
        IFileRecord record = fileStore.getFile(fileId);
        if (bizAuthChecker != null) {
            bizAuthChecker.checkAuth(record.getBizObjName(), record.getBizObjId(), record.getFieldName(), ctx);
        }
        return record;
    }
}
```

NopFileStoreBizModel只是针对POJO对象进行编程，它完全不需要具有任何关于特定Web框架的知识，因此我们可以将它适配到不同的Web框架。例如，对于SpringMVC，

```java
@RestController
public class SpringFileService extends AbstractGraphQLFileService {

    @PostMapping("/f/upload")
    public CompletionStage<ResponseEntity<Object>> upload(MultipartFile file, HttpServletRequest request) {
        String locale = ContextProvider.currentLocale();
        CompletionStage<ApiResponse<?>> res;
        try {
            InputStream is = file.getInputStream();
            String fileName = StringHelper.fileFullName(file.getOriginalFilename());
            String mimeType = MediaTypeHelper.getMimeType(file.getContentType(), StringHelper.fileExt(fileName));
            UploadRequestBean input = new UploadRequestBean(is, fileName, file.getSize(), mimeType);
            input.setBizObjName(request.getParameter(FileConstants.PARAM_BIZ_OBJ_NAME));

            IGraphQLEngine graphQLEngine = getGraphQLEngine();

            IGraphQLExecutionContext ctx = graphQLEngine.newRpcContext(GraphQLOperationType.mutation,
                    "NopFileStore__upload", buildApiRequest(request,input));
            res = graphQLEngine.executeRpcAsync(ctx);
        } catch (IOException e) {
            res = FutureHelper.success(ErrorMessageManager.instance().buildResponse(locale, e));
        }
        return res.thenApply(response -> SpringWebHelper.buildResponse(response.getHttpStatus(), response));
    }

    protected <T> ApiRequest<T> buildApiRequest(HttpServletRequest req, T data) {
        ApiRequest<T> ret = new ApiRequest<>();
        Enumeration<String> it = req.getHeaderNames();
        while (it.hasMoreElements()) {
            String name = it.nextElement();
            name = name.toLowerCase(Locale.ENGLISH);
            if (shouldIgnoreHeader(name))
                continue;
            ret.setHeader(name, req.getHeader(name));
        }
        ret.setData(data);
        return ret;
    }
}
```

## 模块依赖

引入nop-quarkus-web-orm-starter或者nop-spring-web-orm-starter依赖后，会自动引入

* nop-file-dao： 包含文件上传下载服务NopFileStoreBizModel的实现
* nop-file-spring或者 nop-file-quarkus: 引入处理/f/upload和/f/download链接的REST服务

**使用oss云存储支持时需要引入nop-integration-oss模块**

* nop-integration-oss: 包含AmazonS3对象存储支持，阿里云OSS，腾讯云COS，七牛云，京东云，minio都支持这一接口标准。

nop-integration-oss目前是可选模块，使用云存储来保存附件时需要自行引入这个模块。

## 实体字段支持附件类型

NopORM并没有内置对于附件字段的支持，在应用层我们通过OrmFileComponent这种字段级别的抽象将文件存储与数据库存储结合在一起。

1. 附件字段中保存文件下载链接
2. 在数据库中插入NopFileRecord保存附件的大小、文件名、Hash值等元数据，同时保存fileId和实体之间的关联关系，下载文件时可以验证是否具有实体访问权限
3. 通过IFileStore接口保存具体的二进制文件数据。Nop平台中内置了本地文件存储以及AmazonS3对象存储支持，支持Minio、七牛云、腾讯云、阿里云等兼容S3的云存储。

## Excel数据模型

在Excel数据模型中为字段的domain指定stdDomain=file，表示它保存上传文件的链接地址。

![](file-domain.png)
![](field-domain.png)

根据Excel模型生成orm.xml模型文件中会为字段生成stdDomain配置。

```xml

<orm>
    <x:post-extends x:override="replace">
        <orm-gen:DefaultPostExtends xpl:lib="/nop/orm/xlib/orm-gen.xlib"/>
    </x:post-extends>

    <entites>
        <entity className="io.nop.auth.dao.entity.NopAuthUser">
            <columns>
                <column code="AVATAR" displayName="头像" domain="image" name="avatar" precision="100" propId="9"
                        stdDataType="string" stdDomain="file" stdSqlType="VARCHAR" i18n-en:displayName="Avatar"
                        ui:show="X"/>
            </columns>
        </entity>
    </entites>
</orm>
```

在编译期执行的`x:post-extends`段会执行标签函数 `<orm-gen:FileComponentSupport/>`，它会为每个文件链接字段生成一个对应的OrmFileComponent属性。

## OrmFileComponent实现文件与实体的绑定

上传文件会将具体文件数据保存到IFileStore中，同时会在数据库中插入NopFileRecord记录，用于保存文件名、文件大小等描述信息。

当实体更新或者删除的时候，会触发IOrmComponent接口上的onEntityFlush和onEntityDelete回调函数，在回调函数中将更新NopFileRecord对象上的bizObjName,bizObjId属性。

```java

public class OrmFileComponent extends AbstractOrmComponent {
    public static final String PROP_NAME_filePath = "filePath";

    public String getFilePath() {
        return ConvertHelper.toString(internalGetPropValue(PROP_NAME_filePath));
    }

    public void setFilePath(String value) {
        internalSetPropValue(PROP_NAME_filePath, value);
    }

    @Override
    public void onEntityFlush() {
        IOrmEntity entity = orm_owner();
        int propId = getColPropId(PROP_NAME_filePath);
        if (entity.orm_state().isUnsaved() || entity.orm_propDirty(propId)) {
            IBeanProvider beanProvider = entity.orm_enhancer().getBeanProvider();
            IOrmEntityFileStore fileStore = (IOrmEntityFileStore) beanProvider.getBean(OrmConstants.BEAN_ORM_ENTITY_FILE_STORE);
            String oldValue = (String) entity.orm_propOldValue(propId);

            String fileId = fileStore.decodeFileId(getFilePath());
            String propName = entity.orm_propName(propId);

            String bizObjName = getBizObjName();

            if (!StringHelper.isEmpty(oldValue)) {
                String oldFileId = fileStore.decodeFileId(oldValue);
                if (!StringHelper.isEmpty(oldFileId)) {
                    fileStore.detachFile(oldFileId, bizObjName, entity.orm_idString(), propName);
                }
            }

            if (!StringHelper.isEmpty(fileId)) {
                fileStore.attachFile(fileId, bizObjName, entity.orm_idString(), propName);
            }
        }
    }

}
```

这里很重要的一个设计就是实体层面上记录了附件字段是否已经被修改，以及修改前的值。可以想见，如果没有这种历史记录信息，我们就无法在单个字段层面确定如何实现文件存储与实体字段的同步，
而必须上升到整个实体的处理函数中进行。

## 后台读取
通过`/f/upload`上传的文件在后台可以直接读取到

```javascript
 IOrmEntityFileStore fileStore = ...;
String fileId = fileStore.decodeFileId(importFilePath);
// 总是处理上传的临时文件
String objId = FileConstants.TEMP_BIZ_OBJ_ID;
IResource resource = fileStore.getFileResource(fileId, getBizObjName(), objId, NopRuleConstants.PROP_IMPORT_FILE);

```
通过IOrmEntityFileStore接口可以读取。

如果是使用`domain=file`实现的附件字段，则

## 前端控件

在control.xlib标签库中，根据stdDomain设置会自动为字段选择对应的编辑和显示控件。

`<edit-file>`控件缺省会上传文件到/f/upload这个链接，返回的数据格式为

```
{
  status:0,
  data:{
     value: "文件下载链接"
  }
}
```

下载链接的格式为 /f/download/{fileId}

在meta的prop节点上，可以配置以下属性:

* ui:maxUploadSize 控制上传文件的最大大小
* ui:uploadUrl 定制上传文件端点
* ui:accept 可以控制允许的文件后缀，例如 .txt,.md表示只允许上传txt和markddown文件

另外也可以通过全局配置来设置上传控件属性

* nop.file.upload-url 全局指定的上传文件端点，缺省为/f/upload
* nop.file.upload.max-size 全局指定的上传文件大小限制。每个prop上指定的ui:maxUploadSize不能超过这个值，实际起作用的是min(prop.uploadFileSize,global.uploadMaxSize)

## 集成Minio

很多分布式存储都兼容Amazon的S3存储协议，例如阿里云OSS，腾讯云COS，七牛云，京东云，Minio等。

使用如下配置启用Minio支持。**注意，使用oss云存储支持时需要先引入nop-integration-oss模块**。

```yaml
nop:
  file:
    store-impl: oss

  integration:
    oss:
      enabled: true
      endpoint: http://localhost:9000
      #default-bucket-name: nop-file
      access-key: xxx
      secret-key: yyy
      #path-style-access: false
```

* nop.file.store-impl指定为oss时会使用对象存储来保存文件，否则会使用本地文件系统，存放在/nop/file目录下
* 阿里云要求pathStyleAccess必须设置为false

**注意: 配置项是nop.integration.oss.enabled，而不是nop.file.integration.oss.enabled**

## 配置变量

* nop.file.store-dir
  使用本地文件系统存储上传文件时使用的目录，缺省为/nop/file
* nop.file.store-impl
  如果设置为oss则表示启用分布式存储，否则使用本地存储
* nop.file.upload-url 全局指定的上传文件端点，缺省为/f/upload
* nop.file.upload.max-size 全局指定的上传文件大小限制。每个prop上指定的ui:maxUploadSize不能超过这个值，实际起作用的是min(prop.uploadFileSize,global.uploadMaxSize)
