package test.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import test.entity.TestOrmTable;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : TEST_ORM_TABLE
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _TestOrmTable extends DynamicOrmEntity{
    
    /* : SID INTEGER */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* : STR_VALUE VARCHAR */
    public static final String PROP_NAME_strValue = "strValue";
    public static final int PROP_ID_strValue = 2;
    
    /* : CLOB_COL VARCHAR */
    public static final String PROP_NAME_clobCol = "clobCol";
    public static final int PROP_ID_clobCol = 3;
    
    /* : BLOB_COL VARBINARY */
    public static final String PROP_NAME_blobCol = "blobCol";
    public static final int PROP_ID_blobCol = 4;
    
    /* : NUM_VALUE DOUBLE */
    public static final String PROP_NAME_numValue = "numValue";
    public static final int PROP_ID_numValue = 5;
    
    /* : UTIL_DATE_VALUE DATETIME */
    public static final String PROP_NAME_utilDateValue = "utilDateValue";
    public static final int PROP_ID_utilDateValue = 6;
    
    /* : SQL_DATE_VALUE DATE */
    public static final String PROP_NAME_sqlDateValue = "sqlDateValue";
    public static final int PROP_ID_sqlDateValue = 7;
    
    /* : JSON_MAP_COL VARCHAR */
    public static final String PROP_NAME_jsonMap = "jsonMap";
    public static final int PROP_ID_jsonMap = 8;
    
    /* : JSON_LIST_COL VARCHAR */
    public static final String PROP_NAME_jsonList = "jsonList";
    public static final int PROP_ID_jsonList = 9;
    
    /* : rec_ver INTEGER */
    public static final String PROP_NAME_version = "version";
    public static final int PROP_ID_version = 10;
    
    /* : SHARD_ID VARCHAR */
    public static final String PROP_NAME_shardId = "shardId";
    public static final int PROP_ID_shardId = 11;
    
    /* : SUB_CLASS_ID VARCHAR */
    public static final String PROP_NAME_subClassId = "subClassId";
    public static final int PROP_ID_subClassId = 12;
    
    /* : user_id VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 13;
    
    /* : parent_id VARCHAR */
    public static final String PROP_NAME_parentId = "parentId";
    public static final int PROP_ID_parentId = 14;
    
    /* : NOP_SHARD VARCHAR */
    public static final String PROP_NAME_nopShard = "nopShard";
    public static final int PROP_ID_nopShard = 15;
    

    private static int _PROP_ID_BOUND = 16;

    
    /* relation:  */
    public static final String PROP_NAME_shardTable = "shardTable";
    
    /* relation:  */
    public static final String PROP_NAME_user = "user";
    
    /* relation:  */
    public static final String PROP_NAME_parent = "parent";
    
    /* relation:  */
    public static final String PROP_NAME_testOrmToOne = "testOrmToOne";
    
    /* relation:  */
    public static final String PROP_NAME_lefts = "lefts";
    
    /* relation:  */
    public static final String PROP_NAME_subClass = "subClass";
    
    /* relation:  */
    public static final String PROP_NAME_subTables = "subTables";
    
    /* relation:  */
    public static final String PROP_NAME_otherTables = "otherTables";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[16];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_strValue] = PROP_NAME_strValue;
          PROP_NAME_TO_ID.put(PROP_NAME_strValue, PROP_ID_strValue);
      
          PROP_ID_TO_NAME[PROP_ID_clobCol] = PROP_NAME_clobCol;
          PROP_NAME_TO_ID.put(PROP_NAME_clobCol, PROP_ID_clobCol);
      
          PROP_ID_TO_NAME[PROP_ID_blobCol] = PROP_NAME_blobCol;
          PROP_NAME_TO_ID.put(PROP_NAME_blobCol, PROP_ID_blobCol);
      
          PROP_ID_TO_NAME[PROP_ID_numValue] = PROP_NAME_numValue;
          PROP_NAME_TO_ID.put(PROP_NAME_numValue, PROP_ID_numValue);
      
          PROP_ID_TO_NAME[PROP_ID_utilDateValue] = PROP_NAME_utilDateValue;
          PROP_NAME_TO_ID.put(PROP_NAME_utilDateValue, PROP_ID_utilDateValue);
      
          PROP_ID_TO_NAME[PROP_ID_sqlDateValue] = PROP_NAME_sqlDateValue;
          PROP_NAME_TO_ID.put(PROP_NAME_sqlDateValue, PROP_ID_sqlDateValue);
      
          PROP_ID_TO_NAME[PROP_ID_jsonMap] = PROP_NAME_jsonMap;
          PROP_NAME_TO_ID.put(PROP_NAME_jsonMap, PROP_ID_jsonMap);
      
          PROP_ID_TO_NAME[PROP_ID_jsonList] = PROP_NAME_jsonList;
          PROP_NAME_TO_ID.put(PROP_NAME_jsonList, PROP_ID_jsonList);
      
          PROP_ID_TO_NAME[PROP_ID_version] = PROP_NAME_version;
          PROP_NAME_TO_ID.put(PROP_NAME_version, PROP_ID_version);
      
          PROP_ID_TO_NAME[PROP_ID_shardId] = PROP_NAME_shardId;
          PROP_NAME_TO_ID.put(PROP_NAME_shardId, PROP_ID_shardId);
      
          PROP_ID_TO_NAME[PROP_ID_subClassId] = PROP_NAME_subClassId;
          PROP_NAME_TO_ID.put(PROP_NAME_subClassId, PROP_ID_subClassId);
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_parentId] = PROP_NAME_parentId;
          PROP_NAME_TO_ID.put(PROP_NAME_parentId, PROP_ID_parentId);
      
          PROP_ID_TO_NAME[PROP_ID_nopShard] = PROP_NAME_nopShard;
          PROP_NAME_TO_ID.put(PROP_NAME_nopShard, PROP_ID_nopShard);
      
    }

    
    /* : SID */
    private java.lang.Integer _sid;
    
    /* : STR_VALUE */
    private java.lang.String _strValue;
    
    /* : CLOB_COL */
    private java.lang.String _clobCol;
    
    /* : BLOB_COL */
    private io.nop.commons.bytes.ByteString _blobCol;
    
    /* : NUM_VALUE */
    private java.lang.Double _numValue;
    
    /* : UTIL_DATE_VALUE */
    private java.time.LocalDateTime _utilDateValue;
    
    /* : SQL_DATE_VALUE */
    private java.time.LocalDate _sqlDateValue;
    
    /* : JSON_MAP_COL */
    private java.lang.String _jsonMap;
    
    /* : JSON_LIST_COL */
    private java.lang.String _jsonList;
    
    /* : rec_ver */
    private java.lang.Integer _version;
    
    /* : SHARD_ID */
    private java.lang.String _shardId;
    
    /* : SUB_CLASS_ID */
    private java.lang.String _subClassId;
    
    /* : user_id */
    private java.lang.String _userId;
    
    /* : parent_id */
    private java.lang.String _parentId;
    
    /* : NOP_SHARD */
    private java.lang.String _nopShard;
    

    public _TestOrmTable(){
    }

    protected TestOrmTable newInstance(){
       return new TestOrmTable();
    }

    @Override
    public TestOrmTable cloneInstance() {
        TestOrmTable entity = newInstance();
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
      return "test.entity.TestOrmTable";
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
        
            case PROP_ID_strValue:
               return getStrValue();
        
            case PROP_ID_clobCol:
               return getClobCol();
        
            case PROP_ID_blobCol:
               return getBlobCol();
        
            case PROP_ID_numValue:
               return getNumValue();
        
            case PROP_ID_utilDateValue:
               return getUtilDateValue();
        
            case PROP_ID_sqlDateValue:
               return getSqlDateValue();
        
            case PROP_ID_jsonMap:
               return getJsonMap();
        
            case PROP_ID_jsonList:
               return getJsonList();
        
            case PROP_ID_version:
               return getVersion();
        
            case PROP_ID_shardId:
               return getShardId();
        
            case PROP_ID_subClassId:
               return getSubClassId();
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_parentId:
               return getParentId();
        
            case PROP_ID_nopShard:
               return getNopShard();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_sid:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_sid));
               }
               setSid(typedValue);
               break;
            }
        
            case PROP_ID_strValue:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_strValue));
               }
               setStrValue(typedValue);
               break;
            }
        
            case PROP_ID_clobCol:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_clobCol));
               }
               setClobCol(typedValue);
               break;
            }
        
            case PROP_ID_blobCol:{
               io.nop.commons.bytes.ByteString typedValue = null;
               if(value != null){
                   typedValue = io.nop.commons.bytes.ByteString.from(value,
                       err-> newTypeConversionError(PROP_NAME_blobCol));
               }
               setBlobCol(typedValue);
               break;
            }
        
            case PROP_ID_numValue:{
               java.lang.Double typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toDouble(value,
                       err-> newTypeConversionError(PROP_NAME_numValue));
               }
               setNumValue(typedValue);
               break;
            }
        
            case PROP_ID_utilDateValue:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_utilDateValue));
               }
               setUtilDateValue(typedValue);
               break;
            }
        
            case PROP_ID_sqlDateValue:{
               java.time.LocalDate typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDate(value,
                       err-> newTypeConversionError(PROP_NAME_sqlDateValue));
               }
               setSqlDateValue(typedValue);
               break;
            }
        
            case PROP_ID_jsonMap:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jsonMap));
               }
               setJsonMap(typedValue);
               break;
            }
        
            case PROP_ID_jsonList:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_jsonList));
               }
               setJsonList(typedValue);
               break;
            }
        
            case PROP_ID_version:{
               java.lang.Integer typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toInteger(value,
                       err-> newTypeConversionError(PROP_NAME_version));
               }
               setVersion(typedValue);
               break;
            }
        
            case PROP_ID_shardId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_shardId));
               }
               setShardId(typedValue);
               break;
            }
        
            case PROP_ID_subClassId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_subClassId));
               }
               setSubClassId(typedValue);
               break;
            }
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
               break;
            }
        
            case PROP_ID_parentId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_parentId));
               }
               setParentId(typedValue);
               break;
            }
        
            case PROP_ID_nopShard:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nopShard));
               }
               setNopShard(typedValue);
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
               this._sid = (java.lang.Integer)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_strValue:{
               onInitProp(propId);
               this._strValue = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_clobCol:{
               onInitProp(propId);
               this._clobCol = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_blobCol:{
               onInitProp(propId);
               this._blobCol = (io.nop.commons.bytes.ByteString)value;
               
               break;
            }
        
            case PROP_ID_numValue:{
               onInitProp(propId);
               this._numValue = (java.lang.Double)value;
               
               break;
            }
        
            case PROP_ID_utilDateValue:{
               onInitProp(propId);
               this._utilDateValue = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_sqlDateValue:{
               onInitProp(propId);
               this._sqlDateValue = (java.time.LocalDate)value;
               
               break;
            }
        
            case PROP_ID_jsonMap:{
               onInitProp(propId);
               this._jsonMap = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_jsonList:{
               onInitProp(propId);
               this._jsonList = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_version:{
               onInitProp(propId);
               this._version = (java.lang.Integer)value;
               
               break;
            }
        
            case PROP_ID_shardId:{
               onInitProp(propId);
               this._shardId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_subClassId:{
               onInitProp(propId);
               this._subClassId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_parentId:{
               onInitProp(propId);
               this._parentId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_nopShard:{
               onInitProp(propId);
               this._nopShard = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : SID
     */
    public java.lang.Integer getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * : SID
     */
    public void setSid(java.lang.Integer value){
        if(onPropSet(PROP_ID_sid,value)){
            this._sid = value;
            internalClearRefs(PROP_ID_sid);
            orm_id();
        }
    }
    
    /**
     * : STR_VALUE
     */
    public java.lang.String getStrValue(){
         onPropGet(PROP_ID_strValue);
         return _strValue;
    }

    /**
     * : STR_VALUE
     */
    public void setStrValue(java.lang.String value){
        if(onPropSet(PROP_ID_strValue,value)){
            this._strValue = value;
            internalClearRefs(PROP_ID_strValue);
            
        }
    }
    
    /**
     * : CLOB_COL
     */
    public java.lang.String getClobCol(){
         onPropGet(PROP_ID_clobCol);
         return _clobCol;
    }

    /**
     * : CLOB_COL
     */
    public void setClobCol(java.lang.String value){
        if(onPropSet(PROP_ID_clobCol,value)){
            this._clobCol = value;
            internalClearRefs(PROP_ID_clobCol);
            
        }
    }
    
    /**
     * : BLOB_COL
     */
    public io.nop.commons.bytes.ByteString getBlobCol(){
         onPropGet(PROP_ID_blobCol);
         return _blobCol;
    }

    /**
     * : BLOB_COL
     */
    public void setBlobCol(io.nop.commons.bytes.ByteString value){
        if(onPropSet(PROP_ID_blobCol,value)){
            this._blobCol = value;
            internalClearRefs(PROP_ID_blobCol);
            
        }
    }
    
    /**
     * : NUM_VALUE
     */
    public java.lang.Double getNumValue(){
         onPropGet(PROP_ID_numValue);
         return _numValue;
    }

    /**
     * : NUM_VALUE
     */
    public void setNumValue(java.lang.Double value){
        if(onPropSet(PROP_ID_numValue,value)){
            this._numValue = value;
            internalClearRefs(PROP_ID_numValue);
            
        }
    }
    
    /**
     * : UTIL_DATE_VALUE
     */
    public java.time.LocalDateTime getUtilDateValue(){
         onPropGet(PROP_ID_utilDateValue);
         return _utilDateValue;
    }

    /**
     * : UTIL_DATE_VALUE
     */
    public void setUtilDateValue(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_utilDateValue,value)){
            this._utilDateValue = value;
            internalClearRefs(PROP_ID_utilDateValue);
            
        }
    }
    
    /**
     * : SQL_DATE_VALUE
     */
    public java.time.LocalDate getSqlDateValue(){
         onPropGet(PROP_ID_sqlDateValue);
         return _sqlDateValue;
    }

    /**
     * : SQL_DATE_VALUE
     */
    public void setSqlDateValue(java.time.LocalDate value){
        if(onPropSet(PROP_ID_sqlDateValue,value)){
            this._sqlDateValue = value;
            internalClearRefs(PROP_ID_sqlDateValue);
            
        }
    }
    
    /**
     * : JSON_MAP_COL
     */
    public java.lang.String getJsonMap(){
         onPropGet(PROP_ID_jsonMap);
         return _jsonMap;
    }

    /**
     * : JSON_MAP_COL
     */
    public void setJsonMap(java.lang.String value){
        if(onPropSet(PROP_ID_jsonMap,value)){
            this._jsonMap = value;
            internalClearRefs(PROP_ID_jsonMap);
            
        }
    }
    
    /**
     * : JSON_LIST_COL
     */
    public java.lang.String getJsonList(){
         onPropGet(PROP_ID_jsonList);
         return _jsonList;
    }

    /**
     * : JSON_LIST_COL
     */
    public void setJsonList(java.lang.String value){
        if(onPropSet(PROP_ID_jsonList,value)){
            this._jsonList = value;
            internalClearRefs(PROP_ID_jsonList);
            
        }
    }
    
    /**
     * : rec_ver
     */
    public java.lang.Integer getVersion(){
         onPropGet(PROP_ID_version);
         return _version;
    }

    /**
     * : rec_ver
     */
    public void setVersion(java.lang.Integer value){
        if(onPropSet(PROP_ID_version,value)){
            this._version = value;
            internalClearRefs(PROP_ID_version);
            
        }
    }
    
    /**
     * : SHARD_ID
     */
    public java.lang.String getShardId(){
         onPropGet(PROP_ID_shardId);
         return _shardId;
    }

    /**
     * : SHARD_ID
     */
    public void setShardId(java.lang.String value){
        if(onPropSet(PROP_ID_shardId,value)){
            this._shardId = value;
            internalClearRefs(PROP_ID_shardId);
            
        }
    }
    
    /**
     * : SUB_CLASS_ID
     */
    public java.lang.String getSubClassId(){
         onPropGet(PROP_ID_subClassId);
         return _subClassId;
    }

    /**
     * : SUB_CLASS_ID
     */
    public void setSubClassId(java.lang.String value){
        if(onPropSet(PROP_ID_subClassId,value)){
            this._subClassId = value;
            internalClearRefs(PROP_ID_subClassId);
            
        }
    }
    
    /**
     * : user_id
     */
    public java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * : user_id
     */
    public void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
        }
    }
    
    /**
     * : parent_id
     */
    public java.lang.String getParentId(){
         onPropGet(PROP_ID_parentId);
         return _parentId;
    }

    /**
     * : parent_id
     */
    public void setParentId(java.lang.String value){
        if(onPropSet(PROP_ID_parentId,value)){
            this._parentId = value;
            internalClearRefs(PROP_ID_parentId);
            
        }
    }
    
    /**
     * : NOP_SHARD
     */
    public java.lang.String getNopShard(){
         onPropGet(PROP_ID_nopShard);
         return _nopShard;
    }

    /**
     * : NOP_SHARD
     */
    public void setNopShard(java.lang.String value){
        if(onPropSet(PROP_ID_nopShard,value)){
            this._nopShard = value;
            internalClearRefs(PROP_ID_nopShard);
            
        }
    }
    
    /**
     * 
     */
    public test.entity.TestOrmShardTable getShardTable(){
       return (test.entity.TestOrmShardTable)internalGetRefEntity(PROP_NAME_shardTable);
    }

    public void setShardTable(test.entity.TestOrmShardTable refEntity){
       if(refEntity == null){
         
         this.setShardId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_shardTable, refEntity,()->{
             
                    this.setShardId(refEntity.getSid());
                 
          });
       }
    }
       
    /**
     * 
     */
    public test.entity.UserInfo getUser(){
       return (test.entity.UserInfo)internalGetRefEntity(PROP_NAME_user);
    }

    public void setUser(test.entity.UserInfo refEntity){
       if(refEntity == null){
         
         this.setUserId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_user, refEntity,()->{
             
                    this.setUserId(refEntity.getSid());
                 
          });
       }
    }
       
    /**
     * 
     */
    public test.entity.TestOrmTable getParent(){
       return (test.entity.TestOrmTable)internalGetRefEntity(PROP_NAME_parent);
    }

    public void setParent(test.entity.TestOrmTable refEntity){
       if(refEntity == null){
         
         this.setParentId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_parent, refEntity,()->{
             
              this.orm_propValue(PROP_ID_parentId,
                refEntity.getSid());
                
          });
       }
    }
       
    /**
     * 
     */
    public test.entity.TestOrmToOne getTestOrmToOne(){
       return (test.entity.TestOrmToOne)internalGetRefEntity(PROP_NAME_testOrmToOne);
    }

    public void setTestOrmToOne(test.entity.TestOrmToOne refEntity){
       if(refEntity == null){
         
         this.setSid(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_testOrmToOne, refEntity,()->{
             
              this.orm_propValue(PROP_ID_sid,
                refEntity.getSid());
                
          });
       }
    }
       
    private final OrmEntitySet<test.entity.TestManyToManyLeft> _lefts = new OrmEntitySet<>(this, PROP_NAME_lefts,
        test.entity.TestManyToManyLeft.PROP_NAME_testOrmTable, null,test.entity.TestManyToManyLeft.class);

    /**
     * 。 refPropName: testOrmTable, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestManyToManyLeft> getLefts(){
       return _lefts;
    }
       
    /**
     * 
     */
    public test.entity.TestSubClass getSubClass(){
       return (test.entity.TestSubClass)internalGetRefEntity(PROP_NAME_subClass);
    }

    public void setSubClass(test.entity.TestSubClass refEntity){
       if(refEntity == null){
         
         this.setSubClassId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_subClass, refEntity,()->{
             
                    this.setSubClassId(refEntity.getSid());
                 
          });
       }
    }
       
    private final OrmEntitySet<test.entity.TestOrmSubTable> _subTables = new OrmEntitySet<>(this, PROP_NAME_subTables,
        test.entity.TestOrmSubTable.PROP_NAME_testOrmTable, null,test.entity.TestOrmSubTable.class);

    /**
     * 。 refPropName: testOrmTable, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestOrmSubTable> getSubTables(){
       return _subTables;
    }
       
    private final OrmEntitySet<test.entity.TestOrmOtherTable> _otherTables = new OrmEntitySet<>(this, PROP_NAME_otherTables,
        test.entity.TestOrmOtherTable.PROP_NAME_testOrmTable, test.entity.TestOrmOtherTable.PROP_NAME_strValue,test.entity.TestOrmOtherTable.class);

    /**
     * 。 refPropName: testOrmTable, keyProp: {rel.keyProp}
     */
    public IOrmEntitySet<test.entity.TestOrmOtherTable> getOtherTables(){
       return _otherTables;
    }
       
}
// resume CPD analysis - CPD-ON
