# Test Data Generator

The `batch-gen` model provides a mechanism to dynamically generate test data according to specified ratios.

![](../../theory/batch/batch-gen.png)

![](images/batch-gen-case.png)

The above example generates test cases according to ratios:
1. Generate 30% Class A accounts and 70% Class B accounts.
2. Class A accounts are further divided into two cases: Case_AA1 and Case_AA2. Case_AA1 contains sub-cases executed sequentially: first 【Open Main Card】, then 【Open Supplementary Card】.
   After completing the sub-cases, you can record temporary variables via 【Output Variables】, and later test cases can reference existing variables using the prefix directive syntax `@var:mainCard.id`.

In the batch processing model, you can reference the batch-gen model via the generator configuration, indicating that data will be generated based on the batch-generation model.

```xml
<batch>
  <loader>
    <generator genModelPath="create-card.batch-gen.xlsx" totalCountExpr="totalCount"/>
  </loader>
</batch>
```
<!-- SOURCE_MD5:9d196cde1321ad72d07e646b99fb6e3b-->
