# Upload/Download Extensions for the GraphQL Engine

The standard GraphQL engine only supports JSON I/O. To support file upload and download, NopGraphQL adds several extension conventions at the interface layer.

1. Upload information is converted into an UploadRequestBean object; inside the GraphQL engine you only need to program against UploadRequestBean. This is equivalent to augmenting the JSON serialization protocol with an automatic serialization mechanism for uploaded files. By default, the /f/upload endpoint will parse the uploaded file and invoke the GraphQL engine automatically.
2. The GraphQL engine can return a WebContentBean to represent a downloadable resource file. When the web framework calls the GraphQL engine and sees that the result is a WebContentBean, it will automatically read the Resource object from it and set headers such as Content-Type and Content-Disposition. By default, the two invocation forms /p/{bizObjName}\_\_{bizAction} and /f/download/{fileId} will automatically recognize WebContentBean

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

NopFileStoreBizModel only programs against POJO objects and does not need any knowledge of a particular web framework, so we can adapt it to different web frameworks. For example, with SpringMVC,

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

## Module Dependencies

After bringing in the nop-quarkus-web-orm-starter or nop-spring-web-orm-starter dependency, the following are pulled in automatically

* nop-file-dao: contains the implementation of the NopFileStoreBizModel service for file upload and download
* nop-file-spring or nop-file-quarkus: provides REST services that handle the /f/upload and /f/download endpoints

You need to include the nop-integration-oss module when using OSS cloud storage support

* nop-integration-oss: includes Amazon S3 object storage support; Alibaba Cloud OSS, Tencent Cloud COS, Qiniu Cloud, JD Cloud, and Minio all support this interface standard.

nop-integration-oss is currently optional; include it explicitly if you use cloud storage for attachments.

## Entity Fields Supporting Attachment Types

NopORM does not provide built-in support for attachment fields. At the application layer, we use a field-level abstraction OrmFileComponent to integrate file storage with database storage.

1. Store the file download link in the attachment field
2. Insert a NopFileRecord into the database to persist metadata such as attachment size, filename, and hash, while maintaining the association between fileId and the entity. When downloading, you can verify whether the requester has access to the entity.
3. Persist the actual binary file data via the IFileStore interface. The Nop platform has built-in support for local file storage and Amazon S3 object storage, compatible with S3 services like Minio, Qiniu, Tencent Cloud, and Alibaba Cloud.

## Excel Data Model

In the Excel data model, set the field’s domain to stdDomain=file to indicate it stores the link to the uploaded file.

![](file-domain.png)
![](field-domain.png)

When generating the orm.xml model from the Excel model, a stdDomain configuration will be produced for the field.

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

During compilation, the `x:post-extends` section invokes the tag function `<orm-gen:FileComponentSupport/>`, which generates a corresponding OrmFileComponent property for each file link field.

## OrmFileComponent: Binding Files to Entities

Uploading a file stores the binary data in IFileStore and inserts a NopFileRecord into the database to hold descriptive info such as filename and size.

When an entity is updated or deleted, the onEntityFlush and onEntityDelete callbacks on the IOrmComponent interface are triggered. In these callbacks, the bizObjName and bizObjId properties on the NopFileRecord are updated.

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

An important design here is that, at the entity level, we record whether an attachment field has been modified and its previous value. Without such historical information, we cannot determine how to synchronize file storage with entity fields at the individual field level and would have to handle it at the whole-entity processing level.

## Backend Reading
Files uploaded via `/f/upload` can be read directly on the backend

```javascript
 IOrmEntityFileStore fileStore = ...;
String fileId = fileStore.decodeFileId(importFilePath);
// Always process the uploaded temporary file
String objId = FileConstants.TEMP_BIZ_OBJ_ID;
IResource resource = fileStore.getFileResource(fileId, getBizObjName(), objId, NopRuleConstants.PROP_IMPORT_FILE);

```
You can read it through the IOrmEntityFileStore interface.

If the attachment field is implemented using `domain=file`, then

## Front-End Controls

In the control.xlib tag library, the stdDomain setting will automatically select the corresponding edit and view controls for the field.

The `<edit-file>` control uploads files to the /f/upload endpoint by default, and returns data in the following format

```
{
  status:0,
  data:{
     value: "文件下载链接"
  }
}
```

The download link format is /f/download/{fileId}

You can configure the following attributes on the prop node in the meta:

