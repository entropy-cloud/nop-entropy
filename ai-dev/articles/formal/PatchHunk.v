From Stdlib Require Import List Arith Lia.
Import ListNotations.

Definition Line := nat.
Definition File := list Line.

Record Hunk := MkHunk { hname : nat; hoff : nat; hold : File; hnew : File }.

Set Implicit Arguments.
Unset Strict Implicit.

Definition inv_hunk (h : Hunk) : Hunk :=
  MkHunk (hname h) (hoff h) (hnew h) (hold h).

Lemma inv_hunk_involutive : forall h, inv_hunk (inv_hunk h) = h.
Proof. destruct h; reflexivity. Qed.

Inductive ApplyH : Hunk -> File -> File -> Prop :=
| mkApplyH :
    forall (h : Hunk) (pre post : File),
      length pre = hoff h ->
      ApplyH h (pre ++ hold h ++ post) (pre ++ hnew h ++ post).

Lemma applyH_inv_iff : forall h s t,
    ApplyH h s t <->
    exists pre post,
      length pre = hoff h /\
      s = pre ++ hold h ++ post /\
      t = pre ++ hnew h ++ post.
Proof.
  split.
  - intros H; inversion H; subst; eauto.
  - intros [pre [post [Hlen [Hs Ht]]]]; subst. constructor. exact Hlen.
Qed.

Lemma applyH_inv_hunk : forall h s t,
    ApplyH h s t -> ApplyH (inv_hunk h) t s.
Proof.
  intros h s t H.
  apply applyH_inv_iff.
  inversion H; subst; simpl.
  exists pre, post. simpl. auto.
Qed.

Inductive CommuteHunkAt : File -> Hunk -> Hunk -> Hunk -> Hunk -> Prop :=
| commute_q_before_p_at :
    forall (pre mid post : File) (np nq : nat)
           (oldp newp oldq newq : File),
      CommuteHunkAt
        (pre ++ oldq ++ mid ++ oldp ++ post)
        (MkHunk np (length pre + length oldq + length mid) oldp newp)
        (MkHunk nq (length pre) oldq newq)
        (MkHunk nq (length pre) oldq newq)
        (MkHunk np (length pre + length newq + length mid) oldp newp)
| commute_q_after_p_at :
    forall (pre mid post : File) (np nq : nat)
           (oldp newp oldq newq : File),
      CommuteHunkAt
        (pre ++ oldp ++ mid ++ oldq ++ post)
        (MkHunk np (length pre) oldp newp)
        (MkHunk nq (length pre + length newp + length mid) oldq newq)
        (MkHunk nq (length pre + length oldp + length mid) oldq newq)
        (MkHunk np (length pre) oldp newp).

Lemma commute_hunk_square :
  forall A p q q' p',
    CommuteHunkAt A p q q' p' ->
    exists B B' C,
      ApplyH p A B /\
      ApplyH q B C /\
      ApplyH q' A B' /\
      ApplyH p' B' C.
