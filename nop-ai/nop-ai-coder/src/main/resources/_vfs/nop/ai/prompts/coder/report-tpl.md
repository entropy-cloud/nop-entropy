```text
TITLE: MY REPORT
-------------------
AMOUNT    DATE
3         2005-01-01
```

对应报表模板

```xml

<file>
  <header typeRef="header"/>
  <body typeRef="body"/>

  <types>
    <type name="header">
      <template>
        |TITLE: ${title   }  |
        |--------------------|
        |AMOUNT    DATE      |
      </template>
      <fields>
        <field name="title" length="11"/>
      </fields>
    </type>

    <type name="body">
      <template>
      |${amount }${date   }|
      </template>
      <fields>
        <field name="amount" length="10"/>
        <field name="date" length="10"/>
      </fields>
    </type>
  </types>
</file>
```

- 变量全部为固定长度，length表示长度。不够长度时，自动用空格补齐。注意根据【原始报表数据】判断是否需要leftPad。
- 通过`${fieldName   }`这种表达式来表示变量，整个表达式长度应该与length相同，除非字段名本身超长。通过这种方式，变量表达式执行之后会执行额外的padding操作，从而确保报表输出的字符位置不变。
- header/body/trailer都对应一个type配置，body部分会循环，每个record对应一个数据。
- 如果分页，则按照固定页数会产生一个新的page。pagination配置页面内部的header/footer和页面内汇总结果。
- 上下文中的变量aggregateState.pageIndex对应于当前页面下标，从0开始。可以使用${aggregateState.pageIndex+1}来表示从1开始的页数
- type的template指定字段布局，每行的开始和结尾都使用字符|来标记，实际输出时会删除这些标记
- trailer段中可以引用aggregate变量。聚合变量会自动根据指定prop进行汇总计算。
- 原始报表数据中有些示例内容是空的，但实际运行时会有可能有数据，所以也要替换为变量表达式
- `${amount    }`其中amount共6个字符，加上`${}`3个字符，需要填充4个空格，构成length=10
- pageSize指的是每页的记录数，不是行数。
- 如果是固定内容，应该在template中指定，除非特殊说明，不要在field中配置。

【特别注意】
1. 文件级别的header/trailer不属于body部分。如果存在，它们对应自己的type定义，不要和body混在一起使用switchOn机制
2. 每个type都使用template来指定字段布局，并实现固定内容和变量的混排，只有明确要求的情况才不使用template，此时可以通过使用content属性来插入固定内容。
3. 如果【原始报表数据】中文件头部包含TRAILER信息，则实际上它也是header的一部分。整个文件最前方固定格式的部分都作为header。
4. 除非明确要求，body部分只有一个type类型，不需要使用switchOn机制
