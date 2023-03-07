# 配置
* registerShortName	:实体名为包含包名的全类名，如果registerShortName设置为true，则也可以通过去除包名的短类名来访问实体
* appName：	所有子模块的前缀名，格式应该为 xxx-yyy，例如nop-sys，它会自动成为两级目录名
* entityPackageName：	实体对象所在的包名，一般应该是xxx.dao.entity，例如io.nop.sys.dao.entity	
* basePackageName:	所有子模块的父包名, 例如 io.nop.sys
* maven.groupId	：	主工程的groupId
* maven.artifactId：	主工程的artifactId
* maven.version	： 主工程以及所有子模块的版本号
* platformVersion: Nop平台的版本号
* dialect: 生成对应数据库的建表语句，可以是逗号分隔的多个名称，例如 mysql,oracle,postgresql	


# 数据域
数据域(domain)的概念类似于PowerDesigner设计工具中的domain概念，它可以为反复出现的、具有一定业务含义的字段定义提供一个可以被复用的名称。

1. 一般情况下设置了数据表中字段的类型定义应该与指定的数据域保持一致。例如数据域json-1000的数据类型为VARCHAR，长度为1000，则使用了这个数据域的字段也应该设置类型为VARCHAR，长度为1000。如果不一致，则模型验证的时候会提示。

2. 标准域是Nop平台中在StdDomainRegistry中注册的标准业务数据类型，例如var-name表示必须是合法的Java变量名等。

3. 代码生成的时候会特殊识别如下数据域: version, createTime, createdBy,updateTime, updatedBy,delFlag,tenantId。标记了这些数据域的字段会被自动识别为ORM引擎所支持的乐观锁字段、创建时间字段等。

4. 生成meta文件的时候，会自动生成为schema节点的domain属性。在前台布局引擎中会自动映射到control.xlib中定义的显示和编辑控件。例如 domain=phone会对应于控件`<edit-phone/>`


# 字典表

## 数据表作为字典表
* 在数据表上增加标签dict，表示它可以作为字典表来使用。字典表的数据量不应该很大，一般会使用下拉列表来显示。
* 然后通过sys/{objName}来引用这个数据表来作为字典。例如 sys/LitemallBrand

## 在【字典定义】中增加字典表
这里定义的字典表在生成代码的时候会生成静态字典文件到`{appName}-dao/src/main/resources/_vfs/nop/dict`目录下。

* 名称： 字典表的引用名称，例如 mall/order-status
* 中文名、英文名： 字典的显示名称
* 值类型： 字典值的类型，一般是int或者string

字典项配置中：
* 值： 字典项的value
* 名称： 字典项的label
* 代码： 如果配置了这个属性，则会在DaoConstants常量类中生成一个常量定义，例如mall/order-status中CREATED代码对应于生成 
```java
public interface _AppMallDaoConstants {
    
    /**
     * 订单状态: 未付款 
     */
    int ORDER_STATUS_CREATED = 101;
    ...
}    
```

# 数据表
表名会直接按照驼峰规则映射为GraphQL中的对象名，所以表名原则上需要全局唯一（不同模块之间的表名不应该冲突）。建议每个模块具有自己特殊的表名前缀，比如`litemall_xxx`,`nop_wf_xxx`等。
Nop平台内置的表名都具有前缀`nop_`。

## 数据表标签
* dict: 标记为字典表，其他地方可以通过obj/{objName}来将该表的数据作为字典表来使用
* mapper: 为该表生成类似MyBatis的Mapper定义文件和Mapper接口类。
* no-web: 后台使用的数据对象，不为它单独生成前台页面入口

## 字段标签
* seq： 利用SequenceGenerator来自动生成主键。缺省使用nop_sys_sequence表来记录sequence。
* var: 表示随机生成的变量。在自动化测试框架中，这个字段将被记录为变量，录制到数据文件中时会被替换为变量名保存。例如NopAuthUser表的userId字段，新建用户时自动录制的nop_auth_user.csv中，userId列的值为 `@var:NopAuthUser@userId`

* disp: 数据记录的显示名称。在字典表或者选择列表中会作为label被使用。
* masked： 表示打印到log中时需要掩码
* not-pub： 表示不会返回到前台。
* sort： 表示列表的缺省排序字段, sort-desc表示缺省按照此字段的降序排序。生成meta的时候对应于orderBy段。

## 字段显示
代码生成的时候会在view.xml页面结构文件中自动生成列表和表单布局定义。列表缺省按照模型中的字段顺序显示。表单布局如果字段个数超过10个，则按照两列显示，否则按照一列显示。

为了减少手工调整view.xml的工作量，可以在模型中设置最常用的一些显示控制

* X表示在界面上不显示，由程序内部使用， 
* R表示只读字段，
* C表示不允许修改但允许插入，
* S表示占据单行显示，
* L表示在列表上不显示

## 字段数据类型
必须是StdSqlType中定义的类型，包括BOOLEAN, TINYINT, INTEGER, BIGINT, CHAR, VARCHAR, DATE, DATETIME, TIMESTAMP, DECIMAL, FLOAT, DOUBLE等。

## 关联属性标签
一般情况下我们只定义to-one类型的关联对象，它的左对象对应于当前表，关联对象对应于父表。join的左属性对应于外键属性（不是数据库字段名，而是java中的属性名），关联属性是父实体中对应子实体的集合属性，而join的右属性为父表的主键属性

* pub: 关联属性缺省仅在后台编程使用，不对外暴露为GraphQL接口。标记为pub之后才对外暴露。
* ref-cascade-delete: 删除父表的时候自动删除子表集合对象
* cascade-delete: 删除当前对象的时候，也删除关联对象。一般使用的是ref-cascade-delete，而不是cascade-delete。
* insertable: 主表提交的时候允许也同时提交子表数据，一次性插入
* updatable: 主表提交的时候允许同时更新子表数据
