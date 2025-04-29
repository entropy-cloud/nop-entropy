# AI设计的描述格式

```xml
<?xml version="1.0" encoding="UTF-8"?>
<backend-implementation>
  <!-- 结算日自动锁定服务配置 -->
  <service name="AutoLockService" type="scheduled">
    <trigger>
      <cron-expression>0 0 23 * * ?</cron-expression> <!-- 每天23点执行 -->
      <conditional>
        <check type="isSettlementDay" class="com.util.SettlementDayChecker"/>
      </conditional>
    </trigger>

    <execution-flow>
      <step order="1" name="IdentifyLockTargets">
        <dao-operation type="query">
          <sql><![CDATA[
                    SELECT DISTINCT business_type
                    FROM lock_config
                    WHERE auto_lock = true
                    ]]></sql>
        </dao-operation>
        <output-param name="targetTypes" type="list"/>
      </step>

      <step order="2" name="ProcessLocking" loop-over="targetTypes">
        <switch on="currentType">
          <case value="MENU">
            <dao-operation type="update">
              <sql><![CDATA[
                            UPDATE daily_menu
                            SET lock_status = 'LOCKED',
                                lock_time = NOW(),
                                lock_operator = 'SYSTEM_AUTO'
                            WHERE menu_date = :settlementDate
                            AND lock_status = 'UNLOCKED'
                            ]]></sql>
              <param name="settlementDate" source="#settlementDate"/>
            </dao-operation>
          </case>
          <case value="IN_OUT">
            <dao-operation type="batch-update">
              <sql><![CDATA[
                            UPDATE inventory_bill
                            SET lock_flag = 1,
                                lock_reason = '结算日自动锁定',
                                lock_time = NOW()
                            WHERE bill_date = :settlementDate
                            AND lock_flag = 0
                            ]]></sql>
              <batch-size>100</batch-size>
            </dao-operation>
          </case>
        </switch>
        <audit-log>
          <message>自动锁定${currentType}类型数据</message>
          <fields>
            <field name="date" source="#settlementDate"/>
            <field name="count" source="affectedRows"/>
          </fields>
        </audit-log>
      </step>

      <step order="3" name="PostLockActions">
        <service-call ref="NotificationService">
          <param name="message" value="结算日数据已自动锁定"/>
          <param name="target" value="FINANCE_DEPT"/>
        </service-call>
        <transaction-manager mode="commit"/>
      </step>
    </execution-flow>

    <exception-handling>
      <policy type="retry" max-attempts="3"/>
      <policy type="fallback" handler="LockFallbackHandler"/>
      <notification-channel>SYSTEM_ALERT</notification-channel>
    </exception-handling>
  </service>

  <!-- 依赖的配置数据 -->
  <config name="LockRules">
    <rule type="MENU" lock-days="1" description="每日菜单锁定"/>
    <rule type="IN_OUT" lock-days="3" description="出入库单据锁定"/>
    <rule type="INVENTORY" lock-days="7" description="库存记录锁定"/>
  </config>

  <!-- 数据库变更记录（DDL示例） -->
  <schema-changes>
    <table name="lock_records">
      <column name="lock_id" type="varchar(36)" pk="true"/>
      <column name="business_type" type="varchar(20)" index="true"/>
      <column name="business_id" type="varchar(36)" index="true"/>
      <column name="lock_time" type="datetime"/>
      <column name="lock_operator" type="varchar(50)"/>
      <column name="lock_reason" type="varchar(200)"/>
      <column name="is_auto" type="tinyint" default="0"/>
    </table>

    <table name="unlock_requests">
      <column name="request_id" type="varchar(36)" pk="true"/>
      <column name="lock_id" type="varchar(36)" fk="lock_records.lock_id"/>
      <column name="requester" type="varchar(50)"/>
      <column name="request_time" type="datetime"/>
      <column name="approval_status" type="varchar(20)"/>
    </table>
  </schema-changes>
</backend-implementation>
```

```xml
<!-- 新增内容 -->
<improvements>
  <!-- 1. 增加结算日配置检查 -->
  <addition xpath="/backend-implementation/service/execution-flow/step[1]">
    <step order="0.5" name="CheckSettlementConfig">
      <dao-operation type="query">
        <sql><![CDATA[
                SELECT lock_days FROM settlement_config
                WHERE org_id = :orgId
                ]]></sql>
        <param name="orgId" source="#currentOrgId"/>
      </dao-operation>
      <validation>
        <rule if="result.empty" error-code="CONFIG_MISSING"/>
      </validation>
    </step>
  </addition>

  <!-- 2. 精确锁定范围（仅当前服务公司数据） -->
  <modification xpath="/backend-implementation/service/execution-flow/step[2]/case[sql]">
    <sql><![CDATA[
        UPDATE daily_menu
        SET lock_status = 'LOCKED',
            lock_time = NOW(),
            lock_operator = 'SYSTEM_AUTO'
        WHERE menu_date = DATE_SUB(:settlementDate, INTERVAL :lockDays DAY)
        AND company_id = :currentCompanyId  <!-- 新增公司限制 -->
        AND lock_status = 'UNLOCKED'
        ]]></sql>
  </modification>

  <!-- 3. 增加数据锁定影响分析 -->
  <addition xpath="/backend-implementation/service/execution-flow/step[3]">
    <step order="2.5" name="ImpactAnalysis">
      <service-call ref="CostCalculateService" method="recalculate">
        <param name="startDate" source="#settlementDate"/>
        <param name="endDate" source="#settlementDate"/>
        <param name="companyId" source="#currentCompanyId"/>
      </service-call>
    </step>
  </addition>

  <!-- 4. 增加供应商结算关联锁定 -->
  <addition xpath="/backend-implementation/config/LockRules">
    <rule type="SUPPLIER_SETTLEMENT" lock-days="14"
          description="供应商结算单锁定"/>
  </addition>

  <!-- 5. 增强异常处理 -->
  <modification xpath="/backend-implementation/service/exception-handling">
    <policy type="partial-rollback" handler="PartialRollbackHandler">
      <exclude-types>MENU,IN_OUT</exclude-types>
    </policy>
    <notification-template>
      <subject>结算日锁定异常 - ${exceptionType}</subject>
      <body>锁定失败的服务公司：${companyName}</body>
    </notification-template>
  </modification>

  <!-- 移除内容 -->
  <removals>
    <!-- 简化过度的通用错误通知 -->
    <remove xpath="/backend-implementation/service/exception-handling/notification-channel"/>
  </removals>
</improvements>
```
