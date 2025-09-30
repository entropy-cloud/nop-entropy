Split the problem into multiple parts such as generating tables and generating fields, and use a few-shot prompting approach

```
Input: What database tables are needed to develop a Forum system?
Output: User table/user, Post table/post, Category table/category, Notification table/notificatoin, Group table/group

Input: What database tables are needed to develop a Blog system?
Output: Article table/article, Category table/category, Tag table/tag, Comment table/commont, User table/user

Input: What database tables are needed to develop an IT Management system?
Output: Asset table/asset, Department table/department, User table/user, Category table/catetory, Location table/location, Company table/company

Input: What database tables are needed to develop a {system} system?
Output:
```


```
Database types include: int, text, datetime, decimal, float

Input: What database fields and types are needed for the article table in a Blog system?
Output: id/int, title/text, content/text, publish_date/datetime, tag_id/int

Input: What database fields and types are needed for the student table in an Education system?
Output: id/int, name/text, age/int, gender/text, class_id/int

Input: What database fields and types are needed for the {table} in a {system}?
Output:
```

Chain of Thought
Include a step-by-step reasoning process in the prompt examples
Self-Consistency
Generate multiple answers based on Chain of Thought and use majority voting
Tree of Thoughts
Generate multiple results each time; for each result, keep the top few and continue generating, similar to beam search
Least-to-Most
Step 1: first have the model break the problem into subproblems: "To solve xxx, we need to first solve:"
Step 2: have the model solve these subproblems individually, insert the subproblem solutions into the prompt, and ask the original question
Generated Knowledge Prompting
Have the model first generate knowledge points related to the question, then answer the question
Automatic Prompt Engineer
Have the model generate questions similar to the original, evaluate which ones are better, and then ask the model—effectively letting the model optimize the prompt first


Framer uses TypeScript definitions to guide GPT's return format and field requirements

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


During pretraining, the input and output targets are a piece of text with the last token removed and the first token removed, respectively. For example, if the input is "Once upon a time", the output target is "upon a time in". For instruction fine-tuning, the input concatenates the instruction and the expected answer, and the output replaces the instruction part with a special value, masking it when computing loss,
so that during backpropagation only the answer participates in the computation.

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
<!-- SOURCE_MD5:f52b887989f7a3c9c2ab949e3afc5c61-->
