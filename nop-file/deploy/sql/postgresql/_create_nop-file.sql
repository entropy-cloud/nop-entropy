
CREATE TABLE nop_file_record(
  file_id VARCHAR(50) NOT NULL ,
  file_name VARCHAR(300)  ,
  file_path VARCHAR(2000) NOT NULL ,
  file_ext VARCHAR(50)  ,
  mime_type VARCHAR(100) NOT NULL ,
  file_length INT8  ,
  file_last_modified TIMESTAMP  ,
  biz_obj_name VARCHAR(200)  ,
  biz_obj_id VARCHAR(200)  ,
  field_name VARCHAR(100)  ,
  file_hash VARCHAR(200)  ,
  origin_file_id VARCHAR(50) NOT NULL ,
  is_public BOOLEAN NOT NULL ,
  del_flag INT4 NOT NULL ,
  created_by VARCHAR(50) NOT NULL ,
  create_time TIMESTAMP NOT NULL ,
  remark VARCHAR(200)  ,
  constraint PK_nop_file_record primary key (file_id)
);


      COMMENT ON TABLE nop_file_record IS '文件记录';
                
      COMMENT ON COLUMN nop_file_record.file_id IS '文件ID';
                    
      COMMENT ON COLUMN nop_file_record.file_name IS '文件名';
                    
      COMMENT ON COLUMN nop_file_record.file_path IS '文件路径';
                    
      COMMENT ON COLUMN nop_file_record.file_ext IS '扩展名';
                    
      COMMENT ON COLUMN nop_file_record.mime_type IS '内容类型';
                    
      COMMENT ON COLUMN nop_file_record.file_length IS '文件长度';
                    
      COMMENT ON COLUMN nop_file_record.file_last_modified IS '文件修改时间';
                    
      COMMENT ON COLUMN nop_file_record.biz_obj_name IS '对象名';
                    
      COMMENT ON COLUMN nop_file_record.biz_obj_id IS '对象ID';
                    
      COMMENT ON COLUMN nop_file_record.field_name IS '字段名';
                    
      COMMENT ON COLUMN nop_file_record.file_hash IS '文件摘要';
                    
      COMMENT ON COLUMN nop_file_record.origin_file_id IS '原始文件ID';
                    
      COMMENT ON COLUMN nop_file_record.is_public IS '是否允许公开访问';
                    
      COMMENT ON COLUMN nop_file_record.del_flag IS '删除标识';
                    
      COMMENT ON COLUMN nop_file_record.created_by IS '创建人';
                    
      COMMENT ON COLUMN nop_file_record.create_time IS '创建时间';
                    
      COMMENT ON COLUMN nop_file_record.remark IS '备注';
                    
