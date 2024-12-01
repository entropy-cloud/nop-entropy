
CREATE TABLE voyage(
  ID NUMBER(20) NOT NULL ,
  VOYAGE_NUMBER VARCHAR2(255)  ,
  constraint PK_voyage primary key (ID)
);

CREATE TABLE location(
  ID NUMBER(20) NOT NULL ,
  NAME VARCHAR2(255) NOT NULL ,
  UNLOCODE VARCHAR2(255) NOT NULL ,
  constraint PK_location primary key (ID)
);

CREATE TABLE cargo(
  ID NUMBER(20) NOT NULL ,
  CALCULATED_AT DATE  ,
  ETA DATE  ,
  UNLOADED_AT_DEST CHAR(1)  ,
  MISDIRECTED CHAR(1)  ,
  NEXT_EXPECTED_HANDLING_EVENT_TYPE VARCHAR2(255)  ,
  ROUTING_STATUS VARCHAR2(255)  ,
  TRANSPORT_STATUS VARCHAR2(255)  ,
  SPEC_ARRIVAL_DEADLINE DATE NOT NULL ,
  TRACKING_ID VARCHAR2(255)  ,
  CURRENT_VOYAGE_ID NUMBER(20)  ,
  LAST_EVENT_ID NUMBER(20)  ,
  LAST_KNOWN_LOCATION_ID NUMBER(20)  ,
  NEXT_EXPECTED_LOCATION_ID NUMBER(20)  ,
  NEXT_EXPECTED_VOYAGE_ID NUMBER(20)  ,
  ORIGIN_ID NUMBER(20)  ,
  SPEC_DESTINATION_ID NUMBER(20)  ,
  SPEC_ORIGIN_ID NUMBER(20)  ,
  constraint PK_cargo primary key (ID)
);

CREATE TABLE carrier_movement(
  ID NUMBER(20) NOT NULL ,
  ARRIVAL_TIME DATE NOT NULL ,
  DEPARTURE_TIME DATE NOT NULL ,
  ARRIVAL_LOCATION_ID NUMBER(20) NOT NULL ,
  DEPARTURE_LOCATION_ID NUMBER(20) NOT NULL ,
  VOYAGE_ID NUMBER(20)  ,
  constraint PK_carrier_movement primary key (ID)
);

CREATE TABLE handling_event(
  ID NUMBER(20) NOT NULL ,
  COMPLETION_TIME DATE  ,
  REGISTRATION_TIME DATE  ,
  TYPE VARCHAR2(255)  ,
  CARGO_ID NUMBER(20)  ,
  LOCATION_ID NUMBER(20)  ,
  VOYAGE_ID NUMBER(20)  ,
  constraint PK_handling_event primary key (ID)
);

