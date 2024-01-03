# 对象元数据

xmeta文件定义了后台服务对象的元数据，描述了对象具有哪些属性，以及这些属性是否可以修改，是否可以查询等信息。
NopGraphQL引擎返回的对象信息完全由XMeta来定义。如果一个属性在XMeta中没有定义，则即使实体上具有这个字段，前台GraphQL和REST请求也无法访问到该字段。

## 定义关联属性
实体模型中的关联对象生成到XMeta模型中体现为如下配置

````
<props>
  <prop name="parent">
    <schema bizObjName="NopAuthDepartment" />
  </prop>
  
  <prop name="children">
     <schema>
        <item bizObjName="NopAuthDepartment" />
     </schema>
  </prop>
</props>
````

* schema如果具有item，则表示是集合属性，集合元素的类型由 item节点的bizObjName属性来指定。
* 如果是关联对象，则通过schema的bizObjName属性来指定关联类型

