
CREATE TABLE voyage(
  id INT8 NOT NULL ,
  voyage_number VARCHAR(255)  ,
  constraint PK_voyage primary key (id)
);

CREATE TABLE location(
  id INT8 NOT NULL ,
  name VARCHAR(255) NOT NULL ,
  unlocode VARCHAR(255) NOT NULL ,
  constraint PK_location primary key (id)
);

CREATE TABLE cargo(
  id INT8 NOT NULL ,
  calculated_at TIMESTAMP  ,
  eta TIMESTAMP  ,
  unloaded_at_dest BOOLEAN  ,
  misdirected BOOLEAN  ,
  next_expected_handling_event_type VARCHAR(255)  ,
  routing_status VARCHAR(255)  ,
  transport_status VARCHAR(255)  ,
  spec_arrival_deadline TIMESTAMP NOT NULL ,
  tracking_id VARCHAR(255)  ,
  current_voyage_id INT8  ,
  last_event_id INT8  ,
  last_known_location_id INT8  ,
  next_expected_location_id INT8  ,
  next_expected_voyage_id INT8  ,
  origin_id INT8  ,
  spec_destination_id INT8  ,
  spec_origin_id INT8  ,
  constraint PK_cargo primary key (id)
);

CREATE TABLE carrier_movement(
  id INT8 NOT NULL ,
  arrival_time TIMESTAMP NOT NULL ,
  departure_time TIMESTAMP NOT NULL ,
  arrival_location_id INT8 NOT NULL ,
  departure_location_id INT8 NOT NULL ,
  voyage_id INT8  ,
  constraint PK_carrier_movement primary key (id)
);

CREATE TABLE handling_event(
  id INT8 NOT NULL ,
  completion_time TIMESTAMP  ,
  registration_time TIMESTAMP  ,
  type VARCHAR(255)  ,
  cargo_id INT8  ,
  location_id INT8  ,
  voyage_id INT8  ,
  constraint PK_handling_event primary key (id)
);

CREATE TABLE leg(
  id INT8 NOT NULL ,
  load_time TIMESTAMP  ,
  unload_time TIMESTAMP  ,
  load_location_id INT8  ,
  unload_location_id INT8  ,
  voyage_id INT8  ,
  cargo_id INT8  ,
  constraint PK_leg primary key (id)
);


      COMMENT ON TABLE voyage IS '航程';
                
      COMMENT ON COLUMN voyage.id IS 'Id';
                    
      COMMENT ON COLUMN voyage.voyage_number IS '航程号';
                    
      COMMENT ON TABLE location IS '位置';
                
      COMMENT ON COLUMN location.id IS 'Id';
                    
      COMMENT ON COLUMN location.name IS '名称';
                    
      COMMENT ON COLUMN location.unlocode IS 'UN编码';
                    
      COMMENT ON TABLE cargo IS '货物';
                
      COMMENT ON COLUMN cargo.id IS 'Id';
                    
      COMMENT ON COLUMN cargo.calculated_at IS '计算时间';
                    
      COMMENT ON COLUMN cargo.eta IS '预计到达时间';
                    
      COMMENT ON COLUMN cargo.unloaded_at_dest IS '目的地卸载时间';
                    
      COMMENT ON COLUMN cargo.misdirected IS '路线错误';
                    
      COMMENT ON COLUMN cargo.next_expected_handling_event_type IS '下一步预期处理事件类型';
                    
      COMMENT ON COLUMN cargo.routing_status IS '路由状态';
                    
      COMMENT ON COLUMN cargo.transport_status IS '运输状态';
                    
      COMMENT ON COLUMN cargo.spec_arrival_deadline IS '指定到达期限';
                    
      COMMENT ON COLUMN cargo.tracking_id IS '跟踪ID';
                    
      COMMENT ON COLUMN cargo.current_voyage_id IS '当前航程ID';
                    
      COMMENT ON COLUMN cargo.last_event_id IS '最后事件ID';
                    
      COMMENT ON COLUMN cargo.last_known_location_id IS '最后已知位置ID';
                    
      COMMENT ON COLUMN cargo.next_expected_location_id IS '下一个预期位置ID';
                    
      COMMENT ON COLUMN cargo.next_expected_voyage_id IS '下一个预期航程ID';
                    
      COMMENT ON COLUMN cargo.origin_id IS '出发地ID';
                    
      COMMENT ON COLUMN cargo.spec_destination_id IS '指定目的地ID';
                    
      COMMENT ON COLUMN cargo.spec_origin_id IS '指定出发地ID';
                    
      COMMENT ON TABLE carrier_movement IS '运输动作';
                
      COMMENT ON COLUMN carrier_movement.id IS 'Id';
                    
      COMMENT ON COLUMN carrier_movement.arrival_time IS '到达时间';
                    
      COMMENT ON COLUMN carrier_movement.departure_time IS '出发时间';
                    
      COMMENT ON COLUMN carrier_movement.arrival_location_id IS '到达地点ID';
                    
      COMMENT ON COLUMN carrier_movement.departure_location_id IS '出发地点ID';
                    
      COMMENT ON COLUMN carrier_movement.voyage_id IS '航程ID';
                    
      COMMENT ON TABLE handling_event IS '业务时间';
                
      COMMENT ON COLUMN handling_event.id IS 'Id';
                    
      COMMENT ON COLUMN handling_event.completion_time IS '完成时间';
                    
      COMMENT ON COLUMN handling_event.registration_time IS '注册时间';
                    
      COMMENT ON COLUMN handling_event.type IS '类型';
                    
      COMMENT ON COLUMN handling_event.cargo_id IS '货物ID';
                    
      COMMENT ON COLUMN handling_event.location_id IS '位置ID';
                    
      COMMENT ON COLUMN handling_event.voyage_id IS '航程ID';
                    
      COMMENT ON TABLE leg IS '航段';
                
      COMMENT ON COLUMN leg.id IS 'Id';
                    
      COMMENT ON COLUMN leg.load_time IS '装货时间';
                    
      COMMENT ON COLUMN leg.unload_time IS '卸货时间';
                    
      COMMENT ON COLUMN leg.load_location_id IS '装货地点';
                    
      COMMENT ON COLUMN leg.unload_location_id IS '卸货地点';
                    
      COMMENT ON COLUMN leg.voyage_id IS '航程ID';
                    
      COMMENT ON COLUMN leg.cargo_id IS '货物ID';
                    
