## Iterative Refinement

```
#Role: You are a data product manager experienced in data requirements."
#Task: You will be provided with current and history questions asked by a user,"
along with their mapped schema elements(metric, dimension and value),"
"please try understanding the semantics and rewrite a question." + "#Rules: "
1.ALWAYS keep relevant entities, metrics, dimensions, values and date ranges."
2.ONLY respond with the rewritten question."
Current Question: {{current_question}}"
#Current Mapped Schema: {{current_schema}}"
#History Question: {{history_question}}"
#History Mapped Schema: {{history_schema}}" + "#History SQL: {{history_sql}}"
#Rewritten Question: ";
```

## Data Interpretation

```
#Role: You are a data expert who communicates with business users everyday."
#Task: You will be provided with a question asked by a user and the relevant "
result data queried from the databases, please interpret the data and organize a brief answer."
#Rules: 1.ALWAYS respond in the use of the same language as the `#Question`."
        2.ALWAYS reference some key data in the `#Answer`."
#Question:{{question}}"
#Data:{{data}}"
#Answer:";
```

## Correction

```
#Role: You are a senior data engineer experienced in writing SQL."
#Task: You will be provided with a user question and the SQL written by a junior engineer,"
please take a review and help correct it if necessary.
#Rules: "
1.ALWAYS follow the output format: `opinion=(POSITIVE|NEGATIVE),sql=(corrected sql if NEGATIVE; empty string if POSITIVE)`."
2.NO NEED to check date filters as the junior engineer seldom makes mistakes in this regard."
3.SQL columns and values must be mentioned in the `#Schema`."
#Question:{{question}}"
#Schema:{{schema}}"
#InputSQL:{{sql}}"
#Answer:";
```

## Build Semantic Model

```
TermReq termReq1 = new TermReq();
termReq1.setName("核心用户");
termReq1.setDescription("用户为tom和lucy");
termReq1.setAlias(Lists.newArrayList("VIP用户"));
```

