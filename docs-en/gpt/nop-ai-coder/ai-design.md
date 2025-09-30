# AI Design Description Format

```xml
<?xml version="1.0" encoding="UTF-8"?>
<backend-implementation>
  <!-- Settlement day auto-lock service configuration -->
  <service name="AutoLockService" type="scheduled">
    <trigger>
      <cron-expression>0 0 23 * * ?</cron-expression> <!-- Runs daily at 23:00 -->
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
                                lock_reason = 'Settlement day auto lock',
                                lock_time = NOW()
                            WHERE bill_date = :settlementDate
                            AND lock_flag = 0
                            ]]></sql>
              <batch-size>100</batch-size>
            </dao-operation>
          </case>
        </switch>
        <audit-log>
          <message>Automatically lock data of type ${currentType}</message>
          <fields>
            <field name="date" source="#settlementDate"/>
            <field name="count" source="affectedRows"/>
          </fields>
        </audit-log>
      </step>

      <step order="3" name="PostLockActions">
        <service-call ref="NotificationService">
          <param name="message" value="Settlement day data has been auto-locked"/>
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

  <!-- Dependent configuration data -->
  <config name="LockRules">
    <rule type="MENU" lock-days="1" description="Daily menu lock"/>
    <rule type="IN_OUT" lock-days="3" description="In/Out inventory bills lock"/>
    <rule type="INVENTORY" lock-days="7" description="Inventory records lock"/>
  </config>

  <!-- Database change log (DDL example) -->
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
<!-- New content -->
<improvements>
  <!-- 1. Add settlement day configuration check -->
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

  <!-- 2. Narrow locking scope (only data of the current service company) -->
  <modification xpath="/backend-implementation/service/execution-flow/step[2]/case[sql]">
    <sql><![CDATA[
        UPDATE daily_menu
        SET lock_status = 'LOCKED',
            lock_time = NOW(),
            lock_operator = 'SYSTEM_AUTO'
        WHERE menu_date = DATE_SUB(:settlementDate, INTERVAL :lockDays DAY)
        AND company_id = :currentCompanyId  <!-- Added company constraint -->
        AND lock_status = 'UNLOCKED'
        ]]></sql>
  </modification>

  <!-- 3. Add impact analysis of data locking -->
  <addition xpath="/backend-implementation/service/execution-flow/step[3]">
    <step order="2.5" name="ImpactAnalysis">
      <service-call ref="CostCalculateService" method="recalculate">
        <param name="startDate" source="#settlementDate"/>
        <param name="endDate" source="#settlementDate"/>
        <param name="companyId" source="#currentCompanyId"/>
      </service-call>
    </step>
  </addition>

  <!-- 4. Add supplier settlement-related lock -->
  <addition xpath="/backend-implementation/config/LockRules">
    <rule type="SUPPLIER_SETTLEMENT" lock-days="14"
          description="Supplier settlement orders lock"/>
  </addition>

  <!-- 5. Enhance exception handling -->
  <modification xpath="/backend-implementation/service/exception-handling">
    <policy type="partial-rollback" handler="PartialRollbackHandler">
      <exclude-types>MENU,IN_OUT</exclude-types>
    </policy>
    <notification-template>
      <subject>Settlement day lock exception - ${exceptionType}</subject>
      <body>Service company failed to lock: ${companyName}</body>
    </notification-template>
  </modification>

  <!-- Removed content -->
  <removals>
    <!-- Simplify overly generic error notification -->
    <remove xpath="/backend-implementation/service/exception-handling/notification-channel"/>
  </removals>
</improvements>
```
<!-- SOURCE_MD5:6eaaa1e064f7d402224477f9a213984d-->
