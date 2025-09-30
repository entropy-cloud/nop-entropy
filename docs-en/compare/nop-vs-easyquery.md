
# Design Comparison between NopORM and the EasyQuery Framework

Many years ago, Hibernate pioneered ORM frameworks—an instant peak—directly establishing the core feature set of modern ORM frameworks. However, it also has some inherent shortcomings, such as the long-standing difficulty of resolving the N+1 problem and various limitations in HQL query syntax. Many developers who are not familiar with the principles of ORM often write extremely inefficient code. Ultimately, within the Chinese internet community, MyBatis—essentially offering almost no features and merely providing object wrapping and SQL management—ended up prevailing as the mainstream.

However, MyBatis’s feature set is indeed too thin, which even led to the notion that ORMs in Java are far less user-friendly than those in C#. Later, some other frameworks claimed to overcome Hibernate’s defects and made partial improvements, but most of them only support a subset of Hibernate’s functionality and do not fully leverage the role of an ORM framework.

NopORM is the next-generation ORM engine provided by the Nop platform. Its design encompasses all the core features of Hibernate + MyBatis + SpringData, making it a truly complete ORM framework. At the same time, with the help of reversible computation theory and the Nop platform’s infrastructure, it offers a level of flexibility and extensibility that other ORM engines cannot match.
Recently, some people in Nop’s discussion group also brought up easy-query, a framework oriented around SQL construction, mentioning “what the industry needs is strong typing + automatic joins + semanticization + a DSL that can be dynamically optimized,” implying that strongly typed QueryBuilders are the core value of ORM. The new version of easy-query has some interesting features and shows a clear enhancement over SQL in sub-collection querying.

## Query Statement Comparison

### 1. Implicit join

* Query bank cards with the condition that the card’s associated user is named Xiao Ming

easy-query query:

```java
List<SysBankCard> list = easyEntityQuery.queryable(SysBankCard.class)
  .where(bank_card -> {
    bank_card.user().name().eq("小明");
  }).toList();
```

Nop EQL query

```sql
select o
from SysBankCard o left join o.user
where o.user.name = ‘小明’
```

Note: By default, easy-query uses left join for association attributes, while Nop EQL uses join. If you need to force left join, you must specify it explicitly.

Nop QueryBean definition

```xml

<query>
  <sourceName>SysBankCard</sourceName>
  <filter>
    <eq name="user.name" value="小明"/>
  </filter>
</query>
```

* Query bank cards where the associated user’s phone number contains 1234 and the bank card is from ICBC

easy-query query：

```java
List<SysBankCard> list1 = easyEntityQuery.queryable(SysBankCard.class)
  .where(bank_card -> {
    bank_card.user().phone().like("1234");
    bank_card.bank().name().eq("工商银行");
  }).toList();
```

Nop EQL query

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

* Query Xiao Ming’s bank cards at ICBC, returning `[Name | Affiliated Bank | Card Number]`, ordered ascending by card number

easy-query query：

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

Nop EQL query：

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

### 2. Dynamic join

Query bank cards with an optional cardholder name. If a condition is provided, it will automatically join the user table; otherwise, it will not perform the join.

easy-query query:

```java
String queryName = null;
List<SysBankCard> xmCards = easyEntityQuery.queryable(SysBankCard.class)
  // If the query condition does not meet the criteria, it will not be added to the filter
  .filterConfigure(NotNullOrEmptyValueFilter.DEFAULT)
  .where(bank_card -> {
    bank_card.user().name().eq(queryName);
  })
  .toList();
```

Nop EQL query：

```sql
select o
from SysBankCard o
where 1=1
<sql:filter> and o.user.name = :queryName</sql:filter>
```

Nop QueryBean definition：

```xml

<query ext:removeEmptyCondition="true">
  <sourceName>SysBankCard</sourceName>
  <fitler>
    <eq name="user.name" value=""/>
  </fitler>
</query>
```

### 3. Mixed join

easy-query supports not only explicit joins but also implicit joins, and most importantly, it supports the mixed use of explicit + implicit joins.

easy-query query：

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

Nop EQL query:

```sql
select o.code, o.user.name, bank.name
from SysBankCard o left join SysBank bank on o.bankId = bank.id
where o.user.name = '小明'
```

Nop QueryBean:

```xml

<query>
  <sourceName>SysBankCard</sourceName>

  <fields>
    <field name="code"/>
    <field name="user.name"/>
    <field name="name" owner="bank"/>
  </fields>

  <joins>
    <leftJion name="bank" sourceName="SysBank" leftJoinFields="bankId" rightJoinFields="id"/>
  </joins>

  <filter>
    <eq name="user.name" value="小明"/>
  </filter>
</query>
```

### 4. Powerful filtering

Filter out users who have at least two ICBC bank cards and have not opened an account at CCB.

```java
List<SysUser> list = easyEntityQuery.queryable(SysUser.class)
  .subQueryToGroupJoin(u -> u.bankCards())// Enable implicit group
  // .configure(o->{// When there are many subqueries, after upgrading to 2.8.14, you can configure the behavior to convert all subqueries into group joins
  //     o.getBehavior().addBehavior(EasyBehaviorEnum.ALL_SUB_QUERY_GROUP_JOIN);
  // })
  .where(user -> {
    // At least two ICBC cards
    user.bankCards().where(card -> {
      card.bank().name().eq("工商银行");
    }).count().ge(2L);

    // No CCB card
    user.bankCards().none(card -> {
      card.bank().name().eq("建设银行");
    });
  }).toList();
```

