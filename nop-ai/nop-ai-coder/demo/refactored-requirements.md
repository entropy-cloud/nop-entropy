#  食堂物资管理系统需求文档

# 1 术语和缩略语
| 术语 | 说明 |
|-----|-----|
| 服务公司 | 学校食堂的经营单位 |
| 物资 | 食堂所用的各类食材 |
| 出入库 | 物资的入库和出库操作 |
| 盘库 | 库存盘点操作 |

# 2 需求概述
建设一套食堂物资管理系统，整合菜单管理、成本核算、订餐系统及物资出入库管理等环节，实现数据实时共享与精准分析，为食堂高效运营提供支撑。

# 3 系统功能需求

## 3.1 用户角色与权限
| 角色 | 权限 | 操作范围 |
|----|----|------|
| 学校管理员 | 基础数据维护、日常监管、成本核算、统计报表查看 | 全系统 |
| 服务公司 | 日常经营管理（菜单、出入库、经营数据、供应商结算） | 本公司数据 |

## 3.2 核心功能模块

### 3.2.1 基础数据管理(Priority:1,Effort:2)
学校管理员维护系统基础数据

####  子功能列表
1. 物资管理
2. 供应商管理
3. 服务公司管理
4. 基础菜单管理

### 3.2.2 经营管理(Priority:1,Effort:3)
服务公司完成日常经营数据管理

####  子功能列表
1. 库房管理
2. 每日菜单管理
3. 出入库管理
4. 盘库管理
5. 供应商结算
6. 经营数据管理

# 4 核心数据库表
| 表名 | 中文名 | 说明 | 主要字段 |
|----|-----|----|------|
| material | 物资表 | 存储物资基础信息 | id,name,category,code,unit,spec,brand,image,remark |
| supplier | 供应商表 | 存储供应商信息 | id,name,code,credit_code,contact,status,intro,remark |
| service_company | 服务公司表 | 存储服务公司信息 | id,name,code,credit_code,contact,status,intro,remark |
| base_menu | 基础菜单表 | 存储基础菜单信息 | id,name,type,image,ingredients,remark |
| daily_menu | 每日菜单表 | 存储每日菜单信息 | id,date,menu_id,company_id |
| warehouse | 库房表 | 存储库房信息 | id,name,company_id,remark |
| stock_in_out | 出入库表 | 存储出入库记录 | id,type,date,warehouse_id,handler,supplier_id,total_amount,paid_amount,attachments,locked |
| stock_detail | 出入库明细表 | 存储出入库明细 | id,stock_id,material_id,price,quantity,amount |
| inventory | 库存表 | 存储当前库存 | id,material_id,warehouse_id,quantity |
| business_data | 经营数据表 | 存储经营数据 | id,date,company_id,meal_count,fixed_cost,daily_deviation |

# 5 接口设计

## 5.1 依赖的REST服务接口
| 服务对象名 | 说明 |
|-------|----|
| 订餐系统API | 获取每日订餐人数数据 |

## 5.2 提供的REST服务接口
| 服务对象名 | 说明 |
|-------|----|
| 物资查询API | 提供物资基础数据查询 |
| 库存查询API | 提供实时库存查询 |

# 6 非功能需求

## 6.1 性能需求
1. 系统支持100并发用户操作
2. 关键查询响应时间<2秒

## 6.2 安全需求
1. 敏感数据加密存储
2. 操作日志完整记录

## 6.3 合规需求
1. 符合食品安全数据管理要求
2. 财务数据保留5年以上

## 6.4 其他需求
1. 支持移动端访问关键功能
2. 关键操作需二次确认

# 7 原始需求中的额外要求
1. 物资分类采用多级分类（如蔬菜类、水果类等）
2. 已禁用的供应商不出现在选择列表中，但保留历史数据
3. 已禁用的服务公司不能登录，但保留历史数据
4. 菜单配料需包含材料编码、名称、单位、数量、成本单价
5. 已纳入结算日的每日菜单不能编辑删除
6. 出入库操作可锁定数据，锁定后不能编辑删除
7. 出库操作可从入库单整入整出
8. 选择物资时可查看库存数据
9. 订餐人数可从第三方API读取或手工录入

