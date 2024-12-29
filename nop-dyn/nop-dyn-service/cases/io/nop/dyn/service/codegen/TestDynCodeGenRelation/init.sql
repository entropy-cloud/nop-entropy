drop table user_entity if exists;
drop table role_entity if exists;
drop table user_many_role if exists;

CREATE TABLE USER_ENTITY(
                  SID VARCHAR(32)   COMMENT '主键ID' ,
                  USER_NAME VARCHAR(100)   COMMENT '用户名' ,
                  USER_AGE INTEGER   default '1'  COMMENT '用户年龄' ,
                  ROLE_ID VARCHAR(200)   COMMENT '角色 ID' ,
                  STATUS INTEGER   default '1'  COMMENT '状态' ,
                  VERSION INTEGER   COMMENT '数据版本' ,
                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,
                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,
                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,
                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,
                  constraint PK_USER_ENTITY_ID primary key (sid)
                );

INSERT INTO USER_ENTITY (sid, user_name, user_age, role_id, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES ('1', '小明', 1, '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00'),('2', '小李', 200, '123', 1, 1, '小李', '2021-09-01 00:00:00', '小李', '2021-09-01 00:00:00');

CREATE TABLE ROLE_ENTITY(
                  SID VARCHAR(32)   COMMENT '主键ID' ,
                  ROLE_NAME VARCHAR(100)   COMMENT '角色名称' ,
                  ROLE_KEY VARCHAR(100)   COMMENT '角色 key' ,
                  STATUS INTEGER   default '1'  COMMENT '状态' ,
                  VERSION INTEGER   COMMENT '数据版本' ,
                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,
                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,
                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,
                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,
                  constraint PK_ROLE_ENTITY_ID primary key (sid)
                );

INSERT INTO ROLE_ENTITY (sid, role_name, role_key, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES  ('123', '开发角色2', '1', 1, 1, 'development', '2021-09-01 00:00:00', 'development', '2021-09-01 00:00:00');

CREATE TABLE USER_MANY_ROLE(
                  SID VARCHAR(32)   COMMENT '主键ID' ,
                  USER_ID VARCHAR(32)   COMMENT '用户 ID' ,
                  ROLE_ID VARCHAR(32)   COMMENT '角色 ID' ,
                  STATUS INTEGER   default '1'  COMMENT '状态' ,
                  VERSION INTEGER   COMMENT '数据版本' ,
                  CREATED_BY VARCHAR(50)   COMMENT '创建人' ,
                  CREATE_TIME TIMESTAMP   COMMENT '创建时间' ,
                  UPDATED_BY VARCHAR(50)   COMMENT '修改人' ,
                  UPDATE_TIME TIMESTAMP   COMMENT '修改时间' ,
                  constraint PK_USER_MANY_ROLE_ID primary key (user_id, role_id)
                );

INSERT INTO USER_MANY_ROLE (sid, user_id, role_id, STATUS, VERSION, CREATED_BY, CREATE_TIME, UPDATED_BY, UPDATE_TIME) VALUES ('1233123', '1', '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00'),('1412414', '255', '123', 1, 1, '小明', '2021-09-01 00:00:00', '小明', '2021-09-01 00:00:00');

