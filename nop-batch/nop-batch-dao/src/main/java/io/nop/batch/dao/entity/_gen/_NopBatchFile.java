package io.nop.batch.dao.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import io.nop.batch.dao.entity.NopBatchFile;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  批处理文件: nop_batch_file
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116"})
public class _NopBatchFile extends DynamicOrmEntity{
    
    /* 主键: SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* 文件名: FILE_NAME VARCHAR */
    public static final String PROP_NAME_fileName = "fileName";
    public static final int PROP_ID_fileName = 2;
    
    /* 文件路径: FILE_PATH VARCHAR */
    public static final String PROP_NAME_filePath = "filePath";
    public static final int PROP_ID_filePath = 3;
    
    /* 文件长度: FILE_LENGTH BIGINT */
    public static final String PROP_NAME_fileLength = "fileLength";
    public static final int PROP_ID_fileLength = 4;
    
    /* 文件分类: FILE_CATEGORY VARCHAR */
    public static final String PROP_NAME_fileCategory = "fileCategory";
    public static final int PROP_ID_fileCategory = 5;
    
    /* 文件来源: FILE_SOURCE VARCHAR */
    public static final String PROP_NAME_fileSource = "fileSource";
    public static final int PROP_ID_fileSource = 6;
    
    /* 当前处理任务: CURRENT_TASK_ID VARCHAR */
    public static final String PROP_NAME_currentTaskId = "currentTaskId";
    public static final int PROP_ID_currentTaskId = 7;
    
    /* 处理状态: PROCESS_STATE INTEGER */
    public static final String PROP_NAME_processState = "processState";
    public static final int PROP_ID_processState = 8;
    
    /* 文件接收时间: ACCEPT_DATE DATE */
    public static final String PROP_NAME_acceptDate = "acceptDate";
    public static final int PROP_ID_acceptDate = 9;
    
    /* 数据版本: VERSION BIGINT */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* 创建人: CREATED_BY VARCHAR */
    public static final String PROP_NAME_createdBy = "createdBy";
    public static final int PROP_ID_createdBy = 11;
    
    /* 创建时间: CREATE_TIME TIMESTAMP */
    public static final String PROP_NAME_createTime = "createTime";
    public static final int PROP_ID_createTime = 12;
    
    /* 修改人: UPDATED_BY VARCHAR */
    public static final String PROP_NAME_updatedBy = "updatedBy";
    public static final int PROP_ID_updatedBy = 13;
    
    /* 修改时间: UPDATE_TIME TIMESTAMP */
    public static final String PROP_NAME_updateTime = "updateTime";
    public static final int PROP_ID_updateTime = 14;
    
    /* 备注: REMARK VARCHAR */
    public static final String PROP_NAME_remark = "remark";
    public static final int PROP_ID_remark = 15;
    

