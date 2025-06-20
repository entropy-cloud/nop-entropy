# 研发目标：AI智能代码生成系统



1. 需要将需求按照用例拆分后保存到数据库中。需要设计需求管理相关的数据库表，并为AI生成做好准备

2. AI对话的所有历史需要保存，并且可以选择性的决定是否作为缓存使用，如果设置了缓存，则重复执行AI对话会直接使用缓存结果来返回。

3. 需要可以比较多个不同模型对同一个提示词的执行效果。比较不同的参数下同一个模型的执行效果。

4. 需要有一个评估系统，自动根据录制的用例来评估不同模型的执行效果。

5. AI生成系统需要设置一个目录，生成的模型、代码等信息都保存到文件系统中，并通过git来实现版本管理

6. 允许在已有会话的基础上继续完善。也就是说可以选择一个会话，点击重新生成，或者点击在此基础上完善。完善的结果也需要记录， 完善的基础上可以继续完善。 所以所有记录都要保存在同一个表中，便于最终选择哪个结果作为最终结果。但是通过关联关系和属性来区分开到底是什么关系。

7. 所有的表名都以NopAi为前缀，比如NopAiUseCase， 或者nop_ai_use_case。

8. 每次重试的时候都可以选择model, prompt等，并且都要记录最终使用的prompt和其他参数。

9. 应用需求 - 模块需求 - 用例， 按照这种结构进行组织， 模块要有唯一的模块编号，比如3.1.1， 用例也要有唯一的编号，比如US-3.1.1-2，表示是模块3.1.1中的2号用例。

10. 生成需求和用例之后，用户可以查看并进行局部修正。修正要保留历史版本，这样可以便于回溯。 需要有一种方便的机制整体查看所有需求和最终的结果，比如用树形结构显示众多用例需求，以及整体需求，并且可以用一个完整的markdown显示全部内容，便于用户浏览全部。

11. 需要有一个PromptTemplate的管理机制，PromptTemplate要有inputs, outputs子表定义，分别定义输入/输出参数，通过template来表示文本，具体的propmpt结构参见下面的元模型描述
<prompt
        displayName="string">

    <description xdef:value="string"/>

    <defaultChatOptions xdef:ref="chat-options.xdef"/>

    <inputs >
        <!--
        声明模板中使用的变量信息。主要用于模板管理
        -->
        <input name="!var-name" displayName="string" xdef:name="PromptInputModel"
               type="generic-type" optional="!boolean=false" mandatory="!boolean=false">
            <schema xdef:ref="../schema/schema.xdef"/>
            <description>string</description>

            <defaultExpr>xpl-fn:()=>any</defaultExpr>

            <parseFromMessage xdef:name="PromptInputParseModel" blockStartMarker="string" blockEndMarker="string">
                <parseFunction>xpl-fn:(message)=>any</parseFunction>
            </parseFromMessage>
        </input>
    </inputs>

    <outputs >
        <!-- 解析响应消息，得到结果变量保存到AiResultMessage上
          @skipWhenResponseInvalid 当AichatExchange为invalid状态时，跳过此输出变量的解析
          @parseAfterPostProcess 是否在postProcess调用之前解析
          @format 如果是xml，则尝试从content中解析得到XML节点。如果是json，则尝试解析得到json数据。解析中会自动忽略一些无关的输出信息。
        -->
        <output name="!var-name" displayName="string" xdef:name="PromptOutputModel" type="generic-type"
                optional="!boolean=false" mandatory="!boolean=false"
                format="enum:io.nop.ai.core.model.PromptOutputFormat" codeLang="string"
                skipWhenResponseInvalid="!boolean=false" parseAfterPostProcess="!boolean=false">
            <description>string</description>
            <schema xdef:ref="../schema/schema.xdef"/>

            <xdefPath>v-path</xdefPath>
            <markdownPath>v-path</markdownPath>

            <when>xpl-fn:(chatExchange)=>boolean</when>

            <!-- 当不满足解析条件时，执行defaultExpr返回缺省值 -->
            <defaultExpr>xpl-fn:(chatExchange)=>any</defaultExpr>

            <!--
            没有指定format的情况下才会使用parseFromResponse配置
            如果指定了source，则执行代码来解析变量。如果没有指定source，但是指定了startMarker和endMarker，则从响应消息中截取相关信息。
            如果以上配置都没有，但是配置了contains，则只要响应消息中包含此字符串，就设置为true。

            @startMarkerOptional 如果为true，则允许响应消息中没有startMarker，此时认为startMarker在消息的最前方
            @includeStartMarker 如果为true，则将startMarker包含在解析结果中
            -->
            <parseFromResponse containsText="string"
                               startMarkerOptional="!boolean=false" blockStartMarker="string" blockEndMarker="string"
                               includeStartMarker="!boolean=false" includeEndMarker="!boolean=false">
                <parseFunction>xpl-fn:(chatExchange)=>any</parseFunction>
            </parseFromResponse>

            <!-- 对解析得到的value进行后处理。处理之后再执行XDef元模型验证 -->
            <valueNormalizer>xpl-fn:(value,chatExchange)=>any</valueNormalizer>

            <outputBuilder>xpl-fn:(value,chatExchange)=>any</outputBuilder>
        </output>
    </outputs>

    <!--
    通过xpl模板语言生成prompt，可以利用xpl的扩展能力实现Prompt的结构化抽象
    -->
    <template>xpl-text</template>

    <!--
    用于标记整个输出结束的标记，必须以这个字符串为结尾才是合法输出。最终结果会自动删除这个标记
    通过额外增加一些特殊的标记提示，可以简化结果解析并识别AI输出质量。如果不能严格按照格式要求输出，则往往质量不高。
    -->
    <responseEndMarker>string</responseEndMarker>

    <preProcess>xpl-fn:()=>void</preProcess>

    <!--
    执行完AI模型调用后得到AichatExchange对象，可以通过模板内置的后处理器对返回结果进行再加工。
    这样在切换不同的Prompt模板的时候可以自动切换使用不同的后处理器。
    -->
    <postProcess>xpl-fn:(chatExchange)=>void</postProcess>
</prompt>

prompt模板本身不带有指定model信息，可以适用于任何模型。


12. 整个代码生成过程是从需求开始，逐步向下推进，整体流程是 根据用户需求 -> 经过AI优化再经过用户选择处理的需求， 这个过程中可能会拆分成多个模块，逐个模块完善需求。
  需求完善后 生成用例， 然后再逐个用例完善 -> 生成数据库 -> 完善数据库 -> 生成API -> 完善API -> 实现具体的API函数等。

  每个步骤的输入都是前一个阶段由AI生成且可以由人完善后的内容。

13. 增加AI Task的概念。 AI Task具有子表， AI Model Selection, 为每个子任务选择不同的模型配置，并可以选择maxTokens等参数。 每个task有一个prototype的属性，如果AI Task中有一个name为该值的Task，则表示使用这里的子任务配置作为缺省值，除非显示覆盖。

14. AI Model需要管理起来，它具有provider, model 等配置，相当于给远程大模型一个本地名称， 每个子任务可以选择使用哪个子模型。
