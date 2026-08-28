From Stdlib Require Import Bool Vector Fin.
Import VectorNotations.

Set Implicit Arguments.
Unset Strict Implicit.

(** * Well-behaved lenses and Foster's structural combinators *)

Record Lens (S V : Type) : Type := {
  lget : S -> V;
  lput : S -> V -> S;
}.

Arguments lget {S V} l s.
Arguments lput {S V} l s v.

Record LensLaws {S V : Type} (l : Lens S V) : Prop := {
  law_getput : forall s : S, lput l s (lget l s) = s;
  law_putget : forall (s : S) (v : V), lget l (lput l s v) = v;
  law_putput : forall (s : S) (v1 v2 : V), lput l (lput l s v1) v2 = lput l s v2;
}.

(** Identity lens. *)
Definition id_lens (A : Type) : Lens A A :=
  {| lget := (fun s => s); lput := (fun _ v => v) |}.

Lemma id_lens_laws : forall A, LensLaws (id_lens A).
Proof.
  intros A. split; simpl; intros; reflexivity.
Qed.

(** Sequential composition of lenses. *)
Definition comp_lens {S M V : Type} (l1 : Lens S M) (l2 : Lens M V) : Lens S V :=
  {| lget := (fun s => lget l2 (lget l1 s));
     lput := (fun s v => lput l1 s (lput l2 (lget l1 s) v)) |}.

Lemma comp_lens_laws : forall {S M V} (l1 : Lens S M) (l2 : Lens M V),
    LensLaws l1 -> LensLaws l2 -> LensLaws (comp_lens l1 l2).
Proof.
  intros S M V l1 l2 H1 H2.
  split.
  - intros s. simpl.
    rewrite (law_getput H2 (lget l1 s)).
    apply (law_getput H1 s).
  - intros s v. simpl.
    rewrite (law_putget H1 s (lput l2 (lget l1 s) v)).
    apply (law_putget H2 (lget l1 s) v).
  - intros s v1 v2. simpl.
    rewrite (law_putget H1 s (lput l2 (lget l1 s) v1)).
    rewrite (law_putput H2 (lget l1 s) v1 v2).
    apply (law_putput H1 s (lput l2 (lget l1 s) v1) (lput l2 (lget l1 s) v2)).
Qed.

(** Product lens: two independent components. *)
Definition prod_lens {S1 S2 V1 V2 : Type}
           (l1 : Lens S1 V1) (l2 : Lens S2 V2) : Lens (S1 * S2) (V1 * V2) :=
  {| lget := (fun s => (lget l1 (fst s), lget l2 (snd s)));
     lput := (fun s v => (lput l1 (fst s) (fst v), lput l2 (snd s) (snd v))) |}.

Lemma prod_lens_laws : forall {S1 S2 V1 V2}
    (l1 : Lens S1 V1) (l2 : Lens S2 V2),
    LensLaws l1 -> LensLaws l2 -> LensLaws (prod_lens l1 l2).
Proof.
  intros S1 S2 V1 V2 l1 l2 H1 H2.
  split.
  - intros [a b]. simpl.
    f_equal; [apply (law_getput H1) | apply (law_getput H2)].
  - intros [a b] [v w]. simpl.
    f_equal; [apply (law_putget H1) | apply (law_putget H2)].
  - intros [a b] [v1 w1] [v2 w2]. simpl.
    f_equal; [apply (law_putput H1) | apply (law_putput H2)].
Qed.

(** Map lens over fixed-length vectors.  Alignment is by position, the
    same discipline used by Foster's [map] combinator for lists of
    equal length. *)
Definition map_lens {A B : Type} {n : nat} (l : Lens A B)
  : Lens (Vector.t A n) (Vector.t B n) :=
  {| lget := Vector.map (lget l);
     lput := Vector.map2 (lput l) |}.

Lemma map_lens_laws : forall {A B n} (l : Lens A B),
    LensLaws l -> LensLaws (map_lens (A := A) (B := B) (n := n) l).
Proof.
  intros A B n l H. split.
  - intros s.
    apply eq_nth_iff. intros p1 p2 Hp. subst p2. simpl.
    rewrite (@nth_map2 A B A (lput l) n s (Vector.map (lget l) s) p1 p1 p1 eq_refl eq_refl).
    rewrite (@nth_map A B (lget l) n s p1 p1 eq_refl).
    apply (law_getput H).
  - intros s v.
    apply eq_nth_iff. intros p1 p2 Hp. subst p2. simpl.
    rewrite (@nth_map A B (lget l) n (Vector.map2 (lput l) s v) p1 p1 eq_refl).
    rewrite (@nth_map2 A B A (lput l) n s v p1 p1 p1 eq_refl eq_refl).
    apply (law_putget H).
  - intros s v1 v2.
    apply eq_nth_iff. intros p1 p2 Hp. subst p2. simpl.
    rewrite (@nth_map2 A B A (lput l) n (Vector.map2 (lput l) s v1) v2 p1 p1 p1 eq_refl eq_refl).
    rewrite (@nth_map2 A B A (lput l) n s v1 p1 p1 p1 eq_refl eq_refl).
    rewrite (@nth_map2 A B A (lput l) n s v2 p1 p1 p1 eq_refl eq_refl).
    apply (law_putput H (nth s p1) (nth v1 p1) (nth v2 p1)).
Qed.

(** * Syntax-directed bidirectionalization *)

Inductive GetExpr : Type -> Type -> Type :=
| GId : forall A, GetExpr A A
| GComp : forall {A B C}, GetExpr A B -> GetExpr B C -> GetExpr A C
| GProd : forall {A1 A2 B1 B2},
    GetExpr A1 B1 -> GetExpr A2 B2 -> GetExpr (A1 * A2) (B1 * B2)
| GMap : forall {A B} (n : nat), GetExpr A B -> GetExpr (Vector.t A n) (Vector.t B n).

Fixpoint eval_get {S V : Type} (e : GetExpr S V) : S -> V :=
  match e with
  | GId A => (fun s => s)
  | GComp e1 e2 => (fun s => eval_get e2 (eval_get e1 s))
  | GProd e1 e2 => (fun s => (eval_get e1 (fst s), eval_get e2 (snd s)))
  | GMap n e => Vector.map (eval_get e)
  end.

Fixpoint bff {S V : Type} (e : GetExpr S V) : Lens S V :=
  match e with
  | GId A => id_lens A
  | GComp e1 e2 => comp_lens (bff e1) (bff e2)
  | GProd e1 e2 => prod_lens (bff e1) (bff e2)
  | GMap n e => map_lens (bff e)
  end.

(** BFF completeness for the structural fragment: the bidirectionalizer
    is total and returns a well-behaved lens for every [get] expression. *)
Theorem bff_laws : forall (S V : Type) (e : GetExpr S V), LensLaws (bff e).
Proof.
  induction e; simpl.
  - apply id_lens_laws.
  - apply (@comp_lens_laws A B C (bff e1) (bff e2) IHe1 IHe2).
  - apply (@prod_lens_laws A1 A2 B1 B2 (bff e1) (bff e2) IHe1 IHe2).
  - apply (@map_lens_laws A B n (bff e) IHe).
Qed.

Theorem bff_get_correct :
  forall (S V : Type) (e : GetExpr S V) (s : S),
    lget (bff e) s = eval_get e s.
Proof.
  induction e; simpl; intros.
  - reflexivity.
  - rewrite IHe1. apply IHe2.
  - destruct s; simpl. f_equal; auto.
  - apply eq_nth_iff. intros p1 p2 Hp. subst p2.
    rewrite (nth_map (lget (bff e)) s p1 p1 eq_refl).
    rewrite (nth_map (eval_get e) s p1 p1 eq_refl).
    apply IHe.
Qed.

(** * Semantic bidirectionalization is not complete for arbitrary gets *)

Definition and_get (s : bool * bool) : bool := andb (fst s) (snd s).

Lemma andb_pair_true : forall a b : bool,
    andb a b = true -> a = true /\ b = true.
Proof.
  destruct a, b; simpl; intros H; try discriminate; auto.
Qed.

Definition GetPutLaw {S V} (get : S -> V) (put : S -> V -> S) :=
  forall s, put s (get s) = s.
Definition PutGetLaw {S V} (get : S -> V) (put : S -> V -> S) :=
  forall s v, get (put s v) = v.
Definition PutPutLaw {S V} (get : S -> V) (put : S -> V -> S) :=
  forall s v1 v2, put (put s v1) v2 = put s v2.

Lemma and_get_eq_true : forall s : bool * bool,
    and_get s = true -> s = (true, true).
Proof.
  intros [[|] [|]]; simpl; intros H; try discriminate; reflexivity.
Qed.

Lemma no_lens_for_and_get : forall (put : (bool * bool) -> bool -> (bool * bool)),
    GetPutLaw and_get put -> PutGetLaw and_get put -> PutPutLaw and_get put -> False.
Proof.
  intros put HGP HPG HPP.
  assert (Ht_ff : put (false, false) true = (true, true)).
  { apply and_get_eq_true. apply (HPG (false, false) true). }
  assert (Ht_ft : put (false, true) true = (true, true)).
  { apply and_get_eq_true. apply (HPG (false, true) true). }
  assert (Ht_tf : put (true, false) true = (true, true)).
  { apply and_get_eq_true. apply (HPG (true, false) true). }
  (* PutPut after a [true] update followed by the original view [false]
     returns the original source, but all three sources collapse to the
     same intermediate [(true,true)]. *)
  assert (Hgp_ff : put (false, false) false = (false, false)).
  { specialize (HGP (false, false)). simpl in HGP. exact HGP. }
  assert (Hgp_ft : put (false, true) false = (false, true)).
  { specialize (HGP (false, true)). simpl in HGP. exact HGP. }
  assert (Hgp_tf : put (true, false) false = (true, false)).
  { specialize (HGP (true, false)). simpl in HGP. exact HGP. }
  pose proof (HPP (false, false) true false) as HPP1.
  rewrite Ht_ff, Hgp_ff in HPP1.
  pose proof (HPP (false, true) true false) as HPP2.
  rewrite Ht_ft, Hgp_ft in HPP2.
  pose proof (HPP (true, false) true false) as HPP3.
  rewrite Ht_tf, Hgp_tf in HPP3.
  congruence.
Qed.
