---
name: nop-orm-schema
description: 创建、修改、分析Nop平台中的orm模型
---

## What I do

### 理解Nop平台 ORM元模型
orm.xdef：
```xlang
<?xml version="1.0" encoding="UTF-8" ?>

<!--
-->
<orm x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
     xmlns:xdef="/nop/schema/xdef.xdef"
     xdef:name="OrmModel" xdef:bean-package="io.nop.orm.model"
     xdef:bean-extends-type="io.nop.xlang.xdsl.AbstractDslModel"
     displayName="string" version="string"
     xdef:default-extends="/nop/core/defaults/default.orm.xml">

    <description>string</description>

    <domains xdef:body-type="list" xdef:key-attr="name">
        <!--
        数据域定义。orm模型解析完毕之后，domain的定义会合并到column上。如果设置了domain是以domain的设置为准
        -->
        <domain name="!string" xdef:name="OrmDomainModel" displayName="string"
                stdDomain="std-domain" stdSqlType="!std-sql-type" stdDataType="std-data-type"
                precision="int" scale="int"/>
    </domains>

    <dicts xdef:body-type="list" xdef:key-attr="name">
        <dict name="!string" xdef:ref="dict.xdef" />
    </dicts>

    <packages xdef:body-type="list" xdef:key-attr="name">
        <!--
        package仅仅作为界面组织手段
        -->
        <package xdef:name="OrmPackageModel" name="!string" displayName="string">
            <comment>string</comment>
            <diagram>json</diagram>
            <entities>class-name-set</entities>
        </package>
    </packages>

    <entities xdef:body-type="list" xdef:key-attr="name">
        <entity name="!class-name" xdef:ref="entity.xdef" xdef:support-extends="true"/>
    </entities>
</orm>
```
entity.xdef：
```xlang
<?xml version="1.0" encoding="UTF-8"?>

<!--
 @name [实体名] 实体的名称，一般情况下与类名相同
 @displayName [显示名]
 @className [实体类名] 生成的实体类的类名
 @tableName [表名]
 @readonly [是否只读]
 @tableView [是否视图]
 @persistDriver [持久化存储机制] 缺省为jdbc，使用关系数据库存储
 @useShard [是否启用分库分表]
 @useWorkflow [是否启用工作流支持]
 @tagSet [自定义标签列表]
 @querySpace [查询空间]
 @shardProp [分区属性]
 @useGlobalCache [是否启用全局缓存]
 @useRevision [是否启用数据修订支持] 启用数据修订要求实体上具有nopRevType/nopRevBeginVer/nopRevEndVer等字段，
     当实体数据发生修改时不会直接修改原记录，而是会修改原记录的nopRevEndVer字段，并插入一条新的记录。
 @registerShortName [是否注册短名称] 一般情况下会自动注册实体类的短名称，从而使得eql语句中可以使用短类名，简化sql编写。
 @useTenant [是否启用租户]
 @tenantProp [租户id列] 租户id所对应的column的name。如果useTenant为true，tenantProp的缺省值为nopTenant。tenant字段一般不在主键集合中
 @useLogicalDelete [是否启用逻辑删除] 如果启用逻辑删除，则delete动作将设置deleteFlag属性为1，而不是发出delete语句。查询的时候并不会自动按照
   deleteFlag过滤，选择权交给调用者。
 @deleteFlagProp [逻辑删除属性]
 @deleteVersionProp [逻辑删除版本列] 逻辑删除时，为了保持唯一键索引的有效性，需要将deleteVersion设置为当前时间，而正常情况下则设置为0。
 @notGenCode [是否生成代码] 如果设置为true，则代码生成时将跳过本实体对象，不为它生成实体类。当我们引用其他模块的实体类时应该设置此属性，
 从而避免在本模块中生成其他模块的实体类。
 @versionProp [版本列的名称] version机制提供了内置的乐观锁实现。它的数据类型应该是long或者int。
 @kvTable [是否键值对表] 表示当前表作为key-value接口保存单个field对应的值。所谓的横表转纵表，就是把普通的table转换为kvTable。
      kvTable为true要求实体对象必须实现IOrmKeyValueTable接口
 @checkVersionWhenLazyLoad [是否在延迟加载后检查乐观锁版本] 延迟加载属性之后检查数据的乐观锁版本是否发生变化。
   如果发生变化，则抛出异常。因为乐观锁版本发生变化表示实体上的属性来自于两个版本的修改，从而破坏了数据一致性。
 @dimensionalType [数仓维度分类]
 @dbCatalog [数据库目录]
 @dbSchema [数据库模式]
 @dbPkName [数据库主键名]
 @creatorProp [创建者属性]
 @createTimeProp [创建时间属性]
 @updaterProp [更新者属性]
 @updateTimeProp [更新时间属性]
 @labelProp [标签属性]
 @stateProp [状态属性]
 @entityModeEnabled [是否启用实体模式]
 @noPrimaryKey [是否没有主键] 视图对象有可能没有主键，此时不支持延迟加载，所有字段都是eager字段，而且在session缓存里也没有记录
 -->
<entity name="!class-name" className="class-name" displayName="string" tableName="!string"
        readonly="!boolean=false" tableView="!boolean=false" kvTable="!boolean=false" querySpace="string=default"
        persistDriver="string=jdbc" tagSet="tag-set" noPrimaryKey="!boolean=false"
        useShard="!boolean=false" shardProp="var-name" useGlobalCache="!boolean=false"
        useTenant="!boolean=false" tenantProp="var-name"
        useWorkflow="!boolean=false" useRevision="!boolean=false"
        useLogicalDelete="!boolean=false" deleteFlagProp="var-name" deleteVersionProp="var-name"
        versionProp="var-name" dbCatalog="string" dbSchema="string" dbPkName="string"
        createrProp="var-name" createTimeProp="var-name" updaterProp="var-name" updateTimeProp="var-name"
        notGenCode="!boolean=false" registerShortName="!boolean=false"
        maxBatchLoadSize="int" checkVersionWhenLazyLoad="!boolean=true"
        labelProp="string" stateProp="var-name"
        entityModeEnabled="!boolean=false" dimensionalType="string"
        biz:moduleId="string"
        orm:mappingPropName1="string" orm:mappingPropName2="string"
        orm:mappingPropDisplayName1="string" orm:mappingPropDisplayName2="string"
        orm:bizKeyProp="string" orm:approverIdProp="string"
        xdef:default-extends="/nop/core/defaults/default.entity.xml"
        xdef:name="OrmEntityModel" xdef:bean-package="io.nop.orm.model"
        x:schema="/nop/schema/xdef.xdef" xdef:check-ns="ui,biz"
        xmlns:ui="ui" xmlns:biz="biz" xmlns:orm="orm"
        xmlns:xdef="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
>
    <!-- [SQL文本] 当实体实际对应数据库视图时，这里为视图的定义SQL语句 -->
    <sqlText>string</sqlText>

    <!-- [注释] -->
    <comment>string</comment>

    <filters xdef:body-type="list" xdef:key-attr="name">
        <filter name="!var-name" value="any" xdef:name="OrmEntityFilterModel"/>
    </filters>

    <!-- 每个column都对应实体上的一个属性。relation本身不创建新的对象属性，而是使用上的属性所覆盖 -->
    <columns xdef:body-type="list" xdef:key-attr="name">
        <!--
          column必须是原子数据类型，它对应于数据库中的字段。其他属性都根据column字段的值衍生而来。

          @name java实体属性名
          @code 数据库中的列名。code也必须是唯一的
          @propId 每个列都对应唯一的propId。生成代码的时候propId会编程PROP_ID_{propName}这样的常量定义。
            数据库中字段定义顺序与propId顺序一致。propId从1开始，与列的index并不一致，而且从兼容性考虑，propId之间有可能存在被忽略的值
            （例如某字段被从数据库删除，它对应的propId忽略，但是程序中已经使用的其他propId值不变）。当实体修改内容作为json格式保存到数据库中时，
            为减少存储空间，会按照[[propId,oldValue,newValue]]的格式进行存储。
          @primary [是否主键]
          @lazy [是否延迟加载] 如果设置为true，则装载实体时缺省情况下该字段会被延迟加载。clob和blob字段生成代码时会缺省设置为lazy=true
          @mandatory [是否非空] 如果设置为true，则不允许为null或者空字符串
          @sqlText 如果不为空，则表示是sql视图字段，字段为只读列，值不允许被修改。查询的时候会使用sqlText作为获取数据的表达式。
          @fixedValue 字段的值总是取固定值。例如通过一个大宽表来同时支持多个业务实体对象时，可以通过类型列来区分不同的实体类型
        -->
        <column name="!var-name" code="!string" propId="!int=0" displayName="string"
                insertable="!boolean=true" updatable="!boolean=true" jsonPath="string"
                domain="string" stdDomain="string" stdDataType="std-data-type" nativeSqlType="string"
                stdSqlType="!std-sql-type" precision="int" scale="int"
                primary="!boolean=false" fixedValue="string"
                lazy="!boolean=false" sqlText="string" defaultValue="string"
                comment="string" uiHint="string" notGenCode="!boolean=false"
                ui:show="string" ui:control="string" ui:rootLevel="string"
                tagSet="tag-set" mandatory="!boolean=false" xdef:name="OrmColumnModel">
        </column>
    </columns>

    <aliases xdef:body-type="list" xdef:key-attr="name">
        <!--
        关联表上的字段可以通过alias映射为主实体的属性，从而简化程序编写
        -->
        <alias name="!prop-name" displayName="string" propPath="!string" type="!generic-type"
               notGenCode="!boolean=false" tagSet="tag-set"
               xdef:name="OrmAliasModel"/>
    </aliases>

    <computes xdef:body-type="list" xdef:key-attr="name">
        <!--
        根据当前字段值计算得到的属性。在java对象上可以通过get/set方法来实现getter/setter，也可以通过这里的脚本配置来实现。
        compute的结果不会被自动缓存，每次访问都会重新计算。
        -->
        <compute name="!prop-name" displayName="string" type="generic-type" notGenCode="!boolean=false"
                 xdef:name="OrmComputePropModel" tagSet="tag-set" ui:control="string">
            <args xdef:body-type="list" xdef:key-attr="name">
                <arg name="!prop-name" displayName="string" type="!generic-type" xdef:name="OrmComputeArgModel"/>
            </args>
            <getter>xpl</getter>
            <setter>xpl</setter>
        </compute>
    </computes>

    <!--
       @refPropName [反向关联属性名]  reference在子表上定义，name为子表上对应主表实体的属性名。如果要求主表也反向关联子表，则
         refProp可以用来指定主表上反向关联子表的关联属性名。 例如name=parent, refProp=children
       @embedded 是否是嵌入在主实体中的关联属性。例如json字段，或者graphql远程存储等情况下
       @queryable 是否可以通过eql语法实现关联查询
       @autoCascadeDelete 是否在数据库层面自动实现级联删除
       @cascadeDelete 删除主表时是否自动删除子表
     -->
    <xdef:define xdef:name="OrmReferenceModel" name="!prop-name" displayName="string" persistDriver="string"
                 refEntityName="!class-name" refPropName="prop-name" refDisplayName="string"
                 queryable="!boolean=true" maxBatchLoadSize="int"
                 ui:control="string"
                 tagSet="tag-set" embedded="!boolean=false" notGenCode="!boolean=false"
                 cascadeDelete="!boolean=false" autoCascadeDelete="!boolean=false" xdef:bean-tag-prop="type"
    >
        <comment>string</comment>

        <!--
        必须是关联到相关实体的主键上
        -->
        <join xdef:body-type="list" xdef:default-override="replace">
            <on leftProp="prop-name" leftValue="any" rightProp="prop-name" rightValue="any"
                xdef:name="OrmJoinOnModel" xdef:allow-multiple="true"/>
        </join>

    </xdef:define>

    <relations xdef:body-type="list" xdef:key-attr="name" xdef:bean-child-name="relation"
               xdef:bean-body-type="List&lt;io.nop.orm.model.OrmReferenceModel>">
        <!--
         @constraint 外键约束名
         @ignoreDepends 在计算表的拓扑依赖顺序时，是否忽略对此关联表的依赖。出现循环依赖时需要进行标注。
         @reverseDepends 一般情况下to-one应该是子表指向父表。而在一对一关联时，reverseDepends=true表示是从父表指向子表。
         -->
        <to-one name="!prop-name" constraint="string" ignoreDepends="!boolean=false" reverseDepends="!boolean=false"
                xdef:name="OrmToOneReferenceModel" xdef:ref="OrmReferenceModel">
            <!--
            一对多的外键关联中父表对象可以存在集合属性来反向引用子表。集合对象可以定义keyProp，并支持排序条件。
            -->
            <ref-set keyProp="prop-name" xdef:name="OrmRefSetModel">
                <sort xdef:ref="/nop/schema/query/order-by.xdef"/>
            </ref-set>
        </to-one>

        <!--
         @keyProp 如果指定keyProp，则一对多集合中的元素以keyProp为唯一键，因此可以通过属性语法访问。
         例如children集合的keyProp为id, 则children.myElm表示访问children集合中id=myElm的元素。如果child实体设置了kvTable=true，
         则children.myElm实际返回的是children.findById("myElm").getFieldValue()
         @maxSize 如果大于0，则限制集合中的元素个数不能超过maxSize，否则会抛出异常
        -->
        <to-many name="!prop-name" keyProp="prop-name" useGlobalCache="!boolean=false"
                 maxSize="int"
                 xdef:name="OrmToManyReferenceModel" xdef:ref="OrmReferenceModel">
            <sort xdef:ref="/nop/schema/query/order-by.xdef"/>
        </to-many>
    </relations>

    <components xdef:body-type="list" xdef:key-attr="name">
        <!--
        多个字段可能构成一个component对象。component对象如果实现了IOrmComponent接口，则其结果可以被缓存。它的内部实现会自动实现与实体属性的同步
        @needFlush 如果needFlush为true，则保存到数据库之前需要调用IOrmComponent.flushToEntity将组件内部的变换更新到实体字段上
        -->
        <component xdef:name="OrmComponentModel" name="!string" displayName="string" className="!class-name"
                   notGenCode="!boolean=false" needFlush="!boolean=true" tagSet="tag-set"
                   xdef:body-type="list" xdef:key-attr="name" xdef:bean-body-prop="props">
            <prop name="!var-name" column="!var-name" xdef:name="OrmComponentPropModel"/>
        </component>
    </components>

    <unique-keys xdef:body-type="list" xdef:key-attr="name">
        <!-- 唯一键
         @constraint 唯一键在数据库中所对应的约束名
         @columns 逗号分隔的列名（name）的列表
         -->
        <unique-key xdef:name="OrmUniqueKeyModel" name="!string" displayName="string" columns="!csv-list"
                    constraint="string" tagSet="tag-set">
            <comment>string</comment>
        </unique-key>
    </unique-keys>

    <indexes xdef:body-type="list" xdef:key-attr="name">
        <!-- 索引
         @unique [是否唯一索引]
         @indexType [索引类型]
         @columns [列名列表]
         -->
        <index xdef:name="OrmIndexModel" name="!var-name" displayName="string" unique="boolean"
               indexType="string" comment="string" tagSet="tag-set">
            <!--
            @desc [是否降序]
            -->
            <column xdef:unique-attr="name" xdef:name="OrmIndexColumnModel" name="!var-name" desc="boolean"
                    xdef:mandatory="true"/>
        </index>
    </indexes>

</entity>
```
dict.xdef：
```xlang
<dict name="string" label="!string" valueType="string"
      locale="string" static="!boolean=false" tagSet="csv-set"
      deprecated="!boolean=false" internal="!boolean=false" normalized="!boolean=false"
      xdef:bean-class="io.nop.api.core.beans.DictBean"
      x:schema="/nop/schema/xdef.xdef" xmlns:x="/nop/schema/xdsl.xdef"
      xmlns:xdef="/nop/schema/xdef.xdef"
>
    <description>string</description>

    <option xdef:unique-attr="value" xdef:bean-class="io.nop.api.core.beans.DictOptionBean"
            label="!string" value="string" code="string" group="string" description="string"
            deprecated="!boolean=false" internal="!boolean=false" />
</dict>
```

