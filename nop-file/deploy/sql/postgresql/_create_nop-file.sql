
CREATE TABLE nop_file_record(
  FILE_ID VARCHAR(50) NOT NULL ,
  FILE_NAME VARCHAR(300)  ,
  FILE_PATH VARCHAR(2000) NOT NULL ,
  FILE_EXT VARCHAR(50)  ,
  MIME_TYPE VARCHAR(100) NOT NULL ,
  FILE_LENGTH INT8  ,
  FILE_LAST_MODIFIED TIMESTAMP  ,
  BIZ_OBJ_NAME VARCHAR(200)  ,
  BIZ_OBJ_ID VARCHAR(200)  ,
  FIELD_NAME VARCHAR(100)  ,
  FILE_HASH VARCHAR(200)  ,
  ORIGIN_FILE_ID VARCHAR(50) NOT NULL ,
  DEL_FLAG INT4 NOT NULL ,
  CREATED_BY VARCHAR(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR(200)  ,
  constraint PK_nop_file_record primary key (FILE_ID)
);


      COMMENT ON TABLE nop_file_record IS '文件记录';
                
      COMMENT ON COLUMN nop_file_record.FILE_ID IS '文件ID';
                    
      COMMENT ON COLUMN nop_file_record.FILE_NAME IS '文件名';
                    
      COMMENT ON COLUMN nop_file_record.FILE_PATH IS '文件路径';
                    
      COMMENT ON COLUMN nop_file_record.FILE_EXT IS '扩展名';
                    
      COMMENT ON COLUMN nop_file_record.MIME_TYPE IS '内容类型';
                    
      COMMENT ON COLUMN nop_file_record.FILE_LENGTH IS '文件长度';
                    
      COMMENT ON COLUMN nop_file_record.FILE_LAST_MODIFIED IS '文件修改时间';
                    
      COMMENT ON COLUMN nop_file_record.BIZ_OBJ_NAME IS '对象名';
                    
      COMMENT ON COLUMN nop_file_record.BIZ_OBJ_ID IS '对象ID';
                    
      COMMENT ON COLUMN nop_file_record.FIELD_NAME IS '字段名';
                    
      COMMENT ON COLUMN nop_file_record.FILE_HASH IS '文件摘要';
                    
      COMMENT ON COLUMN nop_file_record.ORIGIN_FILE_ID IS '原始文件ID';
                    
      COMMENT ON COLUMN nop_file_record.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_file_record.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_file_record.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_file_record.REMARK IS '备注';
                    
