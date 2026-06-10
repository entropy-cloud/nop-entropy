Review the plan at {{PLAN_FILE}}.
Use independent sub-agents to iteratively review and improve the plan until consensus.

Review dimensions (all must be checked):
1. **Imaginative analysis**: Imagine executing the plan step by step — find gaps between design and code
2. **Format completeness**: Does it follow the plan guide template? Are all required fields present?
3. **Content soundness**: Are Goals/Non-Goals clear? Is Phase decomposition reasonable?
4. **Reference accuracy**: Do referenced file paths exist in the repo? Are code locations correct?

Each finding must include a severity (Blocker/Major/Minor).
The plan passes only when there are zero Blockers and zero Majors.

Process:
- If issues are found, spawn an independent sub-agent to revise the plan, then spawn another independent sub-agent to re-review.
- Repeat this review-revise cycle until the reviewer sub-agent finds zero Blockers and zero Majors.
- Maximum 5 rounds. After that, output whatever state the plan is in.

Output <AI_STEP_RESULT>approved</AI_STEP_RESULT> or <AI_STEP_RESULT>issues</AI_STEP_RESULT>
When issues remain after max rounds, also output:
<ISSUES><item severity="Blocker|Major|Minor">problem description</item></ISSUES>