### 根据示例创建业务需求的 ORM模型

XDef是XLang中的领域模型定义语法，类似于XSD或JSON Schema，但更加简洁直观：
- 与领域描述同形，schema的Tree结构与领域模型的Tree结构一致
- 所有集合元素通过xdef:key-attr定义唯一属性，确保每个节点有稳定的xpath路径
- 支持类型标注、默认值、描述注释等
- 自动支持差量分解和合并

**类型描述符格式**：
```
(!~#)?{stdDomain}:{options}={defaultValue}
```
- `!`：必填属性
- `~`：内部或废弃属性
- `#`：可使用编译期表达式
- `stdDomain`：标准数据类型
- `options`：额外参数（如枚举类型名）
- `defaultValue`：默认值

**节点定义类型**
1. **普通属性**：`name="!string"`
2. **集合节点**：`xdef:body-type="list"`
3. **唯一标识**：`xdef:key-attr="id"`
4. **节点复用**：`xdef:ref="WorkflowStepModel"`
5. **可扩展节点**：`xdef:define` + `xdef:ref`

**生成注意事项**
示例模型中，根节点上的 `ext:xxx` 属性很重要，如果上下文中没有相关信息，需要先查询项目中有没有可参考的示例，如果没有，需要使用 `question` tool 向用户提问。

