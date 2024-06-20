将问题拆分成生成表、生成字段等多个部分，采用few-shot提示方式

```
输入：开发一个 论坛 系统需要哪些数据库表？ 
输出：用户表/user、帖子表/post、分类表/category、通知表/notificatoin、分组表/group

输入：开发一个 博客 系统需要哪些数据库表？ 
输出：文章表/article、分类表/category、标签表/tag、评论表/commont、用户表/user

输入：开发一个 IT管理 系统需要哪些数据库表？ 
输出：资产表/asset、部门表/department、用户表/user、分类表/catetory、位置表/location、公司表/company

输入：开发一个 {system} 系统需要哪些数据库表？ 
输出：
```


```
数据库类型有以下几种：int、text、datetime、decimal、float

输入：开发一个 博客 系统的 文章表 需要哪些数据库字段及类型？
输出：id/int、title/text、content/text、publish_date/datetime、tag_id/int

输入：开发一个 教学 系统的 学生表 需要哪些数据库字段及类型？
输出：id/int、name/text、age/int、gender/text、class_id/int

输入：开发一个 {system} 的 {table} 需要哪些数据库字段及类型？
输出：
```

Chain of Thought
提示词的示例中包含一步步解题过程
Self-Consistency
在 Chain of Thought 基础上生成多个答案，少数服从多数
Tree of Thoughts
每次生成多个结果，每个结果，取其中最好的前几个进一步生成，类似 Beam Search
Least-to-Most
第一步，先让大模型将问题拆分成子问题，「To solve xxx, we need to first solve: 」
第二步，分别让大模型去解决这些子问题，将子问题的解答放入提示词中，问最开始的问题
Generated Knowledge Prompting
让模型先生成关于这个问题的知识点，再去回答问题
Automatic Prompt Engineer
让模型生成和这个问题类似的问题，然后评估这些问题哪个更好，再去问模型，相当于让大模型先帮忙优化一下提示词


Framer通过 TypeScript 定义来指导 GPT 的返回格式及字段要求

```
/** Image keys that are available for use. */
type ImageKey = "single_object" | "sky" | "forest" | "close_up_of_plant" | "silhouette_female" 

type Section =
// A heading with four images under it
  | { id: "gallery"; galleryTitleMax3Words: string; picture1: ImageKey; picture2: }
// A big heading with a smaller text next to it
  | { id: "generic_text_1"; heading: string; paragraph: string }
// A small category/label followed by a big text (a sentence or so)
  | { id: "generic_text_2"; labelOrCategory: string; shortSentence: string }
// Title and description, next to it is a list of four items with years
  | { id: "text_list"; sectionTitle: string; sectionParagraph: string; listTitle: string; item1Title: string; }
// Has 3 differently priced plans in format $4.99/mo etc

interface Page {
/** Several sections with the content that will appear on the page (top to bottom). */
sections: Section[]
}
```


在预训练阶段输入和输出目标分别是一段文本去掉最后一个 token 和去掉第一个 token，比如输入是「Once upon a time」，输出目标是「upon a time in」，而对于指令微调，输入是将指令和期望答案连起来，输出是将指令部分改成一个特殊值，在计算 loss 的时候屏蔽
这样在反向传播的时候就只有答案参与计算。

```
def preprocess(
    sources: Sequence[str],
    targets: Sequence[str],
    tokenizer: transformers.PreTrainedTokenizer,
) -> Dict:
    """Preprocess the data by tokenizing."""
    examples = [s + t for s, t in zip(sources, targets)]
    examples_tokenized, sources_tokenized = [_tokenize_fn(strings, tokenizer) for strings in (examples, sources)]
    input_ids = examples_tokenized["input_ids"]
    labels = copy.deepcopy(input_ids)
    for label, source_len in zip(labels, sources_tokenized["input_ids_lens"]):
        label[:source_len] = IGNORE_INDEX
    return dict(input_ids=input_ids, labels=labels)
```