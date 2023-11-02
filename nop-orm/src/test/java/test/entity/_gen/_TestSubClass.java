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

import test.entity.TestSubClass;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : TEST_SUB_CLASS
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _TestSubClass extends DynamicOrmEntity{
    
    /* : SUB_ID VARCHAR */
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
    
    /* : USER_ID VARCHAR */
    public static final String PROP_NAME_userId = "userId";
    public static final int PROP_ID_userId = 5;
    
    /* : DUP_FLD_SUB VARCHAR */
    public static final String PROP_NAME_dupFld = "dupFld";
    public static final int PROP_ID_dupFld = 6;
    
    /* : IS_EXT VARCHAR */
    public static final String PROP_NAME_testOrmExt = "testOrmExt";
    public static final int PROP_ID_testOrmExt = 7;
    

    private static int _PROP_ID_BOUND = 8;

    
    /* relation:  */
    public static final String PROP_NAME_user = "user";
    
    /* relation:  */
    public static final String PROP_NAME_user2 = "user2";
    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[8];
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
      
          PROP_ID_TO_NAME[PROP_ID_userId] = PROP_NAME_userId;
          PROP_NAME_TO_ID.put(PROP_NAME_userId, PROP_ID_userId);
      
          PROP_ID_TO_NAME[PROP_ID_dupFld] = PROP_NAME_dupFld;
          PROP_NAME_TO_ID.put(PROP_NAME_dupFld, PROP_ID_dupFld);
      
          PROP_ID_TO_NAME[PROP_ID_testOrmExt] = PROP_NAME_testOrmExt;
          PROP_NAME_TO_ID.put(PROP_NAME_testOrmExt, PROP_ID_testOrmExt);
      
    }

    
    /* : SUB_ID */
    private java.lang.String _sid;
    
    /* : STR_VALUE */
    private java.lang.String _strValue;
    
    /* : CLOB_COL */
    private java.lang.String _clobCol;
    
    /* : BLOB_COL */
    private io.nop.commons.bytes.ByteString _blobCol;
    
    /* : USER_ID */
    private java.lang.String _userId;
    
    /* : DUP_FLD_SUB */
    private java.lang.String _dupFld;
    
    /* : IS_EXT */
    private java.lang.String _testOrmExt;
    

    public _TestSubClass(){
    }

    protected TestSubClass newInstance(){
       return new TestSubClass();
    }

    @Override
    public TestSubClass cloneInstance() {
        TestSubClass entity = newInstance();
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
      return "test.entity.TestSubClass";
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
        
            case PROP_ID_userId:
               return getUserId();
        
            case PROP_ID_dupFld:
               return getDupFld();
        
            case PROP_ID_testOrmExt:
               return getTestOrmExt();
        
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
        
            case PROP_ID_userId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_userId));
               }
               setUserId(typedValue);
               break;
            }
        
            case PROP_ID_dupFld:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_dupFld));
               }
               setDupFld(typedValue);
               break;
            }
        
            case PROP_ID_testOrmExt:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_testOrmExt));
               }
               setTestOrmExt(typedValue);
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
        
            case PROP_ID_userId:{
               onInitProp(propId);
               this._userId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_dupFld:{
               onInitProp(propId);
               this._dupFld = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_testOrmExt:{
               onInitProp(propId);
               this._testOrmExt = (java.lang.String)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * : SUB_ID
     */
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * : SUB_ID
     */
    public void setSid(java.lang.String value){
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
     * : USER_ID
     */
    public java.lang.String getUserId(){
         onPropGet(PROP_ID_userId);
         return _userId;
    }

    /**
     * : USER_ID
     */
    public void setUserId(java.lang.String value){
        if(onPropSet(PROP_ID_userId,value)){
            this._userId = value;
            internalClearRefs(PROP_ID_userId);
            
        }
    }
    
    /**
     * : DUP_FLD_SUB
     */
    public java.lang.String getDupFld(){
         onPropGet(PROP_ID_dupFld);
         return _dupFld;
    }

    /**
     * : DUP_FLD_SUB
     */
    public void setDupFld(java.lang.String value){
        if(onPropSet(PROP_ID_dupFld,value)){
            this._dupFld = value;
            internalClearRefs(PROP_ID_dupFld);
            
        }
    }
    
    /**
     * : IS_EXT
     */
    public java.lang.String getTestOrmExt(){
         onPropGet(PROP_ID_testOrmExt);
         return _testOrmExt;
    }

    /**
     * : IS_EXT
     */
    public void setTestOrmExt(java.lang.String value){
        if(onPropSet(PROP_ID_testOrmExt,value)){
            this._testOrmExt = value;
            internalClearRefs(PROP_ID_testOrmExt);
            
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
    public test.entity.UserInfo getUser2(){
       return (test.entity.UserInfo)internalGetRefEntity(PROP_NAME_user2);
    }

    public void setUser2(test.entity.UserInfo refEntity){
       if(refEntity == null){
         
         this.setUserId(null);
         
       }else{
          internalSetRefEntity(PROP_NAME_user2, refEntity,()->{
             
                    this.setUserId(refEntity.getSid());
                 
          });
       }
    }
       
}
// resume CPD analysis - CPD-ON