ORM 模型示例：_nop-auth.orm.xml
```xlang
<?xml version="1.0" encoding="UTF-8" ?>
<orm ext:registerShortName="true" ext:appName="nop-auth" ext:entityPackageName="io.nop.auth.dao.entity"
     ext:basePackageName="io.nop.auth" ext:mavenGroupId="io.github.entropy-cloud" ext:mavenArtifactId="nop-auth"
     ext:mavenVersion="2.0.0-SNAPSHOT" ext:platformVersion="2.0.0-SNAPSHOT" ext:dialect="mysql,oracle,postgresql"
     x:schema="/nop/schema/orm/orm.xdef" xmlns:x="/nop/schema/xdsl.xdef" >

    <domains>
        <domain name="userName" precision="50" stdSqlType="VARCHAR"/>
        <domain name="image" precision="100" stdDomain="file" stdSqlType="VARCHAR"/>
        <domain name="email" precision="100" stdSqlType="VARCHAR"/>
        <domain name="phone" precision="100" stdSqlType="VARCHAR"/>
        <domain name="roleId" precision="100" stdSqlType="VARCHAR"/>
        <domain name="userId" precision="50" stdSqlType="VARCHAR"/>
        <domain name="deptId" precision="50" stdSqlType="VARCHAR"/>
        <domain name="boolFlag" stdSqlType="TINYINT"/>
        <domain name="xml-4k" precision="4000" stdSqlType="VARCHAR"/>
        <domain name="json-1000" precision="1000" stdDomain="json" stdSqlType="VARCHAR"/>
        <domain name="remark" precision="1000" stdSqlType="VARCHAR"/>
        <domain name="version" stdSqlType="INTEGER"/>
        <domain name="createTime" stdSqlType="TIMESTAMP"/>
        <domain name="createdBy" precision="50" stdSqlType="VARCHAR"/>
        <domain name="updateTime" stdSqlType="TIMESTAMP"/>
        <domain name="updatedBy" precision="50" stdSqlType="VARCHAR"/>
        <domain name="delFlag" stdDomain="boolFlag" stdSqlType="TINYINT"/>
        <domain name="roleIds" precision="250" stdSqlType="VARCHAR"/>
        <domain name="file" precision="100" stdDomain="file" stdSqlType="VARCHAR"/>
        <domain name="file-list" precision="500" stdDomain="file-list" stdSqlType="VARCHAR"/>
    </domains>

    <entities>
        <entity className="io.nop.auth.dao.entity.NopAuthUser" createTimeProp="createTime" createrProp="createdBy"
                deleteFlagProp="delFlag" displayName="用户" name="io.nop.auth.dao.entity.NopAuthUser"
                registerShortName="true" tableName="nop_auth_user" tagSet="mapper,no-tenant" updateTimeProp="updateTime"
                updaterProp="updatedBy" useLogicalDelete="true" versionProp="version" i18n-en:displayName="User">
            <columns>
                <column code="USER_ID" displayName="用户ID" domain="userId" mandatory="true" name="userId" precision="50"
                        primary="true" propId="1" stdDataType="string" stdSqlType="VARCHAR" tagSet="seq" ui:show="X"
                        i18n-en:displayName="User ID"/>
                <column code="USER_NAME" displayName="用户名" domain="userName" mandatory="true" name="userName"
                        precision="50" propId="2" stdDataType="string" stdSqlType="VARCHAR" tagSet="disp" ui:show="C"
                        i18n-en:displayName="User Name"/>
                <column code="PASSWORD" displayName="密码" mandatory="true" name="password" precision="80" propId="3"
                        stdDataType="string" stdSqlType="VARCHAR" tagSet="masked,var,not-pub" ui:show="X"
                        i18n-en:displayName="Password"/>
                <column code="SALT" displayName="密码加盐" name="salt" precision="32" propId="4" stdDataType="string"
                        stdSqlType="VARCHAR" tagSet="var,not-pub" ui:show="X" i18n-en:displayName="Salt"/>
                <column code="NICK_NAME" displayName="昵称" mandatory="true" name="nickName" precision="50" propId="5"
                        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Nick Name"/>
                <column code="DEPT_ID" displayName="所属部门" domain="deptId" name="deptId" precision="50" propId="6"
                        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Department"/>
                <column code="OPEN_ID" comment="允许动态改变的用户标识" displayName="用户外部标识" mandatory="true" name="openId"
                        precision="32" propId="7" stdDataType="string" stdSqlType="VARCHAR" tagSet="var" ui:show="L"
                        i18n-en:displayName="OpenId"/>
                <column code="REL_DEPT_ID" displayName="相关部门" domain="deptId" name="relDeptId" precision="50" propId="8"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Related Department"/>
                <column code="GENDER" displayName="性别" mandatory="true" name="gender" propId="9" stdDataType="int"
                        stdSqlType="INTEGER" i18n-en:displayName="Gender" ext:dict="auth/gender"/>
                <column code="AVATAR" displayName="头像" domain="image" name="avatar" precision="100" propId="10"
                        stdDataType="string" stdDomain="file" stdSqlType="VARCHAR" ui:show="X"
                        i18n-en:displayName="Avatar"/>
                <column code="EMAIL" displayName="邮件" domain="email" name="email" precision="100" propId="11"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Email"/>
                <column code="EMAIL_VERIFIED" defaultValue="0" displayName="邮件已验证" domain="boolFlag"
                        name="emailVerified" propId="12" stdDataType="byte" stdSqlType="TINYINT" ui:show="X"
                        i18n-en:displayName="Email Verified"/>
                <column code="PHONE" displayName="电话" domain="phone" name="phone" precision="50" propId="13"
                        stdDataType="string" stdSqlType="VARCHAR" i18n-en:displayName="Phone"/>
                <column code="PHONE_VERIFIED" defaultValue="0" displayName="电话已验证" domain="boolFlag"
                        name="phoneVerified" propId="14" stdDataType="byte" stdSqlType="TINYINT" ui:show="X"
                        i18n-en:displayName="Phone Verified"/>
                <column code="BIRTHDAY" displayName="生日" name="birthday" propId="15" stdDataType="date"
                        stdSqlType="DATE" ui:show="L" i18n-en:displayName="Birthday"/>
                <column code="USER_TYPE" displayName="用户类型" mandatory="true" name="userType" propId="16"
                        stdDataType="int" stdSqlType="INTEGER" i18n-en:displayName="User Type" ext:dict="auth/user-type"/>
                <column code="STATUS" displayName="用户状态" mandatory="true" name="status" propId="17" stdDataType="int"
                        stdSqlType="INTEGER" i18n-en:displayName="Status" ext:dict="auth/user-status"/>
                <column code="ID_TYPE" displayName="证件类型" name="idType" precision="10" propId="18" stdDataType="string"
                        stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="ID Type"/>
                <column code="ID_NBR" displayName="证件号" name="idNbr" precision="100" propId="19" stdDataType="string"
                        stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="ID Number"/>
                <column code="EXPIRE_AT" comment="临时用户过期后无法再登录" displayName="用户过期时间" name="expireAt" propId="20"
                        stdDataType="datetime" stdSqlType="DATETIME" ui:show="L" i18n-en:displayName="Expire At"/>
                <column code="PWD_UPDATE_TIME" comment="要求用户定期更换密码" displayName="上次密码更新时间" name="pwdUpdateTime"
                        propId="21" stdDataType="datetime" stdSqlType="DATETIME" ui:show="RL"
                        i18n-en:displayName="Password Update Time"/>
                <column code="CHANGE_PWD_AT_LOGIN" defaultValue="0" displayName="登陆后立刻修改密码" domain="boolFlag"
                        name="changePwdAtLogin" precision="1" propId="22" stdDataType="byte" stdSqlType="TINYINT"
                        ui:show="L" i18n-en:displayName="Change Password At Logon"/>
                <column code="REAL_NAME" displayName="真实姓名" name="realName" precision="50" propId="23"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Real Name"/>
                <column code="MANAGER_ID" displayName="上级" name="managerId" precision="50" propId="24"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Manager"/>
                <column code="WORK_NO" displayName="工号" name="workNo" precision="100" propId="25" stdDataType="string"
                        stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Work NO"/>
                <column code="POSITION_ID" displayName="职务" name="positionId" precision="32" propId="26"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Position"/>
                <column code="TELEPHONE" displayName="座机" name="telephone" precision="50" propId="27"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="L" i18n-en:displayName="Telephone"/>
                <column code="CLIENT_ID" displayName="设备ID" name="clientId" precision="100" propId="28"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="X" i18n-en:displayName="Client Id"/>
                <column code="DEL_FLAG" displayName="删除标识" domain="delFlag" mandatory="true" name="delFlag" propId="29"
                        stdDataType="byte" stdDomain="boolFlag" stdSqlType="TINYINT" ui:show="X"
                        i18n-en:displayName="Deleted"/>
                <column code="VERSION" displayName="数据版本" domain="version" mandatory="true" name="version" propId="30"
                        stdDataType="int" stdSqlType="INTEGER" ui:show="X" i18n-en:displayName="Version"/>
                <column code="TENANT_ID" displayName="租户ID" mandatory="true" name="tenantId" precision="32" propId="31"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="X" i18n-en:displayName="Tenant ID"/>
                <column code="CREATED_BY" displayName="创建人" domain="createdBy" mandatory="true" name="createdBy"
                        precision="50" propId="32" stdDataType="string" stdSqlType="VARCHAR" ui:show="L"
                        i18n-en:displayName="Created By"/>
                <column code="CREATE_TIME" displayName="创建时间" domain="createTime" mandatory="true" name="createTime"
                        propId="33" stdDataType="timestamp" stdSqlType="TIMESTAMP" ui:show="L"
                        i18n-en:displayName="Create Time"/>
                <column code="UPDATED_BY" displayName="修改人" domain="updatedBy" mandatory="true" name="updatedBy"
                        precision="50" propId="34" stdDataType="string" stdSqlType="VARCHAR" ui:show="L"
                        i18n-en:displayName="Updated By"/>
                <column code="UPDATE_TIME" displayName="修改时间" domain="updateTime" mandatory="true" name="updateTime"
                        propId="35" stdDataType="timestamp" stdSqlType="TIMESTAMP" ui:show="L"
                        i18n-en:displayName="Update Time"/>
                <column code="REMARK" displayName="备注" domain="remark" name="remark" precision="200" propId="36"
                        stdDataType="string" stdSqlType="VARCHAR" ui:show="SL" i18n-en:displayName="Remark"/>
            </columns>
            <relations>
                <to-one displayName="部门" name="dept" refDisplayName="部门用户"
                        refEntityName="io.nop.auth.dao.entity.NopAuthDept" refPropName="deptUsers" tagSet="pub,ref-pub"
                        i18n-en:displayName="Department" ref-i18n-en:displayName="Users">
                    <join>
                        <on leftProp="deptId" rightProp="deptId"/>
                    </join>
                </to-one>
                <to-one displayName="部门" name="relatedDept" refEntityName="io.nop.auth.dao.entity.NopAuthDept"
                        tagSet="pub,ref-pub" i18n-en:displayName="Related Department">
                    <join>
                        <on leftProp="relDeptId" rightProp="deptId"/>
                    </join>
                </to-one>
                <to-one displayName="岗位" name="position" refEntityName="io.nop.auth.dao.entity.NopAuthPosition"
                        tagSet="pub,ref-pub" i18n-en:displayName="Position">
                    <join>
                        <on leftProp="positionId" rightProp="positionId"/>
                    </join>
                </to-one>
                <to-one displayName="上级" name="manager" refEntityName="io.nop.auth.dao.entity.NopAuthUser"
                        tagSet="pub,ref-pub" i18n-en:displayName="Manager">
                    <join>
                        <on leftProp="managerId" rightProp="userId"/>
                    </join>
                </to-one>
                <to-many cascadeDelete="true" displayName="角色映射" name="roleMappings"
                         refEntityName="io.nop.auth.dao.entity.NopAuthUserRole" refPropName="user"
                         tagSet="pub,cascade-delete,insertable,updatable" i18n-en:displayName="RoleMappings">
                    <join>
                        <on leftProp="userId" rightProp="userId"/>
                    </join>
                </to-many>
                <to-many cascadeDelete="true" displayName="代理人映射" name="substitutionMappings"
                         refEntityName="io.nop.auth.dao.entity.NopAuthUserSubstitution" refPropName="user"
                         tagSet="pub,cascade-delete" i18n-en:displayName="Substitution Mappings">
                    <join>
                        <on leftProp="userId" rightProp="userId"/>
                    </join>
                </to-many>
                <to-many cascadeDelete="true" displayName="分组映射" name="groupMappings"
                         refEntityName="io.nop.auth.dao.entity.NopAuthGroupUser" refPropName="user"
                         tagSet="pub,cascade-delete,insertable,updatable" i18n-en:displayName="GroupMappings">
                    <join>
                        <on leftProp="userId" rightProp="userId"/>
                    </join>
                </to-many>
            </relations>
            <unique-keys>
                <unique-key columns="userName" constraint="UK_NOP_AUTH_USER_NAME" name="userNameKey"/>
            </unique-keys>
        </entity>
    </entities>
</orm>
```

## When to use me

当用户需要创建、修改、分析Nop平台中的orm模型时使用
