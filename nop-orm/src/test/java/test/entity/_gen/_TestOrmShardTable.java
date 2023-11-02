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

import test.entity.TestOrmShardTable;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  : TEST_ORM_SHARD_TABLE
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement"})
public class _TestOrmShardTable extends DynamicOrmEntity{
    
    /* : SID VARCHAR */
    public static final String PROP_NAME_sid = "sid";
    public static final int PROP_ID_sid = 1;
    
    /* : STR_VALUE VARCHAR */
    public static final String PROP_NAME_strValue = "strValue";
    public static final int PROP_ID_strValue = 2;
    
    /* : NOP_SHARD VARCHAR */
    public static final String PROP_NAME_nopShard = "nopShard";
    public static final int PROP_ID_nopShard = 3;
    

    private static int _PROP_ID_BOUND = 4;

    

    public static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_sid);
    public static final int[] PK_PROP_IDS = new int[]{PROP_ID_sid};

    private static final String[] PROP_ID_TO_NAME = new String[4];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_sid] = PROP_NAME_sid;
          PROP_NAME_TO_ID.put(PROP_NAME_sid, PROP_ID_sid);
      
          PROP_ID_TO_NAME[PROP_ID_strValue] = PROP_NAME_strValue;
          PROP_NAME_TO_ID.put(PROP_NAME_strValue, PROP_ID_strValue);
      
          PROP_ID_TO_NAME[PROP_ID_nopShard] = PROP_NAME_nopShard;
          PROP_NAME_TO_ID.put(PROP_NAME_nopShard, PROP_ID_nopShard);
      
    }

    
    /* : SID */
    private java.lang.String _sid;
    
    /* : STR_VALUE */
    private java.lang.String _strValue;
    
    /* : NOP_SHARD */
    private java.lang.String _nopShard;
    

    public _TestOrmShardTable(){
    }

    protected TestOrmShardTable newInstance(){
       return new TestOrmShardTable();
    }

    @Override
    public TestOrmShardTable cloneInstance() {
        TestOrmShardTable entity = newInstance();
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
      return "test.entity.TestOrmShardTable";
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
               this._sid = (java.lang.String)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_strValue:{
               onInitProp(propId);
               this._strValue = (java.lang.String)value;
               
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
    public java.lang.String getSid(){
         onPropGet(PROP_ID_sid);
         return _sid;
    }

    /**
     * : SID
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
    
}
// resume CPD analysis - CPD-ON
