# NopORM与EasyQuery框架的设计对比

很多年以前，Hibernate开启了ORM框架的先河，出道即巅峰，直接确立了现代ORM框架的核心功能集合。但是它也存在一些内在的缺陷，比如长期难以解决N+1问题、HQL查询语法存在各种限制等，很多不清楚ORM实现原理的程序员经常会写出性能极其低下的程序代码。最终，在中文互联网领域，反而是基本什么功能都没有，仅仅提供对象封装和SQL管理功能的MyBatis胜出成为了主流。

但是MyBatis的功能集实在是太薄弱了，所以竟然出现一种说法是，Java中的ORM远没有C#中的ORM引擎好用。后续也有一些其他框架号称要克服Hibernate的缺陷，也在局部做出了一些改进，但它们大部分都只是支持Hibernate的一个功能子集，并没有完全发挥ORM框架的作用。

NopORM是Nop平台所提供的下一代ORM引擎，它的设计包含了Hibernate+MyBatis+SpringData的所有核心功能，是一个真正完整的ORM框架，同时它借助于可逆计算理论和Nop平台的基础设施，提供了其他ORM引擎所无法达到的灵活性和可扩展性。
最近有些人在Nop的讨论群中也谈到easy-query这种面向SQL构建的框架，提到“业界需要的就是强类型+自动join+语义化+能够动态优化的dsl”，似乎是认为强类型的QueryBuilder才是ORM的核心价值。easy-query的新版本有一些有趣的特性，相比于SQL语言在子集合查询方面存在明显的增强。

## 查询语句对比

### 1. 隐式join

* 查询银行卡 条件银行卡的所属用户姓名叫小明

easy-query查询:

```java
List<SysBankCard> list = easyEntityQuery.queryable(SysBankCard.class)
  .where(bank_card -> {
    bank_card.user().name().eq("小明");
  }).toList();
```

Nop EQL查询

```sql
select o
from SysBankCard o left join o.user
where o.user.name = ‘小明’
```

说明: easy-query对于关联属性缺省情况下会使用left join，而Nop EQL会使用join，如果要强制指定left join，则必须要显式指定。

Nop QueryBean定义

```xml

<query>
  <sourceName>SysBankCard</sourceName>
  <filter>
    <eq name="user.name" value="小明"/>
  </filter>
</query>
```

* 查询银行卡 条件银行卡的所属用户手机号包含1234并且银行卡是工商银行的

easy-query查询：

```java
List<SysBankCard> list1 = easyEntityQuery.queryable(SysBankCard.class)
  .where(bank_card -> {
    bank_card.user().phone().like("1234");
    bank_card.bank().name().eq("工商银行");
  }).toList();
```

Nop EQL查询

```sql
select o
from SysBankCard
where o.user.phone like '1234'
and o.bank.name = '工商银行'
```

```xml

<query>
  <sourceName>SysBankCard</sourceName>
  <filter>
    <like name="user.phone" value="1234"/>
    <eq name="bank.name" value="工商银行"/>
  </filter>
</query>
```

* 查询小明在工商银行的银行卡信息返回`[姓名|所属银行|卡号]` 按卡号正序排列

easy-query查询：

```java
List<Draft3<String, String, String>> list2 = easyEntityQuery.queryable(SysBankCard.class)
  .where(bank_card -> {
    bank_card.user().name().eq("小明");
    bank_card.bank().name().eq("工商银行");
  })
  .orderBy(bank_card -> bank_card.code().asc())
  .select(bank_card -> Select.DRAFT.of(
    bank_card.user().name(),
    bank_card.bank().name(),
    bank_card.code()
  )).toList();
```

Nop EQL查询：

```sql
select o.user.name, o.bank.name, o.code
from SysBankCard o
where o.user.name = '小明'
and o.bank.name = '工商银行'
order by o.code asc
```

```xml

<query>
  <sourceName>SysBankCard</sourceName>
  <fields>
    <field name="user.name"/>
    <field name="bank.name"/>
    <field name="code"/>
  </fields>
  <filter>
    <eq name="user.name" value="小明"/>
    <eq name="bank.name" value="工商银行"/>
  </filter>

  <orderBy>
    <field name="code" desc="false"/>
  </orderBy>
</query>
```

### 2. 动态join

