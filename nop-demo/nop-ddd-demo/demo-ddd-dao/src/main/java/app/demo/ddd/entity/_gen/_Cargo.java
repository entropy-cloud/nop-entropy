package app.demo.ddd.entity._gen;

import io.nop.orm.model.IEntityModel;
import io.nop.orm.support.DynamicOrmEntity;
import io.nop.orm.support.OrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.orm.IOrmEntitySet; //NOPMD - suppressed UnusedImports - Auto Gen Code
import io.nop.api.core.convert.ConvertHelper;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;
import java.util.List;

import app.demo.ddd.entity.Cargo;

// tell cpd to start ignoring code - CPD-OFF
/**
 *  货物: cargo
 */
@SuppressWarnings({"PMD.UselessOverridingMethod","PMD.UnusedLocalVariable","java:S3008","java:S1602","java:S1128","java:S1161",
        "PMD.UnnecessaryFullyQualifiedName","PMD.EmptyControlStatement","java:S116","java:S115","java:S101","java:S3776"})
public class _Cargo extends DynamicOrmEntity{
    
    /* Id: ID BIGINT */
    public static final String PROP_NAME_id = "id";
    public static final int PROP_ID_id = 1;
    
    /* 计算时间: CALCULATED_AT DATETIME */
    public static final String PROP_NAME_calculatedAt = "calculatedAt";
    public static final int PROP_ID_calculatedAt = 2;
    
    /* 预计到达时间: ETA DATETIME */
    public static final String PROP_NAME_eta = "eta";
    public static final int PROP_ID_eta = 3;
    
    /* 目的地卸载时间: UNLOADED_AT_DEST BOOLEAN */
    public static final String PROP_NAME_unloadedAtDest = "unloadedAtDest";
    public static final int PROP_ID_unloadedAtDest = 4;
    
    /* 路线错误: MISDIRECTED BOOLEAN */
    public static final String PROP_NAME_misdirected = "misdirected";
    public static final int PROP_ID_misdirected = 5;
    
    /* 下一步预期处理事件类型: NEXT_EXPECTED_HANDLING_EVENT_TYPE VARCHAR */
    public static final String PROP_NAME_nextExpectedHandlingEventType = "nextExpectedHandlingEventType";
    public static final int PROP_ID_nextExpectedHandlingEventType = 6;
    
    /* 路由状态: ROUTING_STATUS VARCHAR */
    public static final String PROP_NAME_routingStatus = "routingStatus";
    public static final int PROP_ID_routingStatus = 7;
    
    /* 运输状态: TRANSPORT_STATUS VARCHAR */
    public static final String PROP_NAME_transportStatus = "transportStatus";
    public static final int PROP_ID_transportStatus = 8;
    
    /* 指定到达期限: SPEC_ARRIVAL_DEADLINE DATETIME */
    public static final String PROP_NAME_specArrivalDeadline = "specArrivalDeadline";
    public static final int PROP_ID_specArrivalDeadline = 9;
    
    /* 跟踪ID: TRACKING_ID VARCHAR */
    public static final String PROP_NAME_trackingId = "trackingId";
    public static final int PROP_ID_trackingId = 10;
    
    /* 当前航程ID: CURRENT_VOYAGE_ID BIGINT */
    public static final String PROP_NAME_currentVoyageId = "currentVoyageId";
    public static final int PROP_ID_currentVoyageId = 11;
    
    /* 最后事件ID: LAST_EVENT_ID BIGINT */
    public static final String PROP_NAME_lastEventId = "lastEventId";
    public static final int PROP_ID_lastEventId = 12;
    
    /* 最后已知位置ID: LAST_KNOWN_LOCATION_ID BIGINT */
    public static final String PROP_NAME_lastKnownLocationId = "lastKnownLocationId";
    public static final int PROP_ID_lastKnownLocationId = 13;
    
    /* 下一个预期位置ID: NEXT_EXPECTED_LOCATION_ID BIGINT */
    public static final String PROP_NAME_nextExpectedLocationId = "nextExpectedLocationId";
    public static final int PROP_ID_nextExpectedLocationId = 14;
    
