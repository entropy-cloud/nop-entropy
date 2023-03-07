drop table if exists sys_order;

drop table if exists sys_customer;

drop table if exists sys_user;


-- 常规测试
CREATE TABLE sys_user (
   ID int NOT NULL ,
   CODE  varchar(16) DEFAULT NULL,
   PRIMARY KEY ( ID )
) ;

insert into  sys_user values (1,'用户一');
insert into  sys_user values (2,'用户二');
insert into    sys_user values (3,'用户三');

-- orm 测试的

CREATE TABLE sys_customer (
   ID int NOT NULL ,
   CODE  varchar(16) DEFAULT NULL,
   NAME  varchar(16) DEFAULT NULL,
   PRIMARY KEY ( ID )
) ;

insert into  sys_customer values (1,'a','客户一');
insert into  sys_customer values (2,'b','客户二');
insert into  sys_customer values (3,'c','客户三');


CREATE TABLE sys_order (
     ID int NOT NULL ,
     NAME  varchar(16) DEFAULT NULL,
     customer_id int ,
   PRIMARY KEY ( ID )
) ;

insert into  sys_order values (1,'a',1);
insert into  sys_order values (2,'b',1);
insert into  sys_order values (3,'c',2);
insert into  sys_order values (4,'d',2);