CREATE TABLE leg(
  ID NUMBER(20) NOT NULL ,
  LOAD_TIME DATE  ,
  UNLOAD_TIME DATE  ,
  LOAD_LOCATION_ID NUMBER(20)  ,
  UNLOAD_LOCATION_ID NUMBER(20)  ,
  VOYAGE_ID NUMBER(20)  ,
  CARGO_ID NUMBER(20)  ,
  constraint PK_leg primary key (ID)
);


      COMMENT ON TABLE voyage IS '航程';
                
      COMMENT ON COLUMN voyage.ID IS 'Id';
                    
      COMMENT ON COLUMN voyage.VOYAGE_NUMBER IS '航程号';
                    
      COMMENT ON TABLE location IS '位置';
                
      COMMENT ON COLUMN location.ID IS 'Id';
                    
      COMMENT ON COLUMN location.NAME IS '名称';
                    
      COMMENT ON COLUMN location.UNLOCODE IS 'UN编码';
                    
      COMMENT ON TABLE cargo IS '货物';
                
      COMMENT ON COLUMN cargo.ID IS 'Id';
                    
      COMMENT ON COLUMN cargo.CALCULATED_AT IS '计算时间';
                    
      COMMENT ON COLUMN cargo.ETA IS '预计到达时间';
                    
      COMMENT ON COLUMN cargo.UNLOADED_AT_DEST IS '目的地卸载时间';
                    
      COMMENT ON COLUMN cargo.MISDIRECTED IS '路线错误';
                    
      COMMENT ON COLUMN cargo.NEXT_EXPECTED_HANDLING_EVENT_TYPE IS '下一步预期处理事件类型';
                    
      COMMENT ON COLUMN cargo.ROUTING_STATUS IS '路由状态';
                    
      COMMENT ON COLUMN cargo.TRANSPORT_STATUS IS '运输状态';
                    
      COMMENT ON COLUMN cargo.SPEC_ARRIVAL_DEADLINE IS '指定到达期限';
                    
      COMMENT ON COLUMN cargo.TRACKING_ID IS '跟踪ID';
                    
      COMMENT ON COLUMN cargo.CURRENT_VOYAGE_ID IS '当前航程ID';
                    
      COMMENT ON COLUMN cargo.LAST_EVENT_ID IS '最后事件ID';
                    
      COMMENT ON COLUMN cargo.LAST_KNOWN_LOCATION_ID IS '最后已知位置ID';
                    
      COMMENT ON COLUMN cargo.NEXT_EXPECTED_LOCATION_ID IS '下一个预期位置ID';
                    
      COMMENT ON COLUMN cargo.NEXT_EXPECTED_VOYAGE_ID IS '下一个预期航程ID';
                    
      COMMENT ON COLUMN cargo.ORIGIN_ID IS '出发地ID';
                    
      COMMENT ON COLUMN cargo.SPEC_DESTINATION_ID IS '指定目的地ID';
                    
      COMMENT ON COLUMN cargo.SPEC_ORIGIN_ID IS '指定出发地ID';
                    
      COMMENT ON TABLE carrier_movement IS '运输动作';
                
      COMMENT ON COLUMN carrier_movement.ID IS 'Id';
                    
      COMMENT ON COLUMN carrier_movement.ARRIVAL_TIME IS '到达时间';
                    
      COMMENT ON COLUMN carrier_movement.DEPARTURE_TIME IS '出发时间';
                    
      COMMENT ON COLUMN carrier_movement.ARRIVAL_LOCATION_ID IS '到达地点ID';
                    
      COMMENT ON COLUMN carrier_movement.DEPARTURE_LOCATION_ID IS '出发地点ID';
                    
      COMMENT ON COLUMN carrier_movement.VOYAGE_ID IS '航程ID';
                    
      COMMENT ON TABLE handling_event IS '业务时间';
                
      COMMENT ON COLUMN handling_event.ID IS 'Id';
                    
      COMMENT ON COLUMN handling_event.COMPLETION_TIME IS '完成时间';
                    
      COMMENT ON COLUMN handling_event.REGISTRATION_TIME IS '注册时间';
                    
      COMMENT ON COLUMN handling_event.TYPE IS '类型';
                    
      COMMENT ON COLUMN handling_event.CARGO_ID IS '货物ID';
                    
      COMMENT ON COLUMN handling_event.LOCATION_ID IS '位置ID';
                    
      COMMENT ON COLUMN handling_event.VOYAGE_ID IS '航程ID';
                    
      COMMENT ON TABLE leg IS '航段';
                
      COMMENT ON COLUMN leg.ID IS 'Id';
                    
      COMMENT ON COLUMN leg.LOAD_TIME IS '装货时间';
                    
      COMMENT ON COLUMN leg.UNLOAD_TIME IS '卸货时间';
                    
      COMMENT ON COLUMN leg.LOAD_LOCATION_ID IS '装货地点';
                    
      COMMENT ON COLUMN leg.UNLOAD_LOCATION_ID IS '卸货地点';
                    
      COMMENT ON COLUMN leg.VOYAGE_ID IS '航程ID';
                    
      COMMENT ON COLUMN leg.CARGO_ID IS '货物ID';
                    