    /* 下一个预期航程ID: NEXT_EXPECTED_VOYAGE_ID BIGINT */
    public static final String PROP_NAME_nextExpectedVoyageId = "nextExpectedVoyageId";
    public static final int PROP_ID_nextExpectedVoyageId = 15;
    
    /* 出发地ID: ORIGIN_ID BIGINT */
    public static final String PROP_NAME_originId = "originId";
    public static final int PROP_ID_originId = 16;
    
    /* 指定目的地ID: SPEC_DESTINATION_ID BIGINT */
    public static final String PROP_NAME_specDestinationId = "specDestinationId";
    public static final int PROP_ID_specDestinationId = 17;
    
    /* 指定出发地ID: SPEC_ORIGIN_ID BIGINT */
    public static final String PROP_NAME_specOriginId = "specOriginId";
    public static final int PROP_ID_specOriginId = 18;
    

    private static int _PROP_ID_BOUND = 19;

    
    /* relation: 下一个预期航程 */
    public static final String PROP_NAME_nextExpectedVoyage = "nextExpectedVoyage";
    
    /* relation: 指定目的地 */
    public static final String PROP_NAME_specDestination = "specDestination";
    
    /* relation: 出发地 */
    public static final String PROP_NAME_origin = "origin";
    
    /* relation: 最后事件 */
    public static final String PROP_NAME_lastEvent = "lastEvent";
    
    /* relation: 最后已知位置 */
    public static final String PROP_NAME_lastKnownLocation = "lastKnownLocation";
    
    /* relation: 当前航程 */
    public static final String PROP_NAME_currentVoyage = "currentVoyage";
    
    /* relation: 下一个预期位置 */
    public static final String PROP_NAME_nextExpectedLocation = "nextExpectedLocation";
    
    /* relation: 指定出发地 */
    public static final String PROP_NAME_specOrigin = "specOrigin";
    

    protected static final List<String> PK_PROP_NAMES = Arrays.asList(PROP_NAME_id);
    protected static final int[] PK_PROP_IDS = new int[]{PROP_ID_id};

    private static final String[] PROP_ID_TO_NAME = new String[19];
    private static final Map<String,Integer> PROP_NAME_TO_ID = new HashMap<>();
    static{
      
          PROP_ID_TO_NAME[PROP_ID_id] = PROP_NAME_id;
          PROP_NAME_TO_ID.put(PROP_NAME_id, PROP_ID_id);
      
          PROP_ID_TO_NAME[PROP_ID_calculatedAt] = PROP_NAME_calculatedAt;
          PROP_NAME_TO_ID.put(PROP_NAME_calculatedAt, PROP_ID_calculatedAt);
      
          PROP_ID_TO_NAME[PROP_ID_eta] = PROP_NAME_eta;
          PROP_NAME_TO_ID.put(PROP_NAME_eta, PROP_ID_eta);
      
          PROP_ID_TO_NAME[PROP_ID_unloadedAtDest] = PROP_NAME_unloadedAtDest;
          PROP_NAME_TO_ID.put(PROP_NAME_unloadedAtDest, PROP_ID_unloadedAtDest);
      
          PROP_ID_TO_NAME[PROP_ID_misdirected] = PROP_NAME_misdirected;
          PROP_NAME_TO_ID.put(PROP_NAME_misdirected, PROP_ID_misdirected);
      
          PROP_ID_TO_NAME[PROP_ID_nextExpectedHandlingEventType] = PROP_NAME_nextExpectedHandlingEventType;
          PROP_NAME_TO_ID.put(PROP_NAME_nextExpectedHandlingEventType, PROP_ID_nextExpectedHandlingEventType);
      
          PROP_ID_TO_NAME[PROP_ID_routingStatus] = PROP_NAME_routingStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_routingStatus, PROP_ID_routingStatus);
      
          PROP_ID_TO_NAME[PROP_ID_transportStatus] = PROP_NAME_transportStatus;
          PROP_NAME_TO_ID.put(PROP_NAME_transportStatus, PROP_ID_transportStatus);
      
