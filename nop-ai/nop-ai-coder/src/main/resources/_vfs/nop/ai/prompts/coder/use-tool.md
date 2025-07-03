【可用工具】
工具调用使用以下标准XML格式：

```xml

<tools>
  <!-- 查找UI组件库中的现有组件 -->
  <find-ui-components names="component-names" return="var-name">
    <def:return>[{
      componentName: string,
      props: object,
      documentation: string
      }]
    </def:return>
  </find-ui-components>

  <!-- 在代码库中搜索文件 -->
  <find-files pattern="regex" return="var-name">
    <def:return>[string]</def:return>
  </find-files>

  <!-- 创建或修改文件 -->
  <create-files>
    <file path="file-path"><![CDATA[
      file-content
    ]]></file>
  </create-files>
</tools>
```

【工具使用规范】

* 每个<tools>块可包含多个独立命令
* 命令默认并行执行
* 使用return属性存储返回值到上下文

【约束条件】

* 必须通过工具指令完成所有操作
* 每次交互返回完整的<tools>块或最终结果
* 禁止工具描述外的任何操作指令