查询银行卡条件可以传入持卡人姓名或者不传入来筛选结果。 有查询条件那么会自动join用户表否则不会进行join。

easy-query查询:

```java
String queryName = null;
List<SysBankCard> xmCards = easyEntityQuery.queryable(SysBankCard.class)
  //如果查询条件不符合那么将不会加入到条件中
  .filterConfigure(NotNullOrEmptyValueFilter.DEFAULT)
  .where(bank_card -> {
    bank_card.user().name().eq(queryName);
  })
  .toList();
```

Nop EQL查询：

```sql
select o
from SysBankCard o
where 1=1
<sql:filter> and o.user.name = :queryName</sql:filter>
```

Nop QueryBean定义：

```xml

<query ext:removeEmptyCondition="true">
  <sourceName>SysBankCard</sourceName>
  <fitler>
    <eq name="user.name" value=""/>
  </fitler>
</query>
```

### 3. 混合join

easy-query不单单支持显式join,还支持隐式join并且最最最重要的是支持显式+隐式混合使用

easy-query查询：

```java
List<Draft3<String, String, String>> result = easyEntityQuery.queryable(SysBankCard.class)
  .filterConfigure(NotNullOrEmptyValueFilter.DEFAULT)
  .leftJoin(SysBank.class, (bank_card, bank) -> bank_card.bankId().eq(bank.id()))
  .where((bank_card, bank) -> {
    bank_card.user().name().eq("小明");
  })
  .select((bank_card, bank) -> Select.DRAFT.of(
    bank_card.code(),
    bank_card.user().name(),
    bank.name()
  )).toList();
```

Nop EQL查询:

```sql
select o.code, o.user.name, bank.name
from SysBankCard o left join SysBank bank on o.bankId = bank.id
where o.user.name = '小明'
```



### 4. 超强筛选

筛选出用户拥有至少2张工商银行卡且还未在建设银行开户的用户

```java
List<SysUser> list = easyEntityQuery.queryable(SysUser.class)
  .subQueryToGroupJoin(u -> u.bankCards())//启用隐式group
  // .configure(o->{//当我们的子查询数量很多时升级到后2.8.14后可以配置行为全部子查询转group join
  //     o.getBehavior().addBehavior(EasyBehaviorEnum.ALL_SUB_QUERY_GROUP_JOIN);
  // })
  .where(user -> {
    //至少2张工商银行
    user.bankCards().where(card -> {
      card.bank().name().eq("工商银行");
    }).count().ge(2L);

    //没有建行卡
    user.bankCards().none(card -> {
      card.bank().name().eq("建设银行");
    });
  }).toList();
```

Nop EQL查询：

```sql
select o
from SysUser o
where (select count(*) from o.bankCards card where card.bank.name = '工商银行') >= 2
and not exists (select 1 from o.bankCards card where card.bank.name = '建设银行')
```

或者

```sql
select o
from SysUser o left join (
        select card.uid,
               count(case when card.bank.name = '工商银行' then 1 else null end) as count,
               count((case when card.bank.name = '建设银行' then 1 else null end)) as notNone
        from SysBankCard card
        group by card.uid
     ) t2 on o.id = t2.uid
where
    COALESCE(t2.count, 0) >= 2
    AND COALESCE(t2.notNone, 0) = 0
```

### 5. partition by

筛选用户条件为喜欢工商银行的(第一张开户的银行卡是工商银行的)

easy-query查询:

```java
List<SysUser> list = easyEntityQuery.queryable(SysUser.class)
  .where(user -> {
    //用户的银行卡中第一个开户银行卡是工商银行的
    user.bankCards().orderBy(x -> x.openTime().asc()).first().bank().name().eq("工商银行");
  }).toList();
```

Nop EQL查询：

```sql
select o
from SysUser o
where o.id in (
 select card.uid
 from (select card from o.bankCards card order by card.openTime asc limit 1) card
 where card.bank.name = '工商银行'
)
```

或者

```sql
select o
from SysUser o left join (
     select t.uid, t.bank, t._row_
     from (select card.uid, card.bank,
           row_number() over (partition by card.uid order by card.openTime asc) as _row_
           from SysBankCard card) t
     where t._row_ = 1
   ) t2 on t2.uid = o.id
where t2.bank.name = '工商银行‘
```