Nop EQL query：

```sql
select o
from SysUser o
where (select count(*) from o.bankCards card where card.bank.name = '工商银行') >= 2
and not exists (select 1 from o.bankCards card where card.bank.name = '建设银行')
```

Or

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

Nop QueryBean:

```xml

<query>
  <sourceName>SysUser</sourceName>
  <filter>
    <collection name="bankCards">
      <filter>
        <eq name="bank.name" value="工商银行"/>
      </filter>

      <aggregates>
        <count name="count0"/>
      </aggregates>

      <assert>
        <gt name="count0" value="2"/>
      </assert>
    </collection>
  </filter>
</query>
```

### 5. partition by

Filter users who prefer ICBC (the first bank card they opened is with ICBC)

easy-query query:

```java
List<SysUser> list = easyEntityQuery.queryable(SysUser.class)
  .where(user -> {
    // Among the user's bank cards, the first opened bank card is with ICBC
    user.bankCards().orderBy(x -> x.openTime().asc()).first().bank().name().eq("工商银行");
  }).toList();
```

Nop EQL query：

```sql
select o
from SysUser o
where o.id in (
 select card.uid
 from (select card from o.bankCards card order by card.openTime asc limit 1) card
 where card.bank.name = '工商银行'
)
```

Or

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

Nop QueryBean definition：

```xml

<query>
  <sourceName>SysBankCard</sourceName>
  <filter>
    <collection name="bankCards">
      <orderBy>
        <field name="openTime" desc="false"/>
      </orderBy>
      <first/>
      <assert>
        <eq name="bank.name" value="工商银行"/>
      </assert>
    </collection>
  </filter>
</query>
```

### 6. Multi-table joins

* Multiple conditions in join ON

easy-query query:

```java
 List<BlogEntity> blogEntities = easyEntityQuery
  .queryable(Topic.class)
  .innerJoin(BlogEntity.class, (t, t1) -> t.id().eq(t1.id()))
  .where((t, t1) -> {
    t1.title().isNotNull();
    t.id().eq("3");
  })
  .select((t, t1) -> t1)
  // .select(BlogEntity.class, (t, t1) -> t1.FETCHER.allFields())
  .toList();
```

Nop EQL query:

```sql
select t1
from Topic o join BlogEntity t1
on o.id = t1.id
where t1.title is not null
and o.id = 3
```

Nop QueryBean query

```xml

<query>
  <sourceName>Topic</sourceName>
  <fields>
    <field name="name" owner="t1"/>
  </fields>

  <joins>
    <innerJoin sourceName="BlogEntity" alias="t1">
      <conditions>
        <condition leftField="id" rightField="id"/>
      </conditions>
    </innerJoin>
  </joins>

  <filter>
    <notNull name="title" owner="t1"/>
    <eq name="id" value="3" owner="o"/>
  </filter>
</query>
```

### 7. Grouped queries

easy-query query:

```javascript
easyEntityQuery.queryable(DocUser.class)
        .subQueryToGroupJoin(o -> o.bankCards())
        // Can be applied to non-subQueryToGroupJoin ordinary subqueries as well
        .subQueryConfigure(o -> o.bankCards(), bcq -> bcq.where(x -> {
            // Supports implicit joins and ordinary attribute filters
            x.bank().name().eq("银行");
            x.type().like("45678");
        }))
        .where(user -> {
            user.bankCards().where(x -> x.type().eq("123")).
                    sum(o -> o.code().toNumber(Integer.class))
                    .eq(123);
            user.bankCards().where(x -> x.type().eq("123")).
                    sum(o -> o.code().toNumber(Integer.class))
                    .eq(456);
            user.bankCards().where(x -> x.type().eq("456")).
                    sum(o -> o.code().toNumber(Integer.class))
                    .eq(789);
        })
        .toList();
```

Nop EQL query

```sql
select o
from DocUser o left join (
    select t.uid
       sum(case when t.type = '123' then cast(t.code as INT) else 0 end) as sum_2
       sum(case when t.type = '456' then cast(t.code as INT) else 0 end) as sum_3
    from DocBankCard t
    where t.bank.name = '银行'
    and t.type like '%45678'
    group by t.uid
  ) t3 on t3.uid = t.uid
where t3.sum_2 = 123
and t3.sum_2 = 456
and t3.sum_3 = 789
```

Nop QueryBean query

```xml

<query>
  <sourceName>DocUser</sourceName>
  <joins>
    <leftJoin alias="t" relation="bankCards">
      <filter>
        <eq name="bank.name" value="银行"/>
        <like name="type" value="45678"/>
      </filter>

      <aggregates>
        <sum name="sum_2" sourceField="code">
          <filter>
            <eq name="type" value="123"/>
          </filter>
        </sum>
        <sum name="sum_3" sourceField="code">
          <filter>
            <eq name="type" value="456"/>
          </filter>
        </sum>
      </aggregates>
    </leftJoin>
  </joins>

  <filter>
    <eq name="sum_2" value="123" owner="t"/>
    <eq name="sum_2" value="456" owner="t"/>
    <eq name="sum_3" value="789" owner="t"/>
  </filter>
</query>
```

<!-- SOURCE_MD5:6847af89777ffded9997f6325e8f8162-->