          PROP_ID_TO_NAME[PROP_ID_specArrivalDeadline] = PROP_NAME_specArrivalDeadline;
          PROP_NAME_TO_ID.put(PROP_NAME_specArrivalDeadline, PROP_ID_specArrivalDeadline);
      
          PROP_ID_TO_NAME[PROP_ID_trackingId] = PROP_NAME_trackingId;
          PROP_NAME_TO_ID.put(PROP_NAME_trackingId, PROP_ID_trackingId);
      
          PROP_ID_TO_NAME[PROP_ID_currentVoyageId] = PROP_NAME_currentVoyageId;
          PROP_NAME_TO_ID.put(PROP_NAME_currentVoyageId, PROP_ID_currentVoyageId);
      
          PROP_ID_TO_NAME[PROP_ID_lastEventId] = PROP_NAME_lastEventId;
          PROP_NAME_TO_ID.put(PROP_NAME_lastEventId, PROP_ID_lastEventId);
      
          PROP_ID_TO_NAME[PROP_ID_lastKnownLocationId] = PROP_NAME_lastKnownLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_lastKnownLocationId, PROP_ID_lastKnownLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_nextExpectedLocationId] = PROP_NAME_nextExpectedLocationId;
          PROP_NAME_TO_ID.put(PROP_NAME_nextExpectedLocationId, PROP_ID_nextExpectedLocationId);
      
          PROP_ID_TO_NAME[PROP_ID_nextExpectedVoyageId] = PROP_NAME_nextExpectedVoyageId;
          PROP_NAME_TO_ID.put(PROP_NAME_nextExpectedVoyageId, PROP_ID_nextExpectedVoyageId);
      
          PROP_ID_TO_NAME[PROP_ID_originId] = PROP_NAME_originId;
          PROP_NAME_TO_ID.put(PROP_NAME_originId, PROP_ID_originId);
      
          PROP_ID_TO_NAME[PROP_ID_specDestinationId] = PROP_NAME_specDestinationId;
          PROP_NAME_TO_ID.put(PROP_NAME_specDestinationId, PROP_ID_specDestinationId);
      
          PROP_ID_TO_NAME[PROP_ID_specOriginId] = PROP_NAME_specOriginId;
          PROP_NAME_TO_ID.put(PROP_NAME_specOriginId, PROP_ID_specOriginId);
      
    }

    
    /* Id: ID */
    private java.lang.Long _id;
    
    /* 计算时间: CALCULATED_AT */
    private java.time.LocalDateTime _calculatedAt;
    
    /* 预计到达时间: ETA */
    private java.time.LocalDateTime _eta;
    
    /* 目的地卸载时间: UNLOADED_AT_DEST */
    private java.lang.Boolean _unloadedAtDest;
    
    /* 路线错误: MISDIRECTED */
    private java.lang.Boolean _misdirected;
    
    /* 下一步预期处理事件类型: NEXT_EXPECTED_HANDLING_EVENT_TYPE */
    private java.lang.String _nextExpectedHandlingEventType;
    
    /* 路由状态: ROUTING_STATUS */
    private java.lang.String _routingStatus;
    
    /* 运输状态: TRANSPORT_STATUS */
    private java.lang.String _transportStatus;
    
    /* 指定到达期限: SPEC_ARRIVAL_DEADLINE */
    private java.time.LocalDateTime _specArrivalDeadline;
    
    /* 跟踪ID: TRACKING_ID */
    private java.lang.String _trackingId;
    
    /* 当前航程ID: CURRENT_VOYAGE_ID */
    private java.lang.Long _currentVoyageId;
    
    /* 最后事件ID: LAST_EVENT_ID */
    private java.lang.Long _lastEventId;
    
    /* 最后已知位置ID: LAST_KNOWN_LOCATION_ID */
    private java.lang.Long _lastKnownLocationId;
    
    /* 下一个预期位置ID: NEXT_EXPECTED_LOCATION_ID */
    private java.lang.Long _nextExpectedLocationId;
    
    /* 下一个预期航程ID: NEXT_EXPECTED_VOYAGE_ID */
    private java.lang.Long _nextExpectedVoyageId;
    
    /* 出发地ID: ORIGIN_ID */
    private java.lang.Long _originId;
    
    /* 指定目的地ID: SPEC_DESTINATION_ID */
    private java.lang.Long _specDestinationId;
    
    /* 指定出发地ID: SPEC_ORIGIN_ID */
    private java.lang.Long _specOriginId;
    

    public _Cargo(){
        // for debug
    }

    protected Cargo newInstance(){
        Cargo entity = new Cargo();
        entity.orm_attach(orm_enhancer());
        entity.orm_entityModel(orm_entityModel());
        return entity;
    }

    @Override
    public Cargo cloneInstance() {
        Cargo entity = newInstance();
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
      return "app.demo.ddd.entity.Cargo";
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
    
        return buildSimpleId(PROP_ID_id);
     
    }

    @Override
    public boolean orm_isPrimary(int propId) {
        
            return propId == PROP_ID_id;
          
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
        
            case PROP_ID_id:
               return getId();
        
            case PROP_ID_calculatedAt:
               return getCalculatedAt();
        
            case PROP_ID_eta:
               return getEta();
        
            case PROP_ID_unloadedAtDest:
               return getUnloadedAtDest();
        
            case PROP_ID_misdirected:
               return getMisdirected();
        
            case PROP_ID_nextExpectedHandlingEventType:
               return getNextExpectedHandlingEventType();
        
            case PROP_ID_routingStatus:
               return getRoutingStatus();
        
            case PROP_ID_transportStatus:
               return getTransportStatus();
        
            case PROP_ID_specArrivalDeadline:
               return getSpecArrivalDeadline();
        
            case PROP_ID_trackingId:
               return getTrackingId();
        
            case PROP_ID_currentVoyageId:
               return getCurrentVoyageId();
        
            case PROP_ID_lastEventId:
               return getLastEventId();
        
            case PROP_ID_lastKnownLocationId:
               return getLastKnownLocationId();
        
            case PROP_ID_nextExpectedLocationId:
               return getNextExpectedLocationId();
        
            case PROP_ID_nextExpectedVoyageId:
               return getNextExpectedVoyageId();
        
            case PROP_ID_originId:
               return getOriginId();
        
            case PROP_ID_specDestinationId:
               return getSpecDestinationId();
        
            case PROP_ID_specOriginId:
               return getSpecOriginId();
        
           default:
              return super.orm_propValue(propId);
        }
    }

    

    @Override
    public void orm_propValue(int propId, Object value){
        switch(propId){
        
            case PROP_ID_id:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_id));
               }
               setId(typedValue);
               break;
            }
        
            case PROP_ID_calculatedAt:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_calculatedAt));
               }
               setCalculatedAt(typedValue);
               break;
            }
        
            case PROP_ID_eta:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_eta));
               }
               setEta(typedValue);
               break;
            }
        
            case PROP_ID_unloadedAtDest:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_unloadedAtDest));
               }
               setUnloadedAtDest(typedValue);
               break;
            }
        
            case PROP_ID_misdirected:{
               java.lang.Boolean typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toBoolean(value,
                       err-> newTypeConversionError(PROP_NAME_misdirected));
               }
               setMisdirected(typedValue);
               break;
            }
        
            case PROP_ID_nextExpectedHandlingEventType:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_nextExpectedHandlingEventType));
               }
               setNextExpectedHandlingEventType(typedValue);
               break;
            }
        
            case PROP_ID_routingStatus:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_routingStatus));
               }
               setRoutingStatus(typedValue);
               break;
            }
        
            case PROP_ID_transportStatus:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_transportStatus));
               }
               setTransportStatus(typedValue);
               break;
            }
        
            case PROP_ID_specArrivalDeadline:{
               java.time.LocalDateTime typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLocalDateTime(value,
                       err-> newTypeConversionError(PROP_NAME_specArrivalDeadline));
               }
               setSpecArrivalDeadline(typedValue);
               break;
            }
        
            case PROP_ID_trackingId:{
               java.lang.String typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toString(value,
                       err-> newTypeConversionError(PROP_NAME_trackingId));
               }
               setTrackingId(typedValue);
               break;
            }
        
            case PROP_ID_currentVoyageId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_currentVoyageId));
               }
               setCurrentVoyageId(typedValue);
               break;
            }
        
            case PROP_ID_lastEventId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_lastEventId));
               }
               setLastEventId(typedValue);
               break;
            }
        
            case PROP_ID_lastKnownLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_lastKnownLocationId));
               }
               setLastKnownLocationId(typedValue);
               break;
            }
        
            case PROP_ID_nextExpectedLocationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_nextExpectedLocationId));
               }
               setNextExpectedLocationId(typedValue);
               break;
            }
        
            case PROP_ID_nextExpectedVoyageId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_nextExpectedVoyageId));
               }
               setNextExpectedVoyageId(typedValue);
               break;
            }
        
            case PROP_ID_originId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_originId));
               }
               setOriginId(typedValue);
               break;
            }
        
            case PROP_ID_specDestinationId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_specDestinationId));
               }
               setSpecDestinationId(typedValue);
               break;
            }
        
            case PROP_ID_specOriginId:{
               java.lang.Long typedValue = null;
               if(value != null){
                   typedValue = ConvertHelper.toLong(value,
                       err-> newTypeConversionError(PROP_NAME_specOriginId));
               }
               setSpecOriginId(typedValue);
               break;
            }
        
           default:
              super.orm_propValue(propId,value);
        }
    }

    @Override
    public void orm_internalSet(int propId, Object value) {
        switch(propId){
        
            case PROP_ID_id:{
               onInitProp(propId);
               this._id = (java.lang.Long)value;
               orm_id(); // 如果是设置主键字段，则触发watcher
               break;
            }
        
            case PROP_ID_calculatedAt:{
               onInitProp(propId);
               this._calculatedAt = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_eta:{
               onInitProp(propId);
               this._eta = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_unloadedAtDest:{
               onInitProp(propId);
               this._unloadedAtDest = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_misdirected:{
               onInitProp(propId);
               this._misdirected = (java.lang.Boolean)value;
               
               break;
            }
        
            case PROP_ID_nextExpectedHandlingEventType:{
               onInitProp(propId);
               this._nextExpectedHandlingEventType = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_routingStatus:{
               onInitProp(propId);
               this._routingStatus = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_transportStatus:{
               onInitProp(propId);
               this._transportStatus = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_specArrivalDeadline:{
               onInitProp(propId);
               this._specArrivalDeadline = (java.time.LocalDateTime)value;
               
               break;
            }
        
            case PROP_ID_trackingId:{
               onInitProp(propId);
               this._trackingId = (java.lang.String)value;
               
               break;
            }
        
            case PROP_ID_currentVoyageId:{
               onInitProp(propId);
               this._currentVoyageId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_lastEventId:{
               onInitProp(propId);
               this._lastEventId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_lastKnownLocationId:{
               onInitProp(propId);
               this._lastKnownLocationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_nextExpectedLocationId:{
               onInitProp(propId);
               this._nextExpectedLocationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_nextExpectedVoyageId:{
               onInitProp(propId);
               this._nextExpectedVoyageId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_originId:{
               onInitProp(propId);
               this._originId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_specDestinationId:{
               onInitProp(propId);
               this._specDestinationId = (java.lang.Long)value;
               
               break;
            }
        
            case PROP_ID_specOriginId:{
               onInitProp(propId);
               this._specOriginId = (java.lang.Long)value;
               
               break;
            }
        
           default:
              super.orm_internalSet(propId,value);
        }
    }

    
    /**
     * Id: ID
     */
    public java.lang.Long getId(){
         onPropGet(PROP_ID_id);
         return _id;
    }

    /**
     * Id: ID
     */
    public void setId(java.lang.Long value){
        if(onPropSet(PROP_ID_id,value)){
            this._id = value;
            internalClearRefs(PROP_ID_id);
            orm_id();
        }
    }
    
    /**
     * 计算时间: CALCULATED_AT
     */
    public java.time.LocalDateTime getCalculatedAt(){
         onPropGet(PROP_ID_calculatedAt);
         return _calculatedAt;
    }

    /**
     * 计算时间: CALCULATED_AT
     */
    public void setCalculatedAt(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_calculatedAt,value)){
            this._calculatedAt = value;
            internalClearRefs(PROP_ID_calculatedAt);
            
        }
    }
    
    /**
     * 预计到达时间: ETA
     */
    public java.time.LocalDateTime getEta(){
         onPropGet(PROP_ID_eta);
         return _eta;
    }

    /**
     * 预计到达时间: ETA
     */
    public void setEta(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_eta,value)){
            this._eta = value;
            internalClearRefs(PROP_ID_eta);
            
        }
    }
    
    /**
     * 目的地卸载时间: UNLOADED_AT_DEST
     */
    public java.lang.Boolean getUnloadedAtDest(){
         onPropGet(PROP_ID_unloadedAtDest);
         return _unloadedAtDest;
    }

    /**
     * 目的地卸载时间: UNLOADED_AT_DEST
     */
    public void setUnloadedAtDest(java.lang.Boolean value){
        if(onPropSet(PROP_ID_unloadedAtDest,value)){
            this._unloadedAtDest = value;
            internalClearRefs(PROP_ID_unloadedAtDest);
            
        }
    }
    
    /**
     * 路线错误: MISDIRECTED
     */
    public java.lang.Boolean getMisdirected(){
         onPropGet(PROP_ID_misdirected);
         return _misdirected;
    }

    /**
     * 路线错误: MISDIRECTED
     */
    public void setMisdirected(java.lang.Boolean value){
        if(onPropSet(PROP_ID_misdirected,value)){
            this._misdirected = value;
            internalClearRefs(PROP_ID_misdirected);
            
        }
    }
    
    /**
     * 下一步预期处理事件类型: NEXT_EXPECTED_HANDLING_EVENT_TYPE
     */
    public java.lang.String getNextExpectedHandlingEventType(){
         onPropGet(PROP_ID_nextExpectedHandlingEventType);
         return _nextExpectedHandlingEventType;
    }

    /**
     * 下一步预期处理事件类型: NEXT_EXPECTED_HANDLING_EVENT_TYPE
     */
    public void setNextExpectedHandlingEventType(java.lang.String value){
        if(onPropSet(PROP_ID_nextExpectedHandlingEventType,value)){
            this._nextExpectedHandlingEventType = value;
            internalClearRefs(PROP_ID_nextExpectedHandlingEventType);
            
        }
    }
    
    /**
     * 路由状态: ROUTING_STATUS
     */
    public java.lang.String getRoutingStatus(){
         onPropGet(PROP_ID_routingStatus);
         return _routingStatus;
    }

    /**
     * 路由状态: ROUTING_STATUS
     */
    public void setRoutingStatus(java.lang.String value){
        if(onPropSet(PROP_ID_routingStatus,value)){
            this._routingStatus = value;
            internalClearRefs(PROP_ID_routingStatus);
            
        }
    }
    
    /**
     * 运输状态: TRANSPORT_STATUS
     */
    public java.lang.String getTransportStatus(){
         onPropGet(PROP_ID_transportStatus);
         return _transportStatus;
    }

    /**
     * 运输状态: TRANSPORT_STATUS
     */
    public void setTransportStatus(java.lang.String value){
        if(onPropSet(PROP_ID_transportStatus,value)){
            this._transportStatus = value;
            internalClearRefs(PROP_ID_transportStatus);
            
        }
    }
    
    /**
     * 指定到达期限: SPEC_ARRIVAL_DEADLINE
     */
    public java.time.LocalDateTime getSpecArrivalDeadline(){
         onPropGet(PROP_ID_specArrivalDeadline);
         return _specArrivalDeadline;
    }

    /**
     * 指定到达期限: SPEC_ARRIVAL_DEADLINE
     */
    public void setSpecArrivalDeadline(java.time.LocalDateTime value){
        if(onPropSet(PROP_ID_specArrivalDeadline,value)){
            this._specArrivalDeadline = value;
            internalClearRefs(PROP_ID_specArrivalDeadline);
            
        }
    }
    
    /**
     * 跟踪ID: TRACKING_ID
     */
    public java.lang.String getTrackingId(){
         onPropGet(PROP_ID_trackingId);
         return _trackingId;
    }

    /**
     * 跟踪ID: TRACKING_ID
     */
    public void setTrackingId(java.lang.String value){
        if(onPropSet(PROP_ID_trackingId,value)){
            this._trackingId = value;
            internalClearRefs(PROP_ID_trackingId);
            
        }
    }
    
    /**
     * 当前航程ID: CURRENT_VOYAGE_ID
     */
    public java.lang.Long getCurrentVoyageId(){
         onPropGet(PROP_ID_currentVoyageId);
         return _currentVoyageId;
    }

    /**
     * 当前航程ID: CURRENT_VOYAGE_ID
     */
    public void setCurrentVoyageId(java.lang.Long value){
        if(onPropSet(PROP_ID_currentVoyageId,value)){
            this._currentVoyageId = value;
            internalClearRefs(PROP_ID_currentVoyageId);
            
        }
    }
    
    /**
     * 最后事件ID: LAST_EVENT_ID
     */
    public java.lang.Long getLastEventId(){
         onPropGet(PROP_ID_lastEventId);
         return _lastEventId;
    }

    /**
     * 最后事件ID: LAST_EVENT_ID
     */
    public void setLastEventId(java.lang.Long value){
        if(onPropSet(PROP_ID_lastEventId,value)){
            this._lastEventId = value;
            internalClearRefs(PROP_ID_lastEventId);
            
        }
    }
    
    /**
     * 最后已知位置ID: LAST_KNOWN_LOCATION_ID
     */
    public java.lang.Long getLastKnownLocationId(){
         onPropGet(PROP_ID_lastKnownLocationId);
         return _lastKnownLocationId;
    }

    /**
     * 最后已知位置ID: LAST_KNOWN_LOCATION_ID
     */
    public void setLastKnownLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_lastKnownLocationId,value)){
            this._lastKnownLocationId = value;
            internalClearRefs(PROP_ID_lastKnownLocationId);
            
        }
    }
    
    /**
     * 下一个预期位置ID: NEXT_EXPECTED_LOCATION_ID
     */
    public java.lang.Long getNextExpectedLocationId(){
         onPropGet(PROP_ID_nextExpectedLocationId);
         return _nextExpectedLocationId;
    }

    /**
     * 下一个预期位置ID: NEXT_EXPECTED_LOCATION_ID
     */
    public void setNextExpectedLocationId(java.lang.Long value){
        if(onPropSet(PROP_ID_nextExpectedLocationId,value)){
            this._nextExpectedLocationId = value;
            internalClearRefs(PROP_ID_nextExpectedLocationId);
            
        }
    }
    
    /**
     * 下一个预期航程ID: NEXT_EXPECTED_VOYAGE_ID
     */
    public java.lang.Long getNextExpectedVoyageId(){
         onPropGet(PROP_ID_nextExpectedVoyageId);
         return _nextExpectedVoyageId;
    }

    /**
     * 下一个预期航程ID: NEXT_EXPECTED_VOYAGE_ID
     */
    public void setNextExpectedVoyageId(java.lang.Long value){
        if(onPropSet(PROP_ID_nextExpectedVoyageId,value)){
            this._nextExpectedVoyageId = value;
            internalClearRefs(PROP_ID_nextExpectedVoyageId);
            
        }
    }
    
    /**
     * 出发地ID: ORIGIN_ID
     */
    public java.lang.Long getOriginId(){
         onPropGet(PROP_ID_originId);
         return _originId;
    }

    /**
     * 出发地ID: ORIGIN_ID
     */
    public void setOriginId(java.lang.Long value){
        if(onPropSet(PROP_ID_originId,value)){
            this._originId = value;
            internalClearRefs(PROP_ID_originId);
            
        }
    }
    
    /**
     * 指定目的地ID: SPEC_DESTINATION_ID
     */
    public java.lang.Long getSpecDestinationId(){
         onPropGet(PROP_ID_specDestinationId);
         return _specDestinationId;
    }

    /**
     * 指定目的地ID: SPEC_DESTINATION_ID
     */
    public void setSpecDestinationId(java.lang.Long value){
        if(onPropSet(PROP_ID_specDestinationId,value)){
            this._specDestinationId = value;
            internalClearRefs(PROP_ID_specDestinationId);
            
        }
    }
    
    /**
     * 指定出发地ID: SPEC_ORIGIN_ID
     */
    public java.lang.Long getSpecOriginId(){
         onPropGet(PROP_ID_specOriginId);
         return _specOriginId;
    }

    /**
     * 指定出发地ID: SPEC_ORIGIN_ID
     */
    public void setSpecOriginId(java.lang.Long value){
        if(onPropSet(PROP_ID_specOriginId,value)){
            this._specOriginId = value;
            internalClearRefs(PROP_ID_specOriginId);
            
        }
    }
    
    /**
     * 下一个预期航程
     */
    public app.demo.ddd.entity.Voyage getNextExpectedVoyage(){
       return (app.demo.ddd.entity.Voyage)internalGetRefEntity(PROP_NAME_nextExpectedVoyage);
    }

    public void setNextExpectedVoyage(app.demo.ddd.entity.Voyage refEntity){
   
           if(refEntity == null){
           
                   this.setNextExpectedVoyageId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_nextExpectedVoyage, refEntity,()->{
           
                           this.setNextExpectedVoyageId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 指定目的地
     */
    public app.demo.ddd.entity.Location getSpecDestination(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_specDestination);
    }

    public void setSpecDestination(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setSpecDestinationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_specDestination, refEntity,()->{
           
                           this.setSpecDestinationId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 出发地
     */
    public app.demo.ddd.entity.Location getOrigin(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_origin);
    }

    public void setOrigin(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setOriginId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_origin, refEntity,()->{
           
                           this.setOriginId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 最后事件
     */
    public app.demo.ddd.entity.HandlingEvent getLastEvent(){
       return (app.demo.ddd.entity.HandlingEvent)internalGetRefEntity(PROP_NAME_lastEvent);
    }

    public void setLastEvent(app.demo.ddd.entity.HandlingEvent refEntity){
   
           if(refEntity == null){
           
                   this.setLastEventId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_lastEvent, refEntity,()->{
           
                           this.setLastEventId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 最后已知位置
     */
    public app.demo.ddd.entity.Location getLastKnownLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_lastKnownLocation);
    }

    public void setLastKnownLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setLastKnownLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_lastKnownLocation, refEntity,()->{
           
                           this.setLastKnownLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 当前航程
     */
    public app.demo.ddd.entity.Voyage getCurrentVoyage(){
       return (app.demo.ddd.entity.Voyage)internalGetRefEntity(PROP_NAME_currentVoyage);
    }

    public void setCurrentVoyage(app.demo.ddd.entity.Voyage refEntity){
   
           if(refEntity == null){
           
                   this.setCurrentVoyageId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_currentVoyage, refEntity,()->{
           
                           this.setCurrentVoyageId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 下一个预期位置
     */
    public app.demo.ddd.entity.Location getNextExpectedLocation(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_nextExpectedLocation);
    }

    public void setNextExpectedLocation(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setNextExpectedLocationId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_nextExpectedLocation, refEntity,()->{
           
                           this.setNextExpectedLocationId(refEntity.getId());
                       
           });
           }
       
    }
       
    /**
     * 指定出发地
     */
    public app.demo.ddd.entity.Location getSpecOrigin(){
       return (app.demo.ddd.entity.Location)internalGetRefEntity(PROP_NAME_specOrigin);
    }

    public void setSpecOrigin(app.demo.ddd.entity.Location refEntity){
   
           if(refEntity == null){
           
                   this.setSpecOriginId(null);
               
           }else{
           internalSetRefEntity(PROP_NAME_specOrigin, refEntity,()->{
           
                           this.setSpecOriginId(refEntity.getId());
                       
           });
           }
       
    }
       
}
// resume CPD analysis - CPD-ON
