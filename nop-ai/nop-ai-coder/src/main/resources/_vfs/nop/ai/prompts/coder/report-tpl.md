【核心规则】

* 变量表达式
  格式：${fieldName } 变量表达式+N个空格
  计算：N = fieldLength - fieldName.length - 3
  示例：${amount }（字段名6字符 → 总长=6+4+3=13）

* 对齐验证
  每行总长度 = ∑(固定文本长度) + ∑(fieldLength) = 原始行长度
  如果变量名过长，则不用填充空格，允许每行总长度大于原始行长度

* body部分规范
  - 直接指向record对象，不需要处理分页元素
  - 每行对应一个数据记录
  - 分页相关的header/footer由程序自动插入

【示例】

```text
REPORT DATE: 2024-01-01
=== Page 1/2 ===
ID  AMOUNT
001 100.00
002 200.50
=== Page 1/2 ===
TOTAL: 300.50
=== Page 2/2 ===
003 300.75
=== Page 2/2 ===
TOTAL: 300.75
FINAL TOTAL: 601.25
```

对应报表模板

```xml

<file>
  <header typeRef="fileHeader"/>
  <body typeRef="record"/>
  <trailer typeRef="fileTrailer"/>

  <pagination pageSize="2">
    <pageHeader typeRef="pageHeader"/>
    <pageFooter typeRef="pageFooter"/>
    <aggregates>
      <aggregate name="pageSum" aggFunc="sum" prop="amount"/>
    </aggregates>
  </pagination>

  <aggregates>
    <aggregate name="finalTotal" aggFunc="sum" prop="amount"/>
  </aggregates>

  <types>
    <type name="fileHeader">
      <template>
        |REPORT DATE: ${date   }|
      </template>
      <fields>
        <field name="date" length="10"/>
      </fields>
    </type>

    <type name="pageHeader">
      <template>
        |=== Page ${currentPage}/${totalPages} ===|
        |ID AMOUNT |
      </template>
      <fields>
        <field name="currentPage" length="2"/>
        <field name="totalPages" length="2"/>
      </fields>
    </type>

    <type name="record">
      <template>|${id } ${amount }|</template>
      <fields>
        <field name="id" length="8"/>
        <field name="amount" length="10"/>
      </fields>
    </type>

    <type name="pageFooter">
      <template>
        |=== Page ${currentPage}/${totalPages} ===|
        |TOTAL: ${pageSum}|
      </template>
      <fields>
        <field name="currentPage" length="2"/>
        <field name="totalPages" length="2"/>
        <field name="pageSum" length="10"/>
      </fields>
    </type>

    <type name="fileTrailer">
      <template>|FINAL TOTAL: ${finalTotal}||</template>
      <fields>
        <field name="finalTotal" length="10"/>
      </fields>
    </type>
  </types>
</file>
```

- 文件头尾：fileHeader（首行）和fileTrailer（末行）
- 分页控制：pageSize="2"每页2条记录
- 自动汇总：pageSum（每页小计）和finalTotal（全局总计）
- 严格对齐：所有字段长度通过${fieldName }表达式精确控制

【输出要求】
最终模板必须通过以下验证：

- 管道符|的位置与原始数据完全对齐
- 用原始数据测试时，生成报表的字符位置必须100%匹配
- 所有字段的显示长度包含原始数据中的实际值+填充空格
- 上下文中的变量aggregateState.pageIndex对应于当前页面下标，从0开始。可以使用${aggregateState.pageIndex+1}来表示从1开始的页数
- type的template指定字段布局，每行的开始和结尾都使用字符|来标记，实际输出时会删除这些标记
- trailer段中可以引用aggregate变量。聚合变量会自动根据指定prop进行汇总计算。
- 原始报表数据中有些示例内容是空的，但实际运行时会有可能有数据，所以也要替换为变量表达式
- 如果是固定内容，应该在template中指定，除非特殊说明，不要在field中配置。

