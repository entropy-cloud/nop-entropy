
CREATE TABLE nop_file_record(
  FILE_ID VARCHAR2(32) NOT NULL ,
  FILE_NAME VARCHAR2(300)  ,
  FILE_PATH VARCHAR2(2000) NOT NULL ,
  FILE_EXT VARCHAR2(50)  ,
  MIME_TYPE VARCHAR2(100) NOT NULL ,
  FILE_LENGTH NUMBER(20)  ,
  FILE_LAST_MODIFIED TIMESTAMP  ,
  BIZ_OBJ_NAME VARCHAR2(200)  ,
  BIZ_OBJ_ID VARCHAR2(200)  ,
  FIELD_NAME VARCHAR2(100)  ,
  FILE_HASH VARCHAR2(200)  ,
  DEL_FLAG SMALLINT NOT NULL ,
  CREATED_BY VARCHAR2(50) NOT NULL ,
  CREATE_TIME TIMESTAMP NOT NULL ,
  REMARK VARCHAR2(200)  ,
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
                    
      COMMENT ON COLUMN nop_file_record.DEL_FLAG IS '删除标识';
                    
      COMMENT ON COLUMN nop_file_record.CREATED_BY IS '创建人';
                    
      COMMENT ON COLUMN nop_file_record.CREATE_TIME IS '创建时间';
                    
      COMMENT ON COLUMN nop_file_record.REMARK IS '备注';
                    
