



- **User Table**: user 
- **Post Table**: post 
- **Category Table**: category 
- **Notification Table**: notification 
- **Group Table**: group 


- **Article Table**: article 
- **Category Table**: category 
- **Tag Table**: tag 
- **Comment Table**: comment 
- **User Table**: user


- **Asset Table**: asset 
- **Department Table**: department 
- **User Table**: user 
- **Category Table**: category 
- **Location Table**: location 
- **Company Table**: company 


- **System Table**: system 
- **Table Definition**: table 




- int 
- text 
- datetime 
- decimal 
- float 


- **Article Table**: id (int), title (text), content (text), publish_date (datetime), tag_id (int)


- **Student Table**: id (int), name (text), age (int), gender (text), class_id (int)


- **System Table**: system 
- **Table Definition**: table 



**Prompting Examples:**
1. **To solve XXX, we need to first solve:**  
   - "To solve [X], we need to first solve [Y]."
2. **Generated Knowledge Prompting:**  
   - Let me generate some knowledge about [X].
3. **Automatic Prompt Engineer:**  
   - I will help you engineer the right prompts for [X].

**Steps in Chain of Thought:**
1. **Step 1:**  
   - First, let the model split the problem into sub-problems: "To solve [X], we need to first solve [Y]."
2. **Step 2:**  
   - Then, generate solutions for each sub-problem and select the best ones.
3. **Step 3:**  
   - Finally, use these selected solutions to answer the original question.

**Example:**
- To determine the best way to develop a new feature, we need to:
  1. Identify potential challenges.
  2. Propose solutions for each challenge.
  3. Evaluate and select the most effective solution.



```typescript
interface Page {
  /** Several sections with the content that will appear on the page (top to bottom). */
  sections: Section[];
}

type Section =
  | { id: "gallery"; galleryTitleMax3Words: string; picture1: ImageKey; picture2: ImageKey }
  | { id: "generic_text_1"; heading: string; paragraph: string }
  | { id: "generic_text_2"; labelOrCategory: string; shortSentence: string }
  | { id: "text_list"; sectionTitle: string; sectionParagraph: string; listTitle: string; item1Title: string };

type ImageKey = "single_object" | "sky" | "forest" | "close_up_of_plant" | "silhouette_female";
```


In the pretraining stage, both input and output targets are constructed by removing the last token and the first token from a segment of text. For example, if the input is "Once upon a time", the target will be "upon a time in". For fine-tuning tasks, such as instruction-following, the input consists of concatenated instructions and expected answers, while the output replaces the instruction part with a special value during loss computation.



```python
def preprocess(
    sources: Sequence[str],
    targets: Sequence[str],
    tokenizer: transformers.PreTrainedTokenizer,
) -> Dict:
    """Preprocess data by tokenizing."""
    examples = [s + t for s, t in zip(sources, targets)]
    # Tokenize both examples and sources
    tokenize_fn = lambda x: _tokenize(x, tokenizer)
    examples_tokenized, sources_tokenized = [
        tokenize_fn(x) 
        for x in zip(examples, sources)
    ]
    
    input_ids = examples_tokenized["input_ids"]
    labels = copy.deepcopy(input_ids)
    
    # Replace token at position equal to source length with IGNORE_INDEX
    for label, source_len in zip(labels, sources_tokenized["input_ids_lens"]):
        label[:source_len] = IGNORE_INDEX
        
    return {
        "input_ids": input_ids,
        "labels": labels,
    }
```

