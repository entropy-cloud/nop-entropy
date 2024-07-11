# 字典表翻译

在ORM模型中可以直接定义字典表，并且可以为字段指定关联的字典表，然后在GraphQL层会自动为这些字段生成对应的label字段，
比如status生成status\_label。参见[meta-gen.xlib](https://gitee.com/canonical-entropy/nop-entropy/blob/master/nop-xlang/src/main/resources/_vfs/nop/core/xlib/meta-gen.xlib)中GenDictLabelFields标签的实现。

## 为字段指定字典表

如果手工添加，可以通过prop的schema配置来指定字典表

```xml
<meta>
    <props>
        <prop name="status">
            <schema dict="wf/wf-step-status" />
        </prop>
    </props>
</meta>
```

## 字典表文件

我们可以在`_vfs/dict`目录下存放字典表文件，例如 `wf/wf-step-status`对应于`_vfs/dict/core/wf/wf-step-status.dict.yaml`

这个yaml文件中存放的就是DictBean类型的Java对象，例如

```yaml
label: 步骤状态
locale: zh-CN
valueType: int
description:
options:
  - label: 已创建
    value: 0
    description:

  - label: 已暂停
    value: 10
    description:
```

## 国际化

DictProvider在加载字典表的时候，会检查字典定义中的locale与外部要求的locale是否相同。如果不同，会自动进行I18n翻译。

具体翻译规则是：

1. dict.label.{dictName}
2. dict.option.label.{dictName}.{option.value}

## 数据库中维护的字典表

如果引入nop-sys-dao模块，会自动识别`sys/xxx`形式的字典表定义，它们存放在nop\_sys\_dict和nop\_sys\_dict\_option表中。

## Java枚举类

dict可以直接指定Java Enum类名，例如`dict="io.nop.xlang.xdef.XDefOverride"`。对于没有包含`/`，可以作为类名看待的dictName，
DictProvider会尝试按照类名去加载。

```java

@Locale("zh-CN")
public enum XDefOverride {
    @Option("remove")
    @Description("删除基类中的节点")
    REMOVE("remove"),

    @Option("replace")
    @Description("完全覆盖原有节点")
    REPLACE("replace")
}
```

在Java类中，可以通过`@Option`, `@Label`, `@Description`来指定字段项属性。

## 将业务表作为字典表

在Excel数据模型中，如果为表增加了dict标签，则允许通过 `obj/{bizObjName}`的方式将业务表当作字典表来使用。这种用法要求有一列必须具有disp标签,
它将作为显示名称，而字典项的值就是记录的主键。

## 使用SQL语句作为字典表

在sql-lib.xml中后缀名称是`_dict`的SQL语句可以作为字典表来使用。例如`sql/test.my_dict`对应于`/_vfs/{moduleId}/sql/test.sql-lib.xml`中
名为my\_dict的SQL语句。

```
  <eql name="my_dict">
    select o.fldA as label, o.fldB as value
    from MyEntity o
  </eql>
```

字典表SQL返回的字段名必须是DictOptionBean这个Java类中的属性名，它会被自动包装为DictOptionBean对象。

## 配置选项

* nop.core.dict.return-normalized-label
缺省为true，会将字典的值和label拼接在一起显示