Proof.
  intros A p q q' p' H. inversion H; subst; simpl.
  - exists (pre ++ oldq ++ mid ++ newp ++ post).
    exists (pre ++ newq ++ mid ++ oldp ++ post).
    exists (pre ++ newq ++ mid ++ newp ++ post).
    repeat split.
    + apply applyH_inv_iff. exists (pre ++ oldq ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity.
    + apply applyH_inv_iff. exists (pre ++ newq ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity.
  - exists (pre ++ newp ++ mid ++ oldq ++ post).
    exists (pre ++ oldp ++ mid ++ newq ++ post).
    exists (pre ++ newp ++ mid ++ newq ++ post).
    repeat split.
    + apply applyH_inv_iff. exists (pre ++ newp ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity.
    + apply applyH_inv_iff. exists (pre ++ oldp ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity.
Qed.


Lemma commute_hunk_self_inverse :
  forall A p q q' p',
    CommuteHunkAt A p q q' p' ->
    CommuteHunkAt A q' p' p q.
Proof.
  intros A p q q' p' H.
  inversion H; subst; simpl.
  - apply (commute_q_after_p_at pre mid post nq np oldq newq oldp newp).
  - apply (commute_q_before_p_at pre mid post nq np oldq newq oldp newp).
Qed.

Lemma commute_hunk_inv_dual :
  forall A p q q' p',
    CommuteHunkAt A p q q' p' ->
    exists C,
      CommuteHunkAt C (inv_hunk q) (inv_hunk p) (inv_hunk p') (inv_hunk q').
Proof.
  intros A p q q' p' H.
  inversion H; subst; simpl.
  - exists (pre ++ newq ++ mid ++ newp ++ post). simpl.
    apply (commute_q_after_p_at pre mid post nq np newq oldq newp oldp).
  - exists (pre ++ newp ++ mid ++ newq ++ post). simpl.
    apply (commute_q_before_p_at pre mid post nq np newq oldq newp oldp).
Qed.


Lemma firstn_prefix : forall (pre rest : File) n,
    length pre = n -> firstn n (pre ++ rest) = pre.
Proof.
  intros pre rest n Hlen.
  rewrite firstn_app.
  rewrite <- Hlen.
  rewrite firstn_all.
  rewrite Nat.sub_diag.
  rewrite firstn_0.
  apply app_nil_r.
Qed.

Lemma applyH_deterministic : forall h s t1 t2,
    ApplyH h s t1 -> ApplyH h s t2 -> t1 = t2.
Proof.
  intros h s t1 t2 H1 H2.
  apply applyH_inv_iff in H1. apply applyH_inv_iff in H2.
  destruct H1 as [pre1 [post1 [Hlen1 [Hs1 Ht1]]]].
  destruct H2 as [pre2 [post2 [Hlen2 [Hs2 Ht2]]]].
  assert (Heq_full : pre1 ++ hold h ++ post1 = pre2 ++ hold h ++ post2)
    by congruence.
  assert (Heq_pre : firstn (hoff h) (pre1 ++ hold h ++ post1)
                      = firstn (hoff h) (pre2 ++ hold h ++ post2))
    by (f_equal; exact Heq_full).
  rewrite (@firstn_prefix pre1 (hold h ++ post1) (hoff h) Hlen1) in Heq_pre.
  rewrite (@firstn_prefix pre2 (hold h ++ post2) (hoff h) Hlen2) in Heq_pre.
  assert (Hpre : pre1 = pre2) by exact Heq_pre.
  rewrite Hpre in Heq_full.
  apply app_inv_head in Heq_full.
  apply app_inv_head in Heq_full.
  assert (Hpost : post1 = post2) by exact Heq_full.
  subst pre2. subst post2. rewrite Ht1. rewrite Ht2. reflexivity.
Qed.

Lemma commute_hunk_inv_dual_ctx :
  forall A B C p q q' p',
    ApplyH p A B -> ApplyH q B C ->
    CommuteHunkAt A p q q' p' ->
    CommuteHunkAt C (inv_hunk q) (inv_hunk p) (inv_hunk p') (inv_hunk q').
Proof.
  intros A B C p q q' p' HpB HqC HC.
  inversion HC; subst; simpl.
  - assert (HpB0 : ApplyH (MkHunk np (length pre + length oldq + length mid) oldp newp)
                           (pre ++ oldq ++ mid ++ oldp ++ post)
                           (pre ++ oldq ++ mid ++ newp ++ post)).
    { apply applyH_inv_iff. exists (pre ++ oldq ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity. }
    assert (HB : B = pre ++ oldq ++ mid ++ newp ++ post)
      by (exact (applyH_deterministic HpB HpB0)).
    subst B.
    assert (HqC0 : ApplyH (MkHunk nq (length pre) oldq newq)
                           (pre ++ oldq ++ mid ++ newp ++ post)
                           (pre ++ newq ++ mid ++ newp ++ post)).
    { apply applyH_inv_iff. exists pre, (mid ++ newp ++ post). simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity. }
    assert (HCeq : C = pre ++ newq ++ mid ++ newp ++ post)
      by (exact (applyH_deterministic HqC HqC0)).
    subst C.
    simpl.
    apply (commute_q_after_p_at pre mid post nq np newq oldq newp oldp).
  - assert (HpB0 : ApplyH (MkHunk np (length pre) oldp newp)
                           (pre ++ oldp ++ mid ++ oldq ++ post)
                           (pre ++ newp ++ mid ++ oldq ++ post)).
    { apply applyH_inv_iff. exists pre, (mid ++ oldq ++ post). simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity. }
    assert (HB : B = pre ++ newp ++ mid ++ oldq ++ post)
      by (exact (applyH_deterministic HpB HpB0)).
    subst B.
    assert (HqC0 : ApplyH (MkHunk nq (length pre + length newp + length mid) oldq newq)
                           (pre ++ newp ++ mid ++ oldq ++ post)
                           (pre ++ newp ++ mid ++ newq ++ post)).
    { apply applyH_inv_iff. exists (pre ++ newp ++ mid), post. simpl.
      repeat split; try (rewrite !length_app); try lia;
        try (rewrite <- !app_assoc); try reflexivity. }
    assert (HCeq : C = pre ++ newp ++ mid ++ newq ++ post)
      by (exact (applyH_deterministic HqC HqC0)).
    subst C.
    simpl.
    apply (commute_q_before_p_at pre mid post nq np newq oldq newp oldp).
Qed.


Definition MergeAt (O : File) (p q r s : Hunk) : Prop :=
  exists A B C,
    ApplyH p O A /\
    ApplyH q O B /\
    ApplyH r A C /\
    ApplyH s B C.

Definition merge_by_commute (O : File) (p q r s : Hunk) : Prop :=
  exists A B,
    ApplyH p O A /\
    ApplyH q O B /\
    CommuteHunkAt A (inv_hunk p) q r (inv_hunk s).

Theorem merge_by_commute_correct :
  forall O p q r s,
    merge_by_commute O p q r s -> MergeAt O p q r s.
Proof.
  intros O p q r s H.
  destruct H as [A [B [Hp [Hq HC]]]].
  assert (Hip : ApplyH (inv_hunk p) A O) by (apply applyH_inv_hunk; exact Hp).
  destruct (@commute_hunk_square A (inv_hunk p) q r (inv_hunk s) HC)
    as [O' [M [C' [Hip2 [Hq2 [Hr His]]]]]].
  assert (HO' : O' = O).
  { exact (applyH_deterministic Hip2 Hip). }
  subst O'.
  assert (HC' : C' = B).
  { exact (applyH_deterministic Hq2 Hq). }
  subst C'.
  assert (Hs : ApplyH s B M).
  {
    assert (Hs0 : ApplyH (inv_hunk (inv_hunk s)) B M).
    { apply (@applyH_inv_hunk (inv_hunk s) M B His). }
    rewrite inv_hunk_involutive in Hs0.
    exact Hs0.
  }
  exists A, B, M. auto.
Qed.

(** Merge symmetry: swapping the two starting patches swaps the two
    merged patches.  This is the Darcs "merge either way" law at the
    hunk level. *)
Theorem merge_hunk_symmetric :
  forall O p q r s,
    merge_by_commute O p q r s ->
    merge_by_commute O q p s r.
Proof.
  intros O p q r s H.
  destruct H as [A [B [Hp [Hq HC]]]].
  assert (Hip : ApplyH (inv_hunk p) A O).
  { apply (@applyH_inv_hunk p O A Hp). }
  pose proof (@commute_hunk_inv_dual_ctx A O B (inv_hunk p) q r (inv_hunk s)
               Hip Hq HC) as Hdual.
  simpl in Hdual.
  rewrite (inv_hunk_involutive s) in Hdual.
  rewrite (inv_hunk_involutive p) in Hdual.
  exists B, A.
  repeat split.
  - exact Hq.
  - exact Hp.
  - exact Hdual.
Qed.
