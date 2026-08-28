# GRC patch/lens core formal proofs

Machine-checked with **Rocq (Coq) 9.2.0**.

## Compile

```bash
cd ai-dev/articles/formal
coqc -q PatchHunk.v
coqc -q LensCombinators.v
```

`PatchHunk.v` depends only on the standard library (`List`, `Arith`, `Lia`).
`LensCombinators.v` additionally uses `Bool`, `Vector`, `Fin`.

## Theorem map

### PatchHunk.v — Darcs-style primitive patch theory

| Statement | Name | Meaning |
|---|---|---|
| hunk application is functional | `applyH_deterministic` | one patch, one effect |
| inverse patch applies backwards | `applyH_inv_hunk` | hunk inverse law |
| commute square | `commute_hunk_square` | `p;q` and `q';p'` both apply and land in the same state |
| commute is involutive | `commute_hunk_self_inverse` | commuting the commuted pair returns the original pair |
| inverse duality | `commute_hunk_inv_dual`, `commute_hunk_inv_dual_ctx` | `commute(inv q, inv p) = (inv p', inv q')` at the actual endpoint |
| merge constructed by commute | `merge_by_commute_correct` | `p;r = q;s` with common endpoint |
| merge symmetry | `merge_hunk_symmetric` | `merge(q,p) = swap(merge(p,q))` |

### LensCombinators.v — Foster-style lens core and BFF

| Statement | Name | Meaning |
|---|---|---|
| identity, composition, product, vector-map laws | `id_lens_laws`, `comp_lens_laws`, `prod_lens_laws`, `map_lens_laws` | Foster's structural combinators preserve the three lens laws |
| BFF totality and laws | `bff_laws` | for every `get` in the structural language, `bff(get)` is a well-behaved lens |
| BFF preserves the view | `bff_get_correct` | `lget (bff e) = eval_get e` |
| semantic BFF is not complete | `no_lens_for_and_get` | `get = andb` on `bool*bool` has no well-behaved put |
