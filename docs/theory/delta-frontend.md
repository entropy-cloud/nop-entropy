# 支持差量的前端无代码设计

```xml

<component x:schema="component.xdef">
  <import from="comp:MyComponent/1.0.0"/>

  <component name="MyComponent" x:extends="comp:MyComponent/1.0.0">
    <state>
      <a>1</a>
    </state>
    <props>
      <prop name="a" x:override="remove"/>
      <prop name="b"/>
    </props>

    <component name="SubComponent" x:extends="ss">
      <prop name="ss"/>
    </component>

    <template x:override="merge">
      这里可以只显示Delta修正的部分

      <form x:extends="a.form.xml">
        <actions>
          <action name="ss" x:id="ss"/>
        </actions>
      </form>
    </template>
  </component>

  <template>
    <MyComponent/>
    <MyComponent/>
  </template>
</component>
```

1. 通过一个虚拟文件系统的名字空间来加载组件。这里面就可以实现动态编译，动态Delta定制。
2. 如果需要定制，可以使用通用的x:extends来定义一个局部组件。合并规则是按名称合并，列表中的元素按照name或者id定位，而不是通过数组下标。
3. 组件内部引用组件只要使用局部名称。除去组件定义部分之后的template部分完全可以采用通用的Vue或者React组件形式。
4. Vue组件是一个运行时框架。在运行时框架基础上增加Delta的能力不需要改变运行时框架的基本结构。所有Delta的概念可以只存在于编译期。
5. DSL语言本身不用考虑可扩展性和可编辑性的问题，可以由Nop的底层机制统一提供。
6. AMIS页面的根节点上也是允许指定json schema。整体设计是具有元模型约束的，DSL优先而不是可视化优先。
7. 运行时组件的表达是最简表达，没有任何多余的层级或者属性。


1. 系统中非常重要的一个抽象是Loader，大量操作可以在Loader层面完成。
2. 传统的组件是黑箱模型。但是允许Delta定制的组件是白盒模型。
3. 页面是一个特殊的组件，也不需要一个额外的容器组件。编译期完成结构变换，所以不需要运行期的容器组件支持。
4. Delta合并是一个通用操作，它完全可以脱离任何运行时环境来完成。不依赖于任意的DSL.
5. 组件模型是 状态+输入变量定义+输出变量定义。组件可以提供再封装的语义。
6. overwrite允许改变结构
7. 组件提供局部变量空间，本身影响是局部的。
8. 多层overwrite到底以哪个为准。
