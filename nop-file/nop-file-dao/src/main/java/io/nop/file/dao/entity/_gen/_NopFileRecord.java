package io.nop.file.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.file.dao.entity.NopFileRecord;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  文件记录: nop_file_record
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _NopFileRecord extends DynamicOrmEntity{
    
    /* 文件ID: FILE_ID VARCHAR */
    public static final String PROP_NAME_fileId = "fileId";
    public static final int PROP_ID_fileId = 1;
    
    /* 文件名: FILE_NAME VARCHAR */
    public static final String PROP_NAME_fileName = "fileName";
    public static final int PROP_ID_fileName = 2;
    
    /* 文件路径: FILE_PATH VARCHAR */
    public static final String PROP_NAME_filePath = "filePath";
    public static final int PROP_ID_filePath = 3;
    
    /* 扩展名: FILE_EXT VARCHAR */
    public static final String PROP_NAME_fileExt = "fileExt";
    public static final int PROP_ID_fileExt = 4;
    
    /* 内容类型: MIME_TYPE VARCHAR */
    public static final String PROP_NAME_mimeType = "mimeType";
    public static final int PROP_ID_mimeType = 5;
    
    /* 文件长度: FILE_LENGTH BIGINT */
    public static final String PROP_NAME_fileLength = "fileLength";
    public static final int PROP_ID_fileLength = 6;
    
    /* 文件修改时间: FILE_LAST_MODIFIED TIMESTAMP */
    public static final String PROP_NAME_fileLastModified = "fileLastModified";
    public static final int PROP_ID_fileLastModified = 7;
    
    /* 对象名: BIZ_OBJ_NAME VARCHAR */
    public static final String PROP_NAME_bizObjName = "bizObjName";
    public static final int PROP_ID_bizObjName = 8;
    
    /* 对象ID: BIZ_OBJ_ID VARCHAR */
    public static final String PROP_NAME_bizObjId = "bizObjId";
    public static final int PROP_ID_bizObjId = 9;
    
    /* 字段名: FIELD_NAME VARCHAR */
    public static final String PROP_NAME_fieldName = "fieldName";
    public static final int PROP_ID_fieldName = 10;
    
    /* 文件摘要: FILE_HASH VARCHAR */
    public static final String PROP_NAME_fileHash = "fileHash";
    public static final int PROP_ID_fileHash = 11;
    
    /* 删除标识: DEL_FLAG TINYINT */
    public static final String PROP_NAME_delFlag = "delFlag";
    public static final int PROP_ID_delFlag = 12;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 13;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_fileId);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_fileId};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_fileId] = PROP_NAME_fileId;
          PROP_NAME_TO_ID.put(PROP_NAME_fileId, PROP_ID_fileId);
      
          PROP_ID_TO_NAME[PROP_ID_fileName] = PROP_NAME_fileName;
          PROP_NAME_TO_ID.put(PROP_NAME_fileName, PROP_ID_fileName);
      
          PROP_ID_TO_NAME[PROP_ID_filePath] = PROP_NAME_filePath;
          PROP_NAME_TO_ID.put(PROP_NAME_filePath, PROP_ID_filePath);
      
          PROP_ID_TO_NAME[PROP_ID_fileExt] = PROP_NAME_fileExt;
          PROP_NAME_TO_ID.put(PROP_NAME_fileExt, PROP_ID_fileExt);
      
          PROP_ID_TO_NAME[PROP_ID_mimeType] = PROP_NAME_mimeType;
          PROP_NAME_TO_ID.put(PROP_NAME_mimeType, PROP_ID_mimeType);
      
          PROP_ID_TO_NAME[PROP_ID_fileLength] = PROP_NAME_fileLength;
          PROP_NAME_TO_ID.put(PROP_NAME_fileLength, PROP_ID_fileLength);
      
          PROP_ID_TO_NAME[PROP_ID_fileLastModified] = PROP_NAME_fileLastModified;
          PROP_NAME_TO_ID.put(PROP_NAME_fileLastModified, PROP_ID_fileLastModified);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjName] = PROP_NAME_bizObjName;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjName, PROP_ID_bizObjName);
      
          PROP_ID_TO_NAME[PROP_ID_bizObjId] = PROP_NAME_bizObjId;
          PROP_NAME_TO_ID.put(PROP_NAME_bizObjId, PROP_ID_bizObjId);
      
          PROP_ID_TO_NAME[PROP_ID_fieldName] = PROP_NAME_fieldName;
          PROP_NAME_TO_ID.put(PROP_NAME_fieldName, PROP_ID_fieldName);
      
          PROP_ID_TO_NAME[PROP_ID_fileHash] = PROP_NAME_fileHash;
          PROP_NAME_TO_ID.put(PROP_NAME_fileHash, PROP_ID_fileHash);
      
          PROP_ID_TO_NAME[PROP_ID_delFlag] = PROP_NAME_delFlag;
          PROP_NAME_TO_ID.put(PROP_NAME_delFlag, PROP_ID_delFlag);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 文件ID: FILE_ID */
    private java.lang.String _fileId;
    
    /* 文件名: FILE_NAME */
    private java.lang.String _fileName;
    
    /* 文件路径: FILE_PATH */
    private java.lang.String _filePath;
    
    /* 扩展名: FILE_EXT */
    private java.lang.String _fileExt;
    
    /* 内容类型: MIME_TYPE */
    private java.lang.String _mimeType;
    
    /* 文件长度: FILE_LENGTH */
    private java.lang.Long _fileLength;
    
    /* 文件修改时间: FILE_LAST_MODIFIED */
    private java.sql.Timestamp _fileLastModified;
    
    /* 对象名: BIZ_OBJ_NAME */
    private java.lang.String _bizObjName;
    
    /* 对象ID: BIZ_OBJ_ID */
    private java.lang.String _bizObjId;
    
    /* 字段名: FIELD_NAME */
    private java.lang.String _fieldName;
    
    /* 文件摘要: FILE_HASH */
    private java.lang.String _fileHash;
    
    /* 删除标识: DEL_FLAG */
    private java.lang.Byte _delFlag;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopFileRecord(){
        // for debug
    }

    protected NopFileRecord newInstance(){
        NopFileRecord entity = new NopFileRecord();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public NopFileRecord cloneInstance() {
        NopFileRecord entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.orm_propValue(propId,value);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.file.dao.entity.NopFileRecord";
    }

    @Override
    public int orm_propIdBound(){
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getPropIdBound();
      return _PROP_ID_BOUND;
    }

    @Override
    public Object orm_id() {
    
        return buildSimpleId(PROP_ID_fileId);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_fileId;
          
    }

    @Override
    public String orm_propName(int propId) {
        if(propId >= PROP_ID_TO_NAME.length)
            return super.orm_propName(propId);
        String propName = PROP_ID_TO_NAME[propId];
        if(propName == null)
           return super.orm_propName(propId);
        return propName;
    }

    @Override
    public int orm_propId(String propName) {
        Integer propId = PROP_NAME_TO_ID.get(propName);
        if(propId == null)
            return super.orm_propId(propName);
        return propId;
    }

    @Override
    public Object orm_propValue(int propId) {
        switch(propId){
        
            case PROP_ID_fileId:
               return getFileId();
        
            case PROP_ID_fileName:
               return getFileName();
        
            case PROP_ID_filePath:
               return getFilePath();
        
            case PROP_ID_fileExt:
               return getFileExt();
        
            case PROP_ID_mimeType:
               return getMimeType();
        
            case PROP_ID_fileLength:
               return getFileLength();
        
            case PROP_ID_fileLastModified:
               return getFileLastModified();
        
            case PROP_ID_bizObjName:
               return getBizObjName();
        
            case PROP_ID_bizObjId:
               return getBizObjId();
        
            case PROP_ID_fieldName:
               return getFieldName();
        
            case PROP_ID_fileHash:
               return getFileHash();
        
            case PROP_ID_delFlag:
               return getDelFlag();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_fileId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileId));
               }
               setFileId(typedValue);
               break;
            }
        
            case PROP_ID_fileName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileName));
               }
               setFileName(typedValue);
               break;
            }
        
            case PROP_ID_filePath:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_filePath));
               }
               setFilePath(typedValue);
               break;
            }
        
            case PROP_ID_fileExt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileExt));
               }
               setFileExt(typedValue);
               break;
            }
        
            case PROP_ID_mimeType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_mimeType));
               }
               setMimeType(typedValue);
               break;
            }
        
            case PROP_ID_fileLength:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_fileLength));
               }
               setFileLength(typedValue);
               break;
            }
        
            case PROP_ID_fileLastModified:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_fileLastModified));
               }
               setFileLastModified(typedValue);
               break;
            }
        
            case PROP_ID_bizObjName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjName));
               }
               setBizObjName(typedValue);
               break;
            }
        
            case PROP_ID_bizObjId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_bizObjId));
               }
               setBizObjId(typedValue);
               break;
            }
        
            case PROP_ID_fieldName:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fieldName));
               }
               setFieldName(typedValue);
               break;
            }
        
            case PROP_ID_fileHash:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileHash));
               }
               setFileHash(typedValue);
               break;
            }
        
            case PROP_ID_delFlag:{
               java.lang.Byte typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toByte(value,
                       err-> newTypeConversionError(PROP_NAME_delFlag));
               }
               setDelFlag(typedValue);
               break;
            }
        
            case PROP_ID_createdBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_createdBy));
               }
               setCreatedBy(typedValue);
               break;
            }
        
            case PROP_ID_createTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_createTime));
               }
               setCreateTime(typedValue);
               break;
            }
        
            case PROP_ID_remark:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_remark));
               }
               setRemark(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_fileId:{
               onInitProp(propId);
               this._fileId = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_fileName:{
               onInitProp(propId);
               this._fileName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_filePath:{
               onInitProp(propId);
               this._filePath = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileExt:{
               onInitProp(propId);
               this._fileExt = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_mimeType:{
               onInitProp(propId);
               this._mimeType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileLength:{
               onInitProp(propId);
               this._fileLength = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_fileLastModified:{
               onInitProp(propId);
               this._fileLastModified = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_bizObjName:{
               onInitProp(propId);
               this._bizObjName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_bizObjId:{
               onInitProp(propId);
               this._bizObjId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fieldName:{
               onInitProp(propId);
               this._fieldName = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileHash:{
               onInitProp(propId);
               this._fileHash = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_delFlag:{
               onInitProp(propId);
               this._delFlag = (java.lang.Byte)value;
               
               break;
            }
        
            case PROP_ID_createdBy:{
               onInitProp(propId);
               this._createdBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_createTime:{
               onInitProp(propId);
               this._createTime = (java.sql.Timestamp)value;
               
               break;
            }
        
            case PROP_ID_remark:{
               onInitProp(propId);
               this._remark = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * 文件ID: FILE_ID
     */
    public java.lang.String getFileId(){
         onPropGet(PROP_ID_fileId);
         return _fileId;
    }

    /**
     * 文件ID: FILE_ID
     */
    public void setFileId(java.lang.String value){
        if(onPropSet(PROP_ID_fileId,value)){
            this._fileId = value;
            internalClearRefs(PROP_ID_fileId);
            orm_id();
        }
    }
    
    /**
     * 文件名: FILE_NAME
     */
    public java.lang.String getFileName(){
         onPropGet(PROP_ID_fileName);
         return _fileName;
    }

    /**
     * 文件名: FILE_NAME
     */
    public void setFileName(java.lang.String value){
        if(onPropSet(PROP_ID_fileName,value)){
            this._fileName = value;
            internalClearRefs(PROP_ID_fileName);
            
        }
    }
    
    /**
     * 文件路径: FILE_PATH
     */
    public java.lang.String getFilePath(){
         onPropGet(PROP_ID_filePath);
         return _filePath;
    }

    /**
     * 文件路径: FILE_PATH
     */
    public void setFilePath(java.lang.String value){
        if(onPropSet(PROP_ID_filePath,value)){
            this._filePath = value;
            internalClearRefs(PROP_ID_filePath);
            
        }
    }
    
    /**
     * 扩展名: FILE_EXT
     */
    public java.lang.String getFileExt(){
         onPropGet(PROP_ID_fileExt);
         return _fileExt;
    }

    /**
     * 扩展名: FILE_EXT
     */
    public void setFileExt(java.lang.String value){
        if(onPropSet(PROP_ID_fileExt,value)){
            this._fileExt = value;
            internalClearRefs(PROP_ID_fileExt);
            
        }
    }
    
    /**
     * 内容类型: MIME_TYPE
     */
    public java.lang.String getMimeType(){
         onPropGet(PROP_ID_mimeType);
         return _mimeType;
    }

    /**
     * 内容类型: MIME_TYPE
     */
    public void setMimeType(java.lang.String value){
        if(onPropSet(PROP_ID_mimeType,value)){
            this._mimeType = value;
            internalClearRefs(PROP_ID_mimeType);
            
        }
    }
    
    /**
     * 文件长度: FILE_LENGTH
     */
    public java.lang.Long getFileLength(){
         onPropGet(PROP_ID_fileLength);
         return _fileLength;
    }

    /**
     * 文件长度: FILE_LENGTH
     */
    public void setFileLength(java.lang.Long value){
        if(onPropSet(PROP_ID_fileLength,value)){
            this._fileLength = value;
            internalClearRefs(PROP_ID_fileLength);
            
        }
    }
    
    /**
     * 文件修改时间: FILE_LAST_MODIFIED
     */
    public java.sql.Timestamp getFileLastModified(){
         onPropGet(PROP_ID_fileLastModified);
         return _fileLastModified;
    }

    /**
     * 文件修改时间: FILE_LAST_MODIFIED
     */
    public void setFileLastModified(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_fileLastModified,value)){
            this._fileLastModified = value;
            internalClearRefs(PROP_ID_fileLastModified);
            
        }
    }
    
    /**
     * 对象名: BIZ_OBJ_NAME
     */
    public java.lang.String getBizObjName(){
         onPropGet(PROP_ID_bizObjName);
         return _bizObjName;
    }

    /**
     * 对象名: BIZ_OBJ_NAME
     */
    public void setBizObjName(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjName,value)){
            this._bizObjName = value;
            internalClearRefs(PROP_ID_bizObjName);
            
        }
    }
    
    /**
     * 对象ID: BIZ_OBJ_ID
     */
    public java.lang.String getBizObjId(){
         onPropGet(PROP_ID_bizObjId);
         return _bizObjId;
    }

    /**
     * 对象ID: BIZ_OBJ_ID
     */
    public void setBizObjId(java.lang.String value){
        if(onPropSet(PROP_ID_bizObjId,value)){
            this._bizObjId = value;
            internalClearRefs(PROP_ID_bizObjId);
            
        }
    }
    
    /**
     * 字段名: FIELD_NAME
     */
    public java.lang.String getFieldName(){
         onPropGet(PROP_ID_fieldName);
         return _fieldName;
    }

    /**
     * 字段名: FIELD_NAME
     */
    public void setFieldName(java.lang.String value){
        if(onPropSet(PROP_ID_fieldName,value)){
            this._fieldName = value;
            internalClearRefs(PROP_ID_fieldName);
            
        }
    }
    
    /**
     * 文件摘要: FILE_HASH
     */
    public java.lang.String getFileHash(){
         onPropGet(PROP_ID_fileHash);
         return _fileHash;
    }

    /**
     * 文件摘要: FILE_HASH
     */
    public void setFileHash(java.lang.String value){
        if(onPropSet(PROP_ID_fileHash,value)){
            this._fileHash = value;
            internalClearRefs(PROP_ID_fileHash);
            
        }
    }
    
    /**
     * 删除标识: DEL_FLAG
     */
    public java.lang.Byte getDelFlag(){
         onPropGet(PROP_ID_delFlag);
         return _delFlag;
    }

    /**
     * 删除标识: DEL_FLAG
     */
    public void setDelFlag(java.lang.Byte value){
        if(onPropSet(PROP_ID_delFlag,value)){
            this._delFlag = value;
            internalClearRefs(PROP_ID_delFlag);
            
        }
    }
    
    /**
     * 创建人: CREATED_BY
     */
    public java.lang.String getCreatedBy(){
         onPropGet(PROP_ID_createdBy);
         return _createdBy;
    }

    /**
     * 创建人: CREATED_BY
     */
    public void setCreatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_createdBy,value)){
            this._createdBy = value;
            internalClearRefs(PROP_ID_createdBy);
            
        }
    }
    
    /**
     * 创建时间: CREATE_TIME
     */
    public java.sql.Timestamp getCreateTime(){
         onPropGet(PROP_ID_createTime);
         return _createTime;
    }

    /**
     * 创建时间: CREATE_TIME
     */
    public void setCreateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_createTime,value)){
            this._createTime = value;
            internalClearRefs(PROP_ID_createTime);
            
        }
    }
    
    /**
     * 备注: REMARK
     */
    public java.lang.String getRemark(){
         onPropGet(PROP_ID_remark);
         return _remark;
    }

    /**
     * 备注: REMARK
     */
    public void setRemark(java.lang.String value){
        if(onPropSet(PROP_ID_remark,value)){
            this._remark = value;
            internalClearRefs(PROP_ID_remark);
            
        }
    }
    
}
// resume CPD analysis - CPD-ON