* ui:maxUploadSize controls the maximum upload size
* ui:uploadUrl customizes the upload endpoint
* ui:accept controls allowed file suffixes, e.g., .txt,.md allows only txt and Markdown files

You can also set upload control properties via global configuration

* nop.file.upload-url globally specified upload endpoint; defaults to /f/upload
* nop.file.upload.max-size global maximum upload size. The ui:maxUploadSize specified on each prop cannot exceed this value; the effective value is min(prop.uploadFileSize, global.uploadMaxSize)

## File Copying
IOrmEntityFileStore provides a copyFile function that duplicates a NopFileRecord for the specified fileId, allowing multiple attachment fields to reuse the same stored file.

When deleting a file, call IOrmEntityFileStore's detachFile function.

## Integrating Minio

Many distributed storage systems are compatible with Amazon's S3 protocol, such as Alibaba Cloud OSS, Tencent Cloud COS, Qiniu, JD Cloud, and Minio.

Use the following configuration to enable Minio support. Note: When using OSS cloud storage support, include the nop-integration-oss module first.

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

* When nop.file.store-impl is set to oss, object storage is used to save files; otherwise, the local filesystem is used, with files stored under /nop/file
* Alibaba Cloud requires pathStyleAccess to be set to false

Note: The configuration key is nop.integration.oss.enabled, not nop.file.integration.oss.enabled

## Front-End Display
If the Excel data model sets `domain=file` or `domain=file-list`, XMeta will automatically generate the corresponding FileStatus property. For example, the avatar field in NopAuthUser generates `avatarComponentFileStatus`.

FileStatus returns information such as the file name and size.

The `<view-file>` in `control.xlib` is used to display download links on the front end; it uses information from FileStatus to obtain the file name, etc.

## File Sharing
IOrmEntityFileStore provides a copyFile function that duplicates a NopFileRecord for the specified fileId, allowing multiple attachment fields to reuse the same stored file.

```javascript
    String copyFile(String fileId, String newBizObjName, String newObjId, String newFieldName);
```

* copyFile returns a new fileId
* The NopFileRecord has an originFileId field; records copied from the same NopFileRecord share the same originFileId.
* When detachFile is called, it checks whether the current record is the last one sharing the originFileId. If so, it also deletes the file from storage; otherwise, it only deletes the corresponding NopFileRecord.

## Allow Anonymous File Download

1. Use `IOrmEntityFileStore.changePublic(fileId, true)` to make the file publicly accessible
2. Set `nop.auth.download-file-public=true`, or customize nopAuthFilterConfig in `auth-service.beans.xml`, to allow public access to `/f/download/*`

## File Download
The approach above stores uploaded files uniformly in IFileStore (by default DaoResourceFileStore, using the nop_file_record table to record file storage locations). However, if you only need file download functionality, regardless of where the file is stored, you don't have to use the `/f/download/` endpoint; you can implement it directly in your own business BizModel.

```java
@BizModel("ReportDemo")
public class ReportDemoBizModel{
  @BizQuery
  public WebContentBean download(@Name("reportName") String reportName, @Name("renderType") String renderType){
    File tempFile = ...;

    // If a temporary file is used for download, clean it up automatically
    GlobalExecutors.globalTimer().schedule(() -> {
      tempFile.delete();
      return null;
    }, 30, TimeUnit.MINUTES);

     return new WebContentBean("application/octet-stream",
                    tempFile, fileName);
  }
}
```

The Nop platform's download feature does not use a `response.write` call; instead it returns a WebContentBean object that includes the resource file and download filename, with different external frameworks adapting the download logic.

On the front end, download via a link like `/p/ReportDemo__download`. The difference between a `/p/` call and a typical `/r/` call is that `/p/` returns the result directly, rather than wrapping the BizModel's return value in an ApiResponse object.

## Configuration Variables

* nop.file.store-dir
  the directory used when storing uploaded files on the local filesystem; defaults to /nop/file
* nop.file.store-impl
  set to oss to enable distributed (object) storage; otherwise local storage is used
* nop.file.upload-url globally specified upload endpoint; defaults to /f/upload
* nop.file.upload.max-size global upload size limit. The ui:maxUploadSize specified on each prop cannot exceed this value; the effective value is min(prop.uploadFileSize, global.uploadMaxSize)

<!-- SOURCE_MD5:27380e40533ef531353124da3aeead89-->