    private static int _PROP_ID_BOUND = 16;

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_fileName] = PROP_NAME_fileName;
          PROP_NAME_TO_ID.put(PROP_NAME_fileName, PROP_ID_fileName);
      
          PROP_ID_TO_NAME[PROP_ID_filePath] = PROP_NAME_filePath;
          PROP_NAME_TO_ID.put(PROP_NAME_filePath, PROP_ID_filePath);
      
          PROP_ID_TO_NAME[PROP_ID_fileLength] = PROP_NAME_fileLength;
          PROP_NAME_TO_ID.put(PROP_NAME_fileLength, PROP_ID_fileLength);
      
          PROP_ID_TO_NAME[PROP_ID_fileCategory] = PROP_NAME_fileCategory;
          PROP_NAME_TO_ID.put(PROP_NAME_fileCategory, PROP_ID_fileCategory);
      
          PROP_ID_TO_NAME[PROP_ID_fileSource] = PROP_NAME_fileSource;
          PROP_NAME_TO_ID.put(PROP_NAME_fileSource, PROP_ID_fileSource);
      
          PROP_ID_TO_NAME[PROP_ID_currentTaskId] = PROP_NAME_currentTaskId;
          PROP_NAME_TO_ID.put(PROP_NAME_currentTaskId, PROP_ID_currentTaskId);
      
          PROP_ID_TO_NAME[PROP_ID_processState] = PROP_NAME_processState;
          PROP_NAME_TO_ID.put(PROP_NAME_processState, PROP_ID_processState);
      
          PROP_ID_TO_NAME[PROP_ID_acceptDate] = PROP_NAME_acceptDate;
          PROP_NAME_TO_ID.put(PROP_NAME_acceptDate, PROP_ID_acceptDate);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_createdBy] = PROP_NAME_createdBy;
          PROP_NAME_TO_ID.put(PROP_NAME_createdBy, PROP_ID_createdBy);
      
          PROP_ID_TO_NAME[PROP_ID_createTime] = PROP_NAME_createTime;
          PROP_NAME_TO_ID.put(PROP_NAME_createTime, PROP_ID_createTime);
      
          PROP_ID_TO_NAME[PROP_ID_updatedBy] = PROP_NAME_updatedBy;
          PROP_NAME_TO_ID.put(PROP_NAME_updatedBy, PROP_ID_updatedBy);
      
          PROP_ID_TO_NAME[PROP_ID_updateTime] = PROP_NAME_updateTime;
          PROP_NAME_TO_ID.put(PROP_NAME_updateTime, PROP_ID_updateTime);
      
          PROP_ID_TO_NAME[PROP_ID_remark] = PROP_NAME_remark;
          PROP_NAME_TO_ID.put(PROP_NAME_remark, PROP_ID_remark);
      
    }

    
    /* 主键: SID */
    private java.lang.String _sid;
    
    /* 文件名: FILE_NAME */
    private java.lang.String _fileName;
    
    /* 文件路径: FILE_PATH */
    private java.lang.String _filePath;
    
    /* 文件长度: FILE_LENGTH */
    private java.lang.Long _fileLength;
    
    /* 文件分类: FILE_CATEGORY */
    private java.lang.String _fileCategory;
    
    /* 文件来源: FILE_SOURCE */
    private java.lang.String _fileSource;
    
    /* 当前处理任务: CURRENT_TASK_ID */
    private java.lang.String _currentTaskId;
    
    /* 处理状态: PROCESS_STATE */
    private java.lang.Integer _processState;
    
    /* 文件接收时间: ACCEPT_DATE */
    private java.time.LocalDate _acceptDate;
    
    /* 数据版本: VERSION */
    private java.lang.Long _version;
    
    /* 创建人: CREATED_BY */
    private java.lang.String _createdBy;
    
    /* 创建时间: CREATE_TIME */
    private java.sql.Timestamp _createTime;
    
    /* 修改人: UPDATED_BY */
    private java.lang.String _updatedBy;
    
    /* 修改时间: UPDATE_TIME */
    private java.sql.Timestamp _updateTime;
    
    /* 备注: REMARK */
    private java.lang.String _remark;
    

    public _NopBatchFile(){
    }

    protected NopBatchFile newInstance(){
       return new NopBatchFile();
    }

    @Override
    public NopBatchFile cloneInstance() {
        NopBatchFile entity = newInstance();
        orm_forEachInitedProp((value, propId) -> {
            entity.onInitProp(propId);
        });
        return entity;
    }

    @Override
    public String orm_entityName() {
      // 如果存在实体模型对象，则以模型对象上的设置为准
      IEntityModel entityModel = orm_entityModel();
      if(entityModel != null)
          return entityModel.getName();
      return "io.nop.batch.dao.entity.NopBatchFile";
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
    
        return buildSimpleId(PROP_ID_sid);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_sid;
          
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
        
            case PROP_ID_sid:
               return getSid();
        
            case PROP_ID_fileName:
               return getFileName();
        
            case PROP_ID_filePath:
               return getFilePath();
        
            case PROP_ID_fileLength:
               return getFileLength();
        
            case PROP_ID_fileCategory:
               return getFileCategory();
        
            case PROP_ID_fileSource:
               return getFileSource();
        
            case PROP_ID_currentTaskId:
               return getCurrentTaskId();
        
            case PROP_ID_processState:
               return getProcessState();
        
            case PROP_ID_acceptDate:
               return getAcceptDate();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_createdBy:
               return getCreatedBy();
        
            case PROP_ID_createTime:
               return getCreateTime();
        
            case PROP_ID_updatedBy:
               return getUpdatedBy();
        
            case PROP_ID_updateTime:
               return getUpdateTime();
        
            case PROP_ID_remark:
               return getRemark();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sid:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
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
        
            case PROP_ID_fileLength:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_fileLength));
               }
               setFileLength(typedValue);
               break;
            }
        
            case PROP_ID_fileCategory:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileCategory));
               }
               setFileCategory(typedValue);
               break;
            }
        
            case PROP_ID_fileSource:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_fileSource));
               }
               setFileSource(typedValue);
               break;
            }
        
            case PROP_ID_currentTaskId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_currentTaskId));
               }
               setCurrentTaskId(typedValue);
               break;
            }
        
            case PROP_ID_processState:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_processState));
               }
               setProcessState(typedValue);
               break;
            }
        
            case PROP_ID_acceptDate:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_acceptDate));
               }
               setAcceptDate(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
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
        
            case PROP_ID_updatedBy:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_updatedBy));
               }
               setUpdatedBy(typedValue);
               break;
            }
        
            case PROP_ID_updateTime:{
               java.sql.Timestamp typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toTimestamp(value,
                       err-> newTypeConversionError(PROP_NAME_updateTime));
               }
               setUpdateTime(typedValue);
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
        
            case PROP_ID_sid:{
               onInitProp(propId);
               this._sid = (java.lang.String)value;
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
        
            case PROP_ID_fileLength:{
               onInitProp(propId);
               this._fileLength = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_fileCategory:{
               onInitProp(propId);
               this._fileCategory = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_fileSource:{
               onInitProp(propId);
               this._fileSource = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_currentTaskId:{
               onInitProp(propId);
               this._currentTaskId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_processState:{
               onInitProp(propId);
               this._processState = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_acceptDate:{
               onInitProp(propId);
               this._acceptDate = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Long)value;
               
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
        
            case PROP_ID_updatedBy:{
               onInitProp(propId);
               this._updatedBy = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_updateTime:{
               onInitProp(propId);
               this._updateTime = (java.sql.Timestamp)value;
               
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
     * 主键: SID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * 主键: SID
     */
    public void setSid(java.lang.String value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
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
     * 文件分类: FILE_CATEGORY
     */
    public java.lang.String getFileCategory(){
         onPropGet(PROP_ID_fileCategory);
         return _fileCategory;
    }

    /**
     * 文件分类: FILE_CATEGORY
     */
    public void setFileCategory(java.lang.String value){
        if(onPropSet(PROP_ID_fileCategory,value)){
            this._fileCategory = value;
            internalClearRefs(PROP_ID_fileCategory);
            
        }
    }
    
    /**
     * 文件来源: FILE_SOURCE
     */
    public java.lang.String getFileSource(){
         onPropGet(PROP_ID_fileSource);
         return _fileSource;
    }

    /**
     * 文件来源: FILE_SOURCE
     */
    public void setFileSource(java.lang.String value){
        if(onPropSet(PROP_ID_fileSource,value)){
            this._fileSource = value;
            internalClearRefs(PROP_ID_fileSource);
            
        }
    }
    
    /**
     * 当前处理任务: CURRENT_TASK_ID
     */
    public java.lang.String getCurrentTaskId(){
         onPropGet(PROP_ID_currentTaskId);
         return _currentTaskId;
    }

    /**
     * 当前处理任务: CURRENT_TASK_ID
     */
    public void setCurrentTaskId(java.lang.String value){
        if(onPropSet(PROP_ID_currentTaskId,value)){
            this._currentTaskId = value;
            internalClearRefs(PROP_ID_currentTaskId);
            
        }
    }
    
    /**
     * 处理状态: PROCESS_STATE
     */
    public java.lang.Integer getProcessState(){
         onPropGet(PROP_ID_processState);
         return _processState;
    }

    /**
     * 处理状态: PROCESS_STATE
     */
    public void setProcessState(java.lang.Integer value){
        if(onPropSet(PROP_ID_processState,value)){
            this._processState = value;
            internalClearRefs(PROP_ID_processState);
            
        }
    }
    
    /**
     * 文件接收时间: ACCEPT_DATE
     */
    public java.time.LocalDate getAcceptDate(){
         onPropGet(PROP_ID_acceptDate);
         return _acceptDate;
    }

    /**
     * 文件接收时间: ACCEPT_DATE
     */
    public void setAcceptDate(java.time.LocalDate value){
        if(onPropSet(PROP_ID_acceptDate,value)){
            this._acceptDate = value;
            internalClearRefs(PROP_ID_acceptDate);
            
        }
    }
    
    /**
     * 数据版本: VERSION
     */
    public java.lang.Long getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * 数据版本: VERSION
     */
    public void setVersion(java.lang.Long value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
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
     * 修改人: UPDATED_BY
     */
    public java.lang.String getUpdatedBy(){
         onPropGet(PROP_ID_updatedBy);
         return _updatedBy;
    }

    /**
     * 修改人: UPDATED_BY
     */
    public void setUpdatedBy(java.lang.String value){
        if(onPropSet(PROP_ID_updatedBy,value)){
            this._updatedBy = value;
            internalClearRefs(PROP_ID_updatedBy);
            
        }
    }
    
    /**
     * 修改时间: UPDATE_TIME
     */
    public java.sql.Timestamp getUpdateTime(){
         onPropGet(PROP_ID_updateTime);
         return _updateTime;
    }

    /**
     * 修改时间: UPDATE_TIME
     */
    public void setUpdateTime(java.sql.Timestamp value){
        if(onPropSet(PROP_ID_updateTime,value)){
            this._updateTime = value;
            internalClearRefs(PROP_ID_updateTime);
            
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
