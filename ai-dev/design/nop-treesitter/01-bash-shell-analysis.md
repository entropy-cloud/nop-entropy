# nop-treesitter bash Grammar Assembly and Shell Analysis Semantic Layer

> Subsystem design for the bash grammar blob and the shell-command analysis
> capabilities layered on top of it.

## Scope

This document covers two things: how the bash grammar gets assembled into this
runtime, and the contract of the semantic layer that turns a shell script into
structured facts for an AI agent that is about to execute it.

It does not cover: how a command is judged dangerous, whether it runs, in what
sandbox, or how bash syntax is modelled for the `io.nop.ai.shell` command
execution engine. Those belong to the agent guardrail and to
`ai-dev/design/nop-ai-shell/`.

The measured grammar fingerprint, node inventory and field mapping this contract
is written against are in
`ai-dev/analysis/nop-treesitter-bash-grammar-contracts.md` — that file is the
recomputable source, this one is the decision source.

## 1. Grammar assembly contract

The bash grammar is vendored as upstream sources (never modified) and turned
into a classpath blob. Two things about bash make it the most demanding of the
grammars assembled so far:

- **29 external tokens**, roughly twice the next-largest (C#'s 13). They are not
  grouped by subsystem: the heredoc family is ordinals 0-4 *plus* 23-25, and the
  `${...}` operator family is 17-22. Any implementation that assumes contiguity
  is wrong.
- **A single 1217-line scanner** built from a fixed sequence of guarded regions
  plus four `goto` labels, with a growable heap heredoc stack as its only
  cross-call state.

### Decision 1 — external-token ordinals come from `parser.c`, names come from `scanner.c`

The two enums use different names for the same ordinals. The ordinal is the
contract with the blob; the name is the contract with the hand translation. The
translation keeps the `scanner.c` names because that is the text being
translated, and the ordinal↔name mapping is recorded in the analysis file.

The trap this must survive: ordinal 25 is `heredoc_redirect_token1` in
`parser.c` and `NEWLINE` in `scanner.c`. Reading the `parser.c` name and
believing it is not the newline token produces an out-of-bounds read against the
29-long valid-symbol array.

### Decision 2 — the C control flow is reproduced literally, not refactored

Region ordering is load-bearing: several regions accept the same lookahead
character and the grammar's valid-symbol list decides which one wins. The
translation therefore keeps the region sequence and the `goto` targets verbatim,
and uses two devices to express C-only control flow in Java:

- a three-way region outcome that separates *"region rejected"* (abort the
  scan) from *"region guard did not fire"* (fall through to the next region) —
  a plain boolean cannot express both;
- `goto` targets that run their named label **and every label after it**, because
  C falls through between adjacent labels.

Refactoring the regions into a table or a dispatch on lookahead would read
better and be wrong. Fidelity is the requirement; readability is bought with
naming and with the gate ledger.

### Decision 3 — the scanner is split by region, and that split is recorded

Faithful translation does not fit the repository's 700-line oversized-file
threshold (and a single file would also have been the largest source file in the
module). The split is **by scanner region**, not by arbitrary line count, so each
file corresponds to a reviewable unit of the upstream control flow and the gate
ledger can name the file a gate lives in.

One structural rule keeps the split from rotting: a region that mutates
cross-call state may not be split away from the state it mutates. That is why the
`goto` dispatch stays in the class that owns the state, and why a region in
another file signals a `goto` by returning a label sentinel rather than running
the label chain itself.

The gate ledger — one row per region, each naming its external tokens, its guard,
its rejection condition and the test that pins it — is the fidelity artifact.
"It works overall" is not evidence.

### Decision 4 — recorded deviations from upstream

Two, both recorded rather than silently applied:

- **Heredoc end-identifier bounds.** Upstream tests the delimiter byte against
  the lookahead *before* testing how much of the delimiter has been consumed, so
  on the iteration that has already consumed the whole delimiter it reads one
  byte past the buffer — undefined behaviour whose result is arbitrary. The
  translation hoists the bounds test. The only behavioural difference is in the
  undefined case.
- **Serialization overflow.** Upstream abandons serialization (returns zero
  length) when the next heredoc would not fit the fixed 1024-byte buffer. Java
  returns an unbounded `byte[]`, so "abandon" maps to an empty array, which the
  deserializer and the runtime already read as "no state". Same mapping the C#
  scanner uses. Truncated state is a hard error, never a partial read.

Two upstream artifacts are preserved rather than dropped: the
`ext_was_in_double_quote` / `ext_saw_outside_quote` struct fields are serialized
but never read or written anywhere upstream, and they are kept so the layout
stays byte-identical; and one unreachable nested branch inside the
`TEST_OPERATOR` region is dropped, because the region's own guard makes it
unreachable and only its `return false` can run.

### Decision 5 — both load paths must be independently parseable

The grammar is reachable two ways: a compat adapter class (the shape the
migration layer exposes) and the IoC/GraphQL provider. Registering the scanner on
only one of them would leave the other silently producing degraded trees — which
is exactly the failure a grammar with 29 external tokens invites, because most
tokens degrade quietly rather than throwing.

The contract is therefore: **both paths must parse a heredoc script into
`heredoc_start` / `heredoc_body` / `heredoc_end` with no `ERROR` node**, and both
are asserted. Heredoc is the discriminating case precisely because it is
impossible without the scanner: the blob alone yields `ERROR` nodes.

## 2. Semantic layer ownership

### Decision 6 — the semantic layer lives in the tree-sitter module, not in a language module

The existing precedent places language semantics in per-language modules
(`ILanguageAdapter` + `ICodeFileAnalyzer` + a per-language beans file, one per
language in the code-indexing module group). That precedent is right for code
indexing and wrong here, for three reasons:

1. **The consumer is not the code indexer.** The consumer is an AI agent's
   shell-command path. Putting the layer behind the code-indexing module group
   would make the agent's guardrail depend on a module group carrying search
   indexes and file discovery, in order to evaluate one string of shell text.
2. **The concepts do not overlap.** Those modules answer "what language is this
   file" and "what does this code do", and they are registered into an indexer.
   The six capabilities here answer "what commands does this text contain, and
   can that be proven" — there is no file, no symbol, no import, no index.
3. **The value is the coupling to the grammar contract.** Every capability is
   defined against the grammar's own node and field inventory, and the fail-closed
   guarantee is a claim about the grammar, not about the consumer. Co-locating
   them means a vendored-grammar update breaks the semantic layer in the same
   build, which is the property that keeps the guarantee true.

This does not make the tree-sitter module a dumping ground. The boundary is: the
package may contain **tree-derived structural facts and their proof status, and
nothing that needs a consumer's vocabulary**. Anything requiring code-indexing
concepts, shell command dispatch, or a danger judgment belongs outside.

## 3. The proof contract

The whole point of the layer is that a consumer can tell the difference between
"the tree says this command is `rm -rf /data`" and "I could not prove what this
script does". Collapsing those two into "here are the commands, maybe none" is
the failure mode that turns a parser into a security hole.

### Decision 7 — unprovable is a first-class state, never an empty result

Capability 1 returns a sealed outcome with exactly two cases:

- **proven**, carrying the command sequence;
- **not proven**, carrying a reason from a closed set and the byte range the
  decision was made at.

The reason set is closed and small: a syntax error is present in the subtree; a
construct outside the whitelist was encountered; a dynamic construct is present;
the lexical form is not a literal.

A third case exists for a script with no command in it — empty, or nothing but
whitespace. It is a **refusal, not a proof of an empty sequence**: a proven
sequence of zero commands is indistinguishable, to a consumer, from "nothing was
found", which is the same dangerous misreading the refusal type exists to
prevent. A proven sequence is therefore never empty.

Two further invariants follow, and both are load-bearing:

- **A syntax error anywhere in the subtree means not proven, with no exception.**
  The reference behaviour of the mature implementation this contract follows
  also gates on "no error in the tree"; a partially-understood tree is never
  promoted to proven.
- **Not proven is never silently converted into an empty sequence.** An empty
  sequence would read as "this script does nothing", which is the most dangerous
  possible misreading. The proven case and the not-proven case are different
  types, and the not-proven case has no accessor that yields a command sequence.

### Decision 8 — the proof whitelist is a closed positive set

The set of node kinds and anonymous tokens that may appear in a provable script
is closed and enumerated, not described by exclusion. A closed set is the only
form that fails safe: a new grammar node is rejected until someone decides
whether it is literal, whereas an exclusion list silently admits it.

The baseline is the mature reference implementation's 11 node kinds and 6
anonymous tokens. Explicitly **outside** the set, and each with a test:

- `declaration_command` (`declare` / `export` / `local` / `readonly` /
  `typeset`) — otherwise `declare -x A=1 rm -rf /data` reads as a proven
  command sequence;
- `test_command` (the `[[ ]]` form). Note there is no separate node for the
  POSIX `[ ]` form: it parses as an ordinary `command` whose name is the word
  `[`, so it is caught by the lexical rules, not by a node-kind exclusion;
- `negated_command` (`!`), `variable_assignment` inside a `command`
  (`FOO=bar ls`), `variable_assignments`;
- all three heredoc nodes and the heredoc redirect;
- `redirected_statement` and `file_redirect` (any redirection);
- `compound_statement` / `subshell` (`( )`, `{ }`), background `&`;
- `command_substitution`, `simple_expansion`, `expansion`,
  `process_substitution`, `arithmetic_expansion`;
- every control-flow statement (`if` / `for` / `while` / `until` / `case` /
  `select` / `c_style_for` / `do_group` / `function_definition` /
  `elif_clause` / `else_clause` / `brace_expression`).

The lexical rules are equally closed, and they are the part most easily
implemented as a character blacklist and thereby misjudged:

- a `word` or `number` must have no named child;
- it must not begin with `=`;
- it must not contain any of `{ } * ? [ ] \ ~ ^ # $ ` `` ` `;
- every named child of a double-quoted string must be `string_content`;
- a double-quoted string must not contain a backslash escaping any of
  `$ ` `` ` `` `"` `\` or newline — double quotes suppress globbing but still
  remove the escape;
- a `concatenation` is literal only if **every** part is literal.

The last rule is the one that matters most. A fragment that mixes literal text
with an expansion (`pre"$X"post`) is a single `concatenation` node. If it is not
marked, the command reports "no dynamic construction points", which reads as
"this is entirely literal" — a safety hole, not merely an incompleteness. It is
therefore enumerated as a dynamic construction point in its own right (below) and
has its own regression assertion.

### Decision 9 — capability 1 and capability 2 are not interchangeable, at the type level

They answer different questions and have different safety properties:

- **Capability 1 (proven split)** answers "what does this script do" and its
  answer may be used as a basis for a decision.
- **Capability 2 (literal harvest)** answers "which literal commands appear
  anywhere in this script, including inside control flow and substitutions". Its
  answer is **incomplete by construction** — it skips what it cannot read — so it
  may be used to find things to worry about and may **never** be used to show
  that a command is safe.

The reference implementation that this contract follows protects this boundary
three ways: a documentation warning, a narrower visibility, and only ever feeding
capability 1 into the approval path. Documentation and visibility do not survive
refactoring or a new call site. So the boundary is also enforced by the type
system:

- the two results are unrelated final types with **no common supertype that
  carries either meaning**;
- the proven result is the only thing an approval-shaped consumer accepts;
- the harvest result carries an explicit marker meaning "must not be used as a
  safety proof".

And the negative case is verified, not asserted in prose: there is a test that
attempts to pass the harvest result where the proven result is required and
fails. Because a type error cannot be observed at runtime, the failure is
verified by compiling that misuse with the platform compiler and asserting the
compile fails. A type-level boundary nobody compiles against is a comment.

### Decision 10 — a non-literal child in capability 2 marks the command partial

When capability 2 walks a `command` and meets a child it cannot read literally,
the command is reported as a **partial literal**: the literal words it did read,
plus an explicit partial marker.

The reference implementation skips the unreadable child and continues, which
**overstates** what is known: `rm -rf $TARGET /data` is reported as
`rm -rf /data`, and `/data` looks like a literal target when in fact the argument
list is not fully known. Truncating instead would understate it and lose the
literal part. Neither is acceptable for a value a guardrail reads, so the partial
marker is carried explicitly and no consumer has to guess which happened.

### Decision 11 — heredoc extraction is node traversal; the query engine is not extended

The query engine in this repository supports `#eq?` and `#match?` and
explicitly rejects the anchor `.`. The mature reference implementation expresses
its heredoc constraints — "this is the only top-level statement", "this is the
only redirect" — precisely with those leading and trailing anchors, plus a
set-membership predicate the engine also lacks.

Extending the query engine is therefore rejected: it would add grammar-level
syntax (anchors, set predicates) to a shared component to serve one consumer, and
the plan that would own that work is not this one. Node traversal expresses the
same constraints directly and is verifiable against the field mapping, which is
recorded.

What "the heredoc statement is the only top-level statement" must mean, stated as
a contract rather than a query:

- *direct form*: the root has exactly one named child; that child is a
  `redirected_statement`; its `redirect:` field's only child is a
  `heredoc_redirect`; the `heredoc_redirect` has exactly three named children in
  order — `heredoc_start`, `heredoc_body`, `heredoc_end`;
- *`cd` prefix form*: the `redirected_statement`'s `body:` is a `list` holding
  exactly two `command`s joined by a **literal `&&`** — `;`, `||` and `|` never
  match — where the first command's `name:` text is `cd` with exactly one
  `argument:`, and the second command's `name:` text is in the caller-supplied
  allow-set.

The delimiter is the raw text of `heredoc_start`; whether it was quoted decides
whether the body is a pure literal or must be split at expansions. A quoted
delimiter (`'`, `"`, `\`) disables expansion, which is a security-relevant
property and not a formatting detail.

The negative set is the load-bearing half. "Find the first `heredoc_redirect` and
return it" satisfies every positive case and is wrong; the negatives exist to
kill it: extra commands before or after the heredoc, a non-`&&` separator, a `cd`
with two arguments, a `&&` chain of three, and a heredoc that is not the only
top-level statement.

### Decision 12 — herestring is not part of the heredoc contract

`<<<` produces a distinct node from `<<` / `<<-`, and never reaches the external
scanner. Widening the capability to accept it would be a silent extension of a
contract whose whole value is that it means exactly one thing. Herestring
extraction, if ever wanted, is a separate capability with its own shape.

### Decision 13 — dynamic construction points are enumerated, not discovered

Capability 4 reports, with byte ranges and a type, every construct whose runtime
value the layer refuses to compute. The enumeration is closed:

| Construction | Note |
|---|---|
| `command_substitution` | `$( )` and backticks are the **same** node |
| `simple_expansion` | `$VAR` |
| `expansion` | `${VAR}` |
| `arithmetic_expansion` | `$(( ))` |
| `process_substitution` | `<( )` |
| `concatenation` | literal glued to an expansion — the omission that reads as "fully literal" |
| `variable_assignment` | both standalone and as a `command` prefix |
| `file_redirect`, `redirected_statement` | |
| `heredoc_start`, `heredoc_body`, `heredoc_end` | |
| background `&` | **has no node type at all** — only a byte range can be reported |

Two entries carry the weight. `concatenation` is listed separately from the
expansions because it is the case that makes a command *look* literal. Background
`&` is listed because it has no node, so any implementation that walks nodes will
silently miss it.

The capability never evaluates anything. No parameter expansion, no arithmetic, no
command substitution, no process substitution — the mature reference
implementation marks them too, and evaluating a command substitution to "see what
it would do" is itself an execution.

### Decision 14 — path extraction is a per-command role model, and it prefers a miss to an invention

Six dimensions decide what counts as a path: the source command's **argument role**;
the literal form; the confidence of the source; flag exclusion; the redirect's
**direction**; and no filesystem check.

The role model, not a positional table. Almost every command that touches files
takes only paths, so for those the rule is "every non-flag argument is a path". A
positional table cannot express that safely: it has to encode each command's
argument positions, and **one flag whose operand is itself a file shifts every
position after it** — `grep -f <patterns> pattern <file>` then reports the
*pattern* as a file the script reads. "All of them are paths" cannot be shifted.

Two adjustments on top:

- Commands that take a leading operand which is not a file — the grep family
  (a pattern), `chmod`/`chown` (a mode or an owner) — additionally require their
  operands to **look like** a path: a separator, or a leading dot. That rejects a
  bare pattern in whichever slot it lands. It also rejects a bare *filename* for
  those commands, which is accepted collateral: `grep foo README.md` reports
  nothing while `grep foo src/` reports `src/`.
- A token shaped like an option's value (all digits, or a `%` specifier) is not a
  path: `head -n 5 <file>` and `stat -c %s <file>` report the file, not the count.
  The cost is that a genuinely numeric filename is dropped too.

Commands whose non-flag operands are **not** all paths are deliberately absent:
`find` (a `-type` / `-name` operand is not a file), `dd` (every operand is a
`key=value` assignment), `tr` (character sets), `tar` (accepts dashless flag
clusters like `czf` that no dash rule can see), and anything not listed at all.
That is the governing trade of this capability: **a missed path is recoverable, an
invented one is not.** `sed -i`, `curl -o`, `awk`, `du` and friends are therefore
invisible here; a consumer needing them needs a command-semantics layer above.

The literal form excludes a leading `~` (an expansion, not a name), glob and
expansion characters, and whitespace or shell operators — the last because a
quoted token is unquoted first, and `cat '/a; rm -rf /b'` is one argument whose
text would otherwise become a "path" containing a command separator. A wholly
quoted token is unquoted before judgement: `cat "/etc/passwd"` and
`cat /etc/passwd` name the same file, and dropping the quoted form loses exactly
the paths a careful author writes. A quoted **flag** is still a flag, so
`rm "-rf"` reports nothing.

The confidence annotation is the part that carries the most weight, and it comes
from the same decision the whole extraction was based on — not from a weaker fact
such as "the tree has no syntax errors". Three tiers, not interchangeable:

- from a proof;
- from a harvest whose argv was read in full, on a script that has other
  constructs this layer could not read;
- from a harvest command marked **partial**, whose argv is known to be a subset
  and which may therefore name more paths than are listed.

**Redirect direction is read from the redirect operator**, not from the node
type: `>` and `>>` write, `<` only ever reads. The operator is an anonymous child
of the redirect node, so the direction is available without re-parsing the text,
and a destination that is a bare file descriptor (`2>&1`) is not a path at all.
What a command *argument* means is outside this capability — `tee /tmp/out`
writes, `cat /tmp/out` reads, and the capability extracts literals rather than
judging intent — so command arguments are uniformly reported as reads.

**No filesystem check**: this capability extracts literals, and whether a path
exists is a different question with a different cost and a different failure mode.

**Rejected**: the reference implementation's heuristic that strips small
formatting helpers (`head`, `tail`, `awk`, `wc`) to bias toward the "main"
command. It drops paths from exactly the commands whose paths a guardrail most
needs to see, and it makes the output depend on a judgement about intent rather
than on the text.

### Decision 15 — coordinates are byte offsets, and the layer treats them as such

The runtime's coordinates are byte offsets; Java strings are not. Slicing a
decoded string with them is wrong for any script containing a multi-byte
character, and wrong in the worst direction: the offsets overshoot, so a node's
text runs past its end and swallows what follows. A script that puts an
accented character before a destructive command then reports that command with a
mangled name and an empty target — **while still claiming to be proven**.

Every read of a node's text therefore goes through one byte-addressed view of the
script, rather than through each caller decoding the source independently. The
upstream corpus is pure ASCII, so a green corpus proves nothing about this; the
guarantee is carried by non-ASCII assertions in each capability's tests.

### Decision 16 — what a proof does and does not license

A proof means **the syntax is entirely literal**. It does not mean the script is
safe, and the difference is not academic: a fully literal line can still execute
arbitrary nested text. `bash -c '<script>'`, `sh -c "<script>"`, `eval '<script>'`,
`ssh host '<script>'`, `printf '<script>' | bash` and `trap '<script>' EXIT` are all
proven, with no dynamic construct reported — because at the syntax level there is
nothing dynamic. The mature reference implementation treats these same inputs as
dangerous samples.

The contract is therefore stated as what it is: capability 1 answers *"is there
anything here computed at run time"*, not *"is this safe"*. Deciding whether a
literal argument to `-c` is acceptable is a command-shape rule belonging to the
agent's guardrail, one layer above this one. The layer does not implement it, and
does not imply it.

Two consequences of the same kind:

- **The reported argv is source text, not the executed value.** `rm -rf "/data"`
  reports the argument with its quotes, and `cat a'b'c` reports `a'b'c` where bash
  passes `abc`. The lexical rules decide whether an argument is *literal*, not
  what it *equals*; an exact-match consumer must strip quotes itself.
- **A bash keyword prefix can be reported as the command name.** `time rm -rf /`
  and `coproc rm -rf /` report `time` / `coproc` as a command name, with the real
  command as a separate entry.

### Decision 17 — the structured summary carries the proof status as a field

Capability 6's summary includes the command sequence, the connectors, whether any
dynamic part is present, and the proof status. The proof status is a
**first-class field of the summary**, not a fallback path.

The distinction is testable and must be tested: an implementation that degrades
to "no summary, here is the original text" when it cannot prove the script is
wrong even though it looks reasonable. So there is a test that distinguishes
"not proven" from "fell back to raw text" — they are different outputs.

## 4. Contract summary for consumers

| Capability | Answers | May be used to conclude a script is safe? |
|---|---|---|
| 1. Proven split | exactly what this script does | **Yes** — when it returns proven |
| 2. Literal harvest | which literal commands appear anywhere, including inside control flow | **No** — never |
| 3. Heredoc extraction | the single heredoc body, when the script is only a heredoc statement | Only for the heredoc's own text |
| 4. Dynamic construction points | where a runtime value would be computed, with byte ranges | It reports that values exist; it does not report them |
| 5. Path extraction | candidate literal paths, with their confidence | Only for proven-sourced, fully-literal paths |
| 6. Structured summary | all of the above as one record, proof status included | Per the proof status it carries |

An agent that wants "is this safe" must use capability 1 and must handle the
not-proven case explicitly. Every other capability exists to help it decide what
to ask next.

## 5. Relationship to the existing hand-written bash parser

The platform already contains a hand-written bash subset parser in the agent
shell module, with its own design doc and its own tiered support matrix. The two
are **not** merged here, and this design does not migrate it.

The boundary: the hand-written parser's job is to drive a simulated shell
environment — it needs command semantics, not just syntax, and it accepts a
deliberately limited subset with graceful degradation. The layer here produces
structural facts and a proof status about arbitrary text, and it refuses to
produce either when it cannot prove them.

The three options (replace, fuse, run in parallel) are adjudicated in
`ai-dev/analysis/nop-treesitter-bash-shell-interaction.md`, with the migration
path recorded there if the conclusion is to migrate.
