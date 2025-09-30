
Analyze the following article and write a detailed commentary and interpretive piece focused on its core content.
1. It must be clear that what you are writing is a commentary, and it should entice readers to go back to the original. It should not appear to be your original article.
2. It is not a retelling or paraphrase of the original. Rearrange the priorities and coverage, dissect the core points and subtle aspects, and emphasize what matters. Provide deep analysis and critical commentary.
3. Naturally weave in interpretations of the “golden lines”.
4. Draw on your own knowledge, focus on the genuine innovations in the original, analyze its pros, cons, and the insights it offers, and clearly distinguish the parts that are truly inspiring and insightful. Routine content can be skipped—this is a commentary, not an exhaustive summary.
5. The writing should be professional, objective, and easy to understand—avoid flamboyance, but keep it insightful and thought-provoking.
6. Include enough information in your interpretation so that readers who haven’t read the original can still extract the core ideas, especially the innovations and why they matter.
7. End with an objective evaluation of the original piece.
8. The output should reach 5,000–10,000 words.

The article to be analyzed is as follows:

Paxos is a foundational algorithm in distributed systems, long-renowned for being opaque and mind-bending. The reason it feels so hard to grasp is mainly that we don’t intuitively understand why it is designed the way it is. While we can verify correctness through concrete examples, and even persuade ourselves with rigorous proofs, we still struggle to answer: why must it be this way? Is it the only viable approach? Could there be an explanation—one that doesn’t rely on mathematical derivation—that makes the design of Paxos feel self-evident to our intuition?

In my article “[A Magical Study Report on Paxos](https://mp.weixin.qq.com/s/CVa_gUdCtdMEURs40CiXsA),” I constructed a “magical image” of Paxos through the lens of extradimensional magic, aiming to provide a “why,” rather than just explaining how Paxos works. This piece supplements that article.

For basic knowledge of the Paxos algorithm, refer to xp’s posts on Zhihu: [200 lines of code to implement a Paxos-based KV store](https://zhuanlan.zhihu.com/p/275710507), [Reliable Distributed Systems — an intuitive explanation of Paxos](https://zhuanlan.zhihu.com/p/145044486)

## I. Why learn Paxos at all?

Some may ask: my work has little to do with distributed systems; is it necessary to learn Paxos? The answer is yes. As long as your problems involve multiple state spaces or coordinating multiple independent actors, you will encounter analogous issues. The approach Paxos offers is instructive far beyond its canonical domain.

A natural objection is that Raft is more popular nowadays, and Paxos seems less common. Here’s how to see it: Raft is essentially a variant of Paxos. It picks particular implementation strategies guided by Paxos’s underlying principles.

More precisely, Paxos can be treated as a general framework for consensus in distributed systems. It defines the core mechanisms needed to reach consensus. Raft is a specific instantiation of that framework—it introduces additional rules and steps to achieve consensus, which may offer convenience but are not strictly necessary. If you understand Paxos, you can more easily understand Raft and the principles behind other consensus protocols.

## II. What is the Paxos algorithm?

Paxos solves the simplest consensus problem in distributed systems: how multiple nodes reach agreement on a single value despite failures.

A correct consensus algorithm must satisfy:

**Agreement**: All nodes must agree on the same value, and once agreed, the value never changes.

**Validity**: The agreed-upon value must originate as a proposal from some node.

**Termination**: Eventually, all nodes reach agreement.

These are often called Safety + Liveness—a classic case of wanting both correctness and feasibility. Agreement captures the essence of consensus; validity rules out trivialities (e.g., all nodes decide to always choose the value 3 regardless of inputs—indeed consensus, but uselessly static).

The consensus scenario is: initially the system is uncertain, allowing many possibilities (e.g., both x and y could be chosen). But after some pivotal action, the system abruptly transitions—akin to a phase change in physics, like water freezing—into a globally coordinated, deterministic state (locked to a chosen value). If the algorithm proceeds by its rules, ultimately all nodes will acknowledge that the value must be x and cannot be y.

> If we linearize all actions taken by all nodes in a consensus protocol into an action sequence, there must exist a critical action: before it, multiple outcomes remain possible; after it, the result is fixed. For example, once an Acceptor records a value and a majority is formed, the value becomes fixed. Without such a record, there’s no majority yet, so new possibilities remain.
> 
> Although all nodes run concurrently, we can always retrospectively linearize all actions and identify the critical transition.

Curiously, can the system slip into a Schrödinger-cat-like state—both having chosen and not chosen a value? From an observer’s perspective, such states can indeed occur. But Paxos solves this: it embeds an observation mechanism ensuring eventual wavefunction collapse to a definite outcome.

### The FLP theorem

Unfortunately, a consensus algorithm satisfying all three properties above is impossible in the absolute sense! The FLP theorem (Fischer, Lynch, and Paterson) states that in a completely asynchronous distributed system, no consensus algorithm can simultaneously guarantee agreement, reliability, and termination.

The asynchronous model has no global clock; processes run at arbitrary speeds; messages can arrive at arbitrary times—though eventual delivery is guaranteed.

FLP essentially says: if an omniscient, omnipotent adversarial deity maliciously delays progress toward consensus, suspending key processes at critical moments (right before pivotal transitions), no algorithm can ensure consensus. Fortunately, in our world, no such bored deity has been found—try a few times, and you’ll eventually get lucky.

### Paxos at a glance

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos-diagram.webp)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/phase1.png)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/phase2.png)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos-algorithm.png)

The images above are from Dengcheng He’s PPT at Alibaba Infrastructure Division, [PaxosRaft Distributed Consensus Principles and Their Practical Applications.pdf](https://github.com/hedengcheng/tech/blob/master/distributed/PaxosRaft%20%E5%88%86%E5%B8%83%E5%BC%8F%E4%B8%80%E8%87%B4%E6%80%A7%E7%AE%97%E6%B3%95%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E5%8F%8A%E5%85%B6%E5%9C%A8%E5%AE%9E%E6%88%98%E4%B8%AD%E7%9A%84%E5%BA%94%E7%94%A8.pdf)

Definition of a chosen value: A value is chosen if a majority (more than half) of acceptors accept it.

### Relativity, time, and distributed systems

[Time, Clocks and the Ordering of Events in a Distributed System (1978)](https://dl.acm.org/doi/pdf/10.1145/359545.359563)

This is Lamport’s most cited paper and often called the most important paper in distributed systems. In reflecting on it, Lamport wrote:

> The origin of this paper was the note [The Maintenance of Duplicate Databases](http://www.rfc-archive.org/getrfc.php?rfc=677) by Paul Johnson and Bob Thomas. I believe their note introduced the
> idea of using message timestamps in a distributed algorithm. I happen to
>  have a solid, visceral understanding of special relativity .
>  This enabled me to grasp immediately the essence of what they were
> trying to do. Special relativity teaches us that there is no invariant
> total ordering of events in space-time; different observers can disagree
>  about which of two events happened first. There is only a partial order
>  in which an event e1 precedes an event e2 iff e1 can causally affect
> e2. I realized that the essence of Johnson and Thomas’s algorithm was
> the use of timestamps to provide a total ordering of events that was
> consistent with the causal order. This realization may have been
> brilliant. Having realized it, everything else was trivial. **Because
> Thomas and Johnson didn’t understand exactly what they were doing**, they didn’t get the algorithm quite right; their algorithm permitted
> anomalous behavior that essentially violated causality. I quickly wrote a
>  short note pointing this out and correcting the algorithm.

Lamport has a B.S. in Mathematics from MIT (1960) and a Ph.D. in Mathematics from Brandeis University (1972), and studied special relativity systematically. His recollection underscores that to master computer science, you must know a bit of physics.

Einstein imagined firing a photon to probe the world and uncovered a shocking truth: times at different locations are only partially ordered by causality; the order of events depends on the observer. After seeing concepts like message sending and message timestamps in others’ work, Lamport immediately realized that message passing and photon propagation are one and the same at a deeper level: the physical picture underneath is special relativity. Once he saw this, the rest was trivial logic! Thomas and Johnson, by contrast, suffered from not understanding the physics; they didn’t grasp what their algorithm was actually doing, leading to subtle flaws. Without the guiding physical image, confusion at critical moments is all but inevitable.

Interestingly, although Lamport was guided by special relativity, his paper discusses logical clocks at length without mentioning relativity. This has led some to think that the idea came as a bolt from the blue.

Lamport’s original Paxos paper, [The Part-Time Parliament](https://ying-zhang.github.io/dist/1989-paxos-cn/), finally saw formal publication in 1998. Many readers cried out in confusion, so in 2001 Lamport wrote [Paxos Made Simple](https://www.jianshu.com/p/1bbbfbe300d1)—openly stating: “The Paxos algorithm, when presented in plain English, is very simple.” In that paper he offers a “why” for the design of Paxos—but the explanation proceeds via step-by-step mathematical derivation, effectively forcing readers to concede the algorithm’s reasonableness. The result: a lot of people felt they “got it” at first glance but quickly drifted into confusion.

Physics research emphasizes physical intuition. Physicists never slavishly derive results by formal rules; they believe a derivation because the outcome matches a sound physical explanation. So with Paxos, what is the underlying physical picture? Does Lamport have, deep down, a non-mathematical understanding of Paxos’s necessity—just as he once withheld relativity from those computer folks?

## III. A magical image of Paxos

The backdrop of distributed systems is a chaotic expanse of birth’s freedom and death’s randomness, rife with conflict. And yet Paxos builds a unified, consistent world of consensus atop this chaos—an apparent miracle. But mortals struggle to understand miracles. Unable to see from the god’s vantage, they can only infer the divine intent from limited experience—hence mortal confusion.

The difference between gods and mortals lies in the domain: in the divine realm, words shape laws. Making rules is godhood’s starting point; humbly accepting and cleverly exploiting rules is the essence of humanity. This is the gulf between heaven and man. But if humankind nurtures rebellion and imagines itself as a master of rules, might it grasp what seems inexplicable? A single thought of transgression, and the world opens wide. The divine solution to consensus can be stated in three steps:

1. God said: Let there be time.

2. God said: Let time stand still.

3. God said: Let the value be X.
* Time must exist before it can be frozen.

* If time exists, and we freeze time everywhere in the universe, then nothing unexpected can happen; the deity can calmly do anything. We perceive time through observable change—compare a pendulum’s swing with other changes. If time stands still, it means no observable change occurs. But that doesn’t mean absolutely nothing changes. For example, in a game, if you hit pause and later resume, NPCs never experienced a change.

* After time is frozen, the god directly sets the same value in multiple places. When time resumes, people in different locations find the same value suddenly present—consensus is achieved.

Today’s world is in the age of short-lived magic; spiritual energy is exhausted; magical power has faded; true magic no longer exists. Yet we still have computers in our hands. If the substrate of our world were a giant computer, physical laws might be simulated by this machine. In this low-magic world, can we simulate high-magic with our computers?

> A computer is essentially a Turing machine; a Turing machine is a universal simulator that can simulate any computation. That is what Turing completeness means.

Paxos is a simulated implementation of the ninth-level spell: Time Freeze.

Once you realize Paxos’s secret lies in extradimensional magic, the rest is just pedestrian technical detail.

> What’s needed here is a shift in worldview or cognitive paradigm: we design a natural law to reach a goal, then figure out how to realize that law. It’s like programming—define an interface first, then implement it.

Recall: the actions by Proposers and Acceptors in Paxos essentially ensure that time flows forward.

1. Proposers generate globally unique, monotonically increasing Proposal IDs. These IDs are markers of logical time; each ID corresponds to a unique instant.

2. When an Acceptor receives a Propose, why does it refuse to respond to any Propose with a Proposal ID less than or equal to the current Propose? Because time flows forward. A successful Propose marks the start of time freeze at moment t. You cannot freeze at any moment earlier than t after freezing at t. Nor should another Proposer attempt to freeze at the same t. Thus an Acceptor refuses Proposal IDs equal to the current Propose.

3. When an Acceptor receives a Propose, why does it refuse to respond to Accept requests with Proposal IDs less than the current Propose? From Propose to Accept is the freezing phase. We can accept an Accept matching the freeze-start t, but we cannot accept Accepts for earlier moments than t.

In this low-magic world we simulate magic using cognitive deletion: we delete from our cognition all facts that violate magical principles. What we can’t see doesn’t exist! The Acceptor’s seemingly odd behaviors are simply ignoring facts that would “break the illusion” of Time Freeze.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/time_arrow.png)

Each Acceptor records a strictly non-decreasing Proposal ID—its local time arrow. Aligning Proposal IDs among Acceptors aligns local time arrows to a single point, bundling them into a coarse-grained, global time arrow. The flow of time resembles a wavefront sweeping across the system.

From the god’s perspective, Paxos is a minor trick that forcibly aligns multiple timelines into a single master timeline using Time Freeze.

> Note: this “time” is logical time we define ourselves, not physical time.

### Interesting application: Stop–Align and optimistic locking

This “stop–align” technique is a basic strategy for consensus in distributed systems. For instance, in Kafka, consumers within the same group act independently, but they must agree on how to divide work. When group membership or topic structure changes, a rebalance is triggered. During rebalance, the Coordinator first asks all workers to stop ongoing work and collectively switch to the next epoch. Only then is a new assignment delivered. An assignment is valid only within its epoch.

Optimistic locking in databases uses a similar approach. At handler entry, read the version of a MainRecord; then modify the MainRecord and associated SubRecords; finally commit all in a transaction while trying to update the main record’s version.

```
 update MainRecord
 set version = version + 1
 where version = :record_version
```

If the update succeeds, it means time remained frozen throughout the process—no conflicting actions occurred.

### Micro and macro

Local time arrows can be interrupted by failures and anomalies. We need a sense of the big picture—a distinction between micro and macro. Microscopic success may be patchy, but if a majority succeeds, we define the operation as a macroscopic success. A set cannot contain two majorities simultaneously; a majority cannot both choose X and choose Y; hence the ascending route from micro to macro is well-defined.

A key to understanding Paxos is that we focus only on events that ascend to the macroscopic level—that is, events on the master timeline. Each point on the master timeline corresponds to a micro-world process interval from freeze-start to freeze-end. At each point a value may be set; success means consensus achieved. The effect of Time Freeze ultimately manifests fully on the master timeline: as isolated points that never overlap, corresponding to non-overlapping process intervals in the micro worlds.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_phase2.png)

Only events occurring in most micro worlds ascend to the master world and become master-world events. The first step of Paxos is to obtain a majority promise from Acceptors. If successful, the majority’s time is aligned to one instant, creating a new master-time point. In other words, only time points recognized by a majority appear on the master timeline, and they record the start of Time Freeze there. If multiple Proposers compete concurrently, they may access different Acceptors with the same Proposal ID but fail to obtain a majority; that moment is discarded and won’t appear on the master timeline. There may be multiple competition attempts at the micro level; on the master timeline we observe only competition successes recognized by a majority. In analysis, we need only consider the master timeline.

Since competition can fail and Proposers pick Proposal IDs independently, visible Proposal IDs on the master timeline are not continuous. But absolute logical times are unimportant; we need only their relative ordering. If needed, once the master timeline is established we can reassign sequential indices starting at 0.

Mutual exclusion in a single instant ensures uniqueness and determinism at the macro level: if a majority at t picks A, there cannot be another majority at t picking B. Once most micro worlds have written value A at t, that fact cannot be overridden by subsequent events—no second voice can oppose it. Only then does the master world record the event of writing A.

> Proposer to a majority of Acceptors: Freeze time at t3.
> 
> Majority of Acceptors reply success: Agreed. Time has arrived at t3. By the way, the last value written at time t was X.
> 
> Proposer: When time is frozen at t3, set the value to X.
> 
> Majority of Acceptors: Processing success: the value at t3 has been set to X.
> 
> Note: As long as a majority processes successfully, a reply is unnecessary—consensus has already been reached.

### Interesting application: avoiding split brain

In leader election, a classic challenge is avoiding split brain. What if a new leader enjoys popular support, but the old one refuses to abdicate and keeps meddling? A general solution is to declare the old leader a zombie and utterly ignore all information from a previous epoch (e.g., reject all requests with smaller epochs). In fact, we never constrained the old leader’s behavior; in its own micro world it can do whatever it wants. But its actions won’t ascend to the collective will and won’t affect the master world. When the new leader assumes office, it should “write before read”: stamp its epoch on the master world first (akin to writing a global shared variable). Then, when the old leader later tries to commit, optimistic lock reveals it has lost power; it must abandon its results.

In our physical universe, with the development of quantum mechanics, observation and measurement have acquired profound theoretical meaning. In quantum field theory’s view, in an invisible imaginary-time domain, countless wild entities compete and annihilate, and only a composite outcome emerges in reality. Through the quirky tunnel of quantum effects, we glimpse the raging storm beneath.

## IV. Monotonicity: collapse from Schrödinger’s cat

Paxos phase 2 contains a puzzling step: after collecting a majority of Propose replies, if any reply contains a value, the Proposer must choose the value from the reply with the largest Proposal ID as the value to be accepted. Why abandon your original proposal and choose someone else’s? And why specifically the one with the largest Proposal ID?

These questions stem from mortal limitations.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

Consider 5 Acceptors with multiple Proposers. At ProposalID = t1, proposal P1 is accepted by A1 and A2, but not by a majority, so no value is determined in this round. ProposalID = t2 with P2 similarly fails. ProposalID = t3 with P3 is accepted by the majority A2, A3, A4, achieving consensus.

After consensus at t3, could we reach a new consensus P4 at t4? Then the t3 consensus is P3, the t4 consensus is P4, while t1 and t2 have none. For a god, selecting different values at different instants is perfectly fine—no problem, as gods are omniscient. But mortals, blunt and limited, suffer cognitive overload if different instants can host different consensuses.

If we allow consensus to be overturned, how does a mortal with limited cognition know which value to use? Many moments may have no consensus (e.g., t1 and t2). Must we scan all moments from t1 to tn to extract every consensus? That’s why foundational consensus algorithms demand: once consensus is reached, it remains fixed.

> In fact, the definition of consensus already rules out overturning it after it’s reached.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus_fail.png)

Now consider the figure above. Suppose A3 crashes while processing P3. From the outside, two cases exist:

1. A3 accepted P3, so consensus is achieved.
2. A3 did not accept P3, so consensus is not achieved.

Except for A3, no one knows what happened. But A3 is down—it cannot answer! If different instants can have different consensuses, we can be trapped in an awkward situation: history is in a quantum indeterminate state, unanswerable with a simple yes or no.

For mortals, the ideal is a system with monotonicity: it moves forward, and once it hits the target state, it remains there forever. Then whenever we want to extract information, we push the system forward one step. If consensus is already achieved, one more step yields the same consensus value; if not, our step will choose an actual value, escaping indeterminacy. In the example above, continue running Paxos one more step: regardless of A3’s choice at t3, we will get P3 at t4, thereby eliminating uncertainty after t4. In Yubai’s posts, this is called the “maximum-commit principle.”

From the master-world perspective, random fluctuations in micro worlds mean macroscopic facts may sit in Schrödinger-cat-like states. Reading replies from a majority of Acceptors at phase 1 is akin to an observation on the master timeline with side effects.

1. We know facts on the master timeline are time-ordered; consensus is a write occurring at a master-time point. Keeping consensus consistent is easiest with “read before write”: peek at what came before.

2. If the majority replies deliver the same value and instant t, then t’s value is already determined—consensus on the master timeline has already been achieved. No need to proceed; simply return.

3. If the majority replies disagree on the value or t, the prior instant may or may not have achieved consensus; the system is indeterminate. In this case, we must ensure that if a consensus exists, our subsequent behavior won’t break it. That is, if a majority at t−1 accepted value X, then at t we cannot propose a different Y. If consensus exists, then the consensus value must be the value with the largest Proposal ID. Why? If consensus was reached at t3, then t4, immediately following, must have seen t3’s result; and since consensus cannot be overturned, t4’s value must be the consensus value. Therefore, if the largest Proposal ID’s value is not the consensus value, then consensus could not have been reached earlier. Paxos evolution is monotone on the master timeline: from “no value” to “uncertain whether value exists” to “determinately has a value.” We therefore need only check the last step’s result. Because each Proposal ID is used by at most one Proposer once, a Proposal ID corresponds to at most one value.

4. In phase 2, after reading a value from a majority, the Proposer essentially abandons a write; it devolves into pure observation, with a side effect: the state at a master-time point collapses from “may or may not be set” to “definitely set.” That is, no new value is written; we choose among possible values—just like observation in quantum mechanics.
   
   $$
   |X\rangle + |0\rangle \longrightarrow |X\rangle
   $$

5. If phase 2 succeeds, it is effectively an atomic read+write operation after time is frozen.

> In a Schrödinger-cat state, the system is a quantum superposition; when unobserved the cat is neither dead nor alive, but a superposition of both. Upon observation, the state collapses to either dead or alive.

Returning to the earlier example, there’s a subtlety. At t1, only two P1 entries were set; no consensus. At t2, two P2 entries; no consensus. At t3, the majority must have seen P1 or P2; thus P3 must be either P1 or P2—not arbitrary. If all majority replies are empty, we can safely conclude no consensus yet. If some have values, for safety, Paxos chooses among existing values. Suppose we received five responses at t3 and explicitly know no consensus was reached earlier. If we don’t follow Paxos’s choice and pick a different value, would this cause a conflict? Traditional Paxos learners often get confused—they see this as deviating from the original analysis. But with the Time Freeze + master timeline picture, the answer is clear: no conflict. Values on the master timeline are updated by Time Freeze; they are separated and ordered. A master-time point may be uncommitted (if three nodes report “not written” then definitely uncommitted), unknown (we know attempts were made), or committed (three nodes report the same time point’s value). Transitions among these are one-way. Only the committed state affects correctness: if committed and we write a different value, that conflicts; but if we know it is uncommitted, there’s no contradiction.

### When is consensus actually achieved?

When consensus is determined, do participants immediately realize it? Interestingly, at the instant consensus is achieved, none of the participants—Acceptors or Proposers—knows this. Over time, the algorithm’s progression gradually reveals the fact of consensus.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

Before consensus is reached, an Acceptor can change its accepted value—for example, A3 first accepts P2, then P3. Since Proposers may fail or disconnect at any time, Acceptors must be willing to accept newer values. Thus, when A3 accepts P3, it cannot know that P3 is the final consensus. Similarly, A2 and A4 only see local states and cannot judge the system’s global consensus. On the Proposer side, before receiving a majority’s success replies, it cannot know whether P3 will be accepted by a majority. Consensus is global; single participants need time to comprehend it. This underscores the importance of the Learner role. Learners first identify that consensus has been reached, then disseminate this information—so each participant doesn’t have to independently collect and reason.

There’s another interesting detail. At t2, if phase 1’s majority replies come from A3, A4, A5, we can propose any P2 (if the majority included A1 and A2, then by Paxos rules P2 must equal P1). But writing to a majority in phase 2 need not target A3, A4, A5; any majority will do. We can write a P2 different from P1 to A1. The fact that you can write at t implies no intervening changes occurred after t. Lamport emphasizes this in his paper: “This need not be the same set of acceptors that responded to the initial requests.” Without the master timeline picture, it’s easy to get confused here.

## V. Leader-based: copy and paste

Casting ninth-level magic is costly. A diligent, thrifty god with socialist core values won’t waste magic. Hence, once time is frozen, the best way to maintain consistent behavior across nodes is to cast an eighth-level spell—Grand Puppet Mastery—replicate a leader’s behavior to all other nodes.

This replication springs from divine power. Once the leader initiates a new action, it traverses mountains and seas, ignoring physics, descending upon followers; followers cannot oppose—only execute. Still, as the saying goes: “Orders given above; legs worn out below.” In our low-magic world, implementing Grand Puppet Mastery is not easy. Typically we add logs on sender and receiver.

The sender writes decisions to the log, turning them into immutable oracles. The sending component scans the log system to ensure delivery to remote endpoints. If it cannot connect, or sending fails, or no expected response arrives, the sender must not complain or give up; it must keep trying—retry until success. This provides “at least once” delivery. The receiver must unconditionally accept all messages, without rejecting or tampering. Because duplicate messages can arrive, it must perform idempotency checks via its local log, filtering duplicates to achieve “at most once” processing. If a streaming pipeline relays messages, to avoid replays from the source on each retry, intermediate stages must record completed results via snapshots.

> End-to-end “exactly once” = initiator’s “at least once” + processor’s “at most once”.

MultiPaxos and Raft are concrete implementations of this replication strategy. Once a leader is elected, the term ID can be reused many times to issue commands, as long as log indices distinguish them.

If we look closely, messages can be grouped into two types: requests, where the receiver chooses how to process and may succeed or fail; and notices, which have fixed semantics—the receiver can’t argue.

A classic example is two-phase commit (2PC). In Prepare, participants receive a request; they can choose to commit or abort. Once they reply with their potential choices to the coordinator, they surrender autonomy, promising to accept notices and align behavior with the coordinator. If the coordinator decides to commit, participants must commit and cannot abort. If a participant aborts, the coordinator can only abort. Their choices are no longer independent—they are entangled.

### 2PC and quantum entanglement

In 2PC, the coordinator supplies the source of consistency; participants gradually entangle with the coordinator. In Paxos, we progressively build a quorum; participants entangle within the quorum.

Before 2PC runs, each participant can independently choose success or failure—purely random. After phase 1, if we observe a single participant, it is still success-or-failure randomly. But if we observe the overall state space, feasible states are reduced; only entangled states remain:

$$

|success, success\rangle + |failure, failure\rangle
$$

On quantum entanglement, here is an explanation from Zhipu Qingyan AI:

Quantum entanglement is a special, non-classical phenomenon in quantum mechanics. It describes a strong correlation between two or more particles such that even if the particles are far apart, their states can instantaneously influence each other.

Suppose we have particles A and B prepared in a special quantum state, e.g., a Bell state:

$$
\frac{1}{\sqrt{2}} (|00\rangle + |11\rangle)
$$

Here “0” and “1” denote two possible states (e.g., spin), and $|00\rangle$ means both A and B are in “0,” while $|11\rangle$ means both are in “1.”

#### Behavior of entangled particles

When A and B are in such an entangled state, regardless of distance:

- Consistency under measurement: If we measure A and find “0,” B is instantly determined to be “0.” If B is “1,” A is “1.” This immediate correlation is a key feature of entanglement.
- Randomness: Upon measuring A or B, we cannot predict whether the outcome is “0” or “1,” since the entangled state is a superposition of both. But once one is measured, the other is instantly determined in correlation.
- Nonlocality: Quantum entanglement manifests nonlocality: A’s state can instantly affect B’s state without any signal transmission—violating classical locality.

===========Zhipu Qingyan AI creation completed========

### The most fundamental difficulty in distributed systems: not knowing

To mortals, the world is riddled with aggravating uncertainty. Every action can produce three outcomes: success, failure, and unknown. Single-machine systems once offered a utopian illusion of binary outcomes—good and bad, success and failure, light and darkness. Reality makes us sober. In a world ruled by chance, this intrinsic uncertainty is the essence of distributed difficulty.

When the result of an action is unknown, what can we do? Wait for feedback. Either passively wait for the executor to return a result, or actively probe and wait. For example, to determine whether a data export task successfully produced a file, the only method is to check for file existence and data integrity after generation. If probing fails, keep retrying.

## VI. Variants of Paxos

See [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://escholarship.org/uc/item/9w79h2jg)

### Fast Paxos

If you are confident you are the first to propose a value, you can safely skip phase 1 and jump straight to phase 2. Fast Paxos uses rnd=0 to attempt a phase 2 write directly, but requires a quorum size of n*3/4.

> In round 1, Proposers bypass the Leader, connect directly to Acceptors, and try to write.

For a value to be committed fast, it must win not only a majority, but a majority of majorities to be safe.

At t=0 multiple Proposers may write multiple values. If round 1 fails, round 2 falls back to classic Paxos; however, we cannot choose the value from the largest timestamp as in classic Paxos, because multiple values may exist at t=0. Fortunately, we can still choose the value supported by the majority of the majority. If fast round succeeded with n*3/4, then the majority of the majority must contain the consensus value.

$$
[\frac n 2] = (\frac n 4) + [\frac n 4]
$$

> At most fewer than 1/4 of values differ from the consensus, so a majority (over 1/2) must contain more than 1/4 that are the consensus value.

Of course, rnd=0 may fail to write a quorum; choosing the majority of the majority remains safe. If no majority-of-majority exists, then consensus hasn’t been reached, and any value may be chosen.

### Flexible Paxos

To strive in a capricious, uncertain world, we must work together, forming a collective consciousness beyond the individual. Individuals perish; collectives achieve immortality through renewal. Is a simple majority the only path to collective consciousness? Clearly not. For spiritual transmission, seeds suffice.

Consider a Grid Quorum:

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_grid_quorum.png)

For a 3×6 Acceptor grid, we can define consensus as a write into any column quorum. Any two columns are disjoint. To prevent contradictions, we need a horizontal bridge: define Paxos phase 1 reads to scan at least one row. If consensus is achieved at some time, the next consensus must read a row and then write a column. Any row intersects any column. Row reads must read the consensus value; the new write must be consistent with the previous consensus. Notice that neither row nor column quorums are a majority. Their total size is 3+6−1=8, not a majority of 18. Thus read and write quorums need neither be the same nor a majority; intersection suffices to transfer information.

Flexible Paxos points out that phase 1 and phase 2 quorums must intersect, but quorums within the same phase need not. Any quorum system with this property can be used, not just majority quorums. Phase 1 (read) + Phase 2 (write) is a single atomic operation during Time Freeze (fixed logical time), so it suffices for the two quorums’ outcomes to be mutually exclusive. Then master-time points won’t overlap, and later points can see earlier values.

Let $Q_{2c}$ be the set of classic Paxos phase 2 quorums:

$$
\forall Q\in Q_1,\forall Q' \in Q_{2c}: Q\cap Q' \ne \emptyset
$$

> Original Paxos requires all quorums used to intersect.

Flexible Paxos helps performance. For example, in MultiPaxos, phase 1 runs far less frequently than phase 2, so we can make phase 2 quorums as small as possible.

For Fast Paxos, we must also consider the fast-round quorum requirement:

$$
\forall Q\in Q_1, \forall Q',Q''\in Q_{2f} : Q\cap Q'\cap Q'' \ne \emptyset
$$

$Q\cap Q' \cap Q'' \ne \emptyset$ avoids conflicts where $Q\cap Q'$ would pick X while $Q\cap Q''$ picks Y.

In terms of quorum sizes:

$$
q_1 + q_{2c} \gt n\\
q_1 + 2q_{2f} \gt 2n
$$

Two $Q_{2f}$ quorums overlap in $2q_{2f}-n$ elements. For the overlapping set to intersect $Q_1$:

$$
q_1 + (2q_{2f} -n) > n
$$

### A quorum need not be a majority

A quorum is defined to intersect other quorums. For example, require all quorums contain a designated element a—this is a legal quorum system but lacks fault tolerance: `{{a,d},{a,b},{a,c}}`

Also note: if $A \cap B \ne \emptyset$ and A is not a subset of B, then the intersection of A and B’s complement is non-empty: $A\cap \bar B \ne \emptyset$. Thus given a set of pairwise intersecting, mutually non-including quorums,

$$
Q_i \cap Q_j \ne \emptyset \Longrightarrow Q_i \cap \bar {Q_j} \ne \emptyset
$$

That is, for a quorum set, pick any $Q_j$ and take its complement; that complement still intersects all other quorums.

### Even-sized clusters

Typically, cluster size is odd—e.g., 5. If the total is 4, partitioning can render the system non-tolerant. Imagine 4 nodes with a,b in DC1 and c,d in DC2. A majority requires at least 3. If DC1 and DC2 partition, each has 2 nodes—no majority; Paxos stalls.

One solution is to expand the quorum options: add `{a,b},{b,c},{a,c}` to the quorum set (maximize the set so the algorithm can proceed as long as any quorum is satisfied; more options are better). Original 4-node majority quorums are `{a,b,c},{b,c,d},{a,c,d},{a,b,d}`; `{a,b}` intersects each of these, so include it. Adding some non-3-node quorums helps overcome partition and keep Paxos running.

This is a general strategy for even-node clusters: expand the quorum set to $Major(D)\cup Major(D \backslash \{d\})$—i.e., start with majorities of the even-sized set, then delete one node d (chosen as needed), take majorities of the remaining odd-sized set, and union the results.

> If the total is 2n, then M(2n)=n+1, while M(2n−1)=n. So M(2n)+M(2n−1)=2n+1 exceeds the total 2n; each quorum in $Major(D)$ must intersect each in $Major(D \backslash \{d\})$.

### MultiPaxos and Raft

On unifying Raft and Paxos, see xp’s piece: [Unifying Paxos and Raft into one protocol: abstract-paxos](https://zhuanlan.zhihu.com/p/488629044). For a Chinese translation of the Raft paper, see [raft-zh_cn.md](https://github.com/maemual/raft-zh_cn/blob/master/raft-zh_cn.md)

Paxos solves a single decision: how to agree on one value. In practice, we must make decisions continuously.

MultiPaxos applies Paxos to Replicated State Machines: every state change is a log entry. MultiPaxos ensures the entries’ order and values are consistent across replicas, by running Paxos for each entry—each entry maps to a Paxos instance.

To ensure order, MultiPaxos introduces an increasing logIndex and mandates it be continuous—no holes (otherwise it’s hard to determine whether all valid log entries exist). Adjacent entries are implicitly linked.

Multiple log entries can be processed somewhat in parallel. The value of each entry can be determined out-of-order, but commits (application to the state machine) must be in order. For example, we can determine logIndex=100 first, but it cannot be applied until logIndex=1 through 99 are all determined; then apply from 1 onward. Proposals can be out-of-order; application must be strictly ordered to preserve the state machine’s consistency.

MultiPaxos optimizes performance by introducing a Leader and sharing state. All proposals go through the Leader, reducing conflict and simplifying interactions. Moreover, after securing a single Promise, the Leader can reuse a Proposal ID across many entries (distinguished by logIndex), skipping phase 1 repeatedly—equivalent to inserting multiple entries within one Time Freeze.

Raft can be viewed as an improvement or supplement to MultiPaxos, supplying clear solutions to details MultiPaxos glosses over (though with more constraints). Below are some key differences per Zhipu Qingyan AI:

1. Clarity: Raft clarifies many details under-specified in MultiPaxos—e.g., log replication, leader election, log consistency checks.
2. Understandability: Raft aims to be easier to understand—reducing states and simplifying transitions.
3. Constraints: Raft adds constraints—e.g., logs must be committed in order. MultiPaxos allows out-of-order proposals (though application still must be ordered).

Specifics:

- Leader election: Raft uses randomized timers to reduce election conflicts, making the process clearer.
- Log replication: Raft’s replication is straightforward—Leader directly replicates to followers; MultiPaxos may involve more out-of-order handling.
- Safety: Raft strengthens safety via mechanisms like pre-vote and log matching.
- Membership changes: Raft offers a clear joint consensus mechanism; MultiPaxos typically needs extra logic.

==========Zhipu Qingyan AI creation completed=========

Raft’s leader election uses local timers—introducing comparable local physical time. Paxos originally only needs logical clocks induced by causality; Raft effectively incorporates a physical clock. Time is a source of inherent consistency. Adding a physical clock simplifies the consistency problem. For example, Google’s TrueTime uses precise atomic clocks—introducing an absolute time with bounded error, simplifying distributed transactions.

Further explanations from Zhipu Qingyan AI:

Physical vs logical clocks:

- Physical clocks are based on real time; logical clocks (like Lamport clocks) capture event ordering and don’t rely on real time.
- Introducing physical clocks can simplify consistency: global reference time allows comparing timestamps or durations across nodes.

Google’s TrueTime:

- Google’s Spanner uses TrueTime—a combination of atomic clocks and GPS, providing very precise time services. TrueTime exposes a bounded uncertainty interval.
- This simplifies distributed transactions—time-stamp certainty supports external consistency (commit order matches real-time order).

======Zhipu Qingyan AI creation completed=======

### Membership changes

See xp’s post [Pitfalls in TiDB’s Raft membership changes](https://zhuanlan.zhihu.com/p/342319702)

The original Raft paper proposed single-step changes (one member at a time) and joint consensus (changing multiple members at once), advocating single steps. Later this was found problematic, with flaws in the original algorithm; joint consensus emerged as best both theoretically and practically.

Consider migrating from cluster C1 (abc) to C2 (def):

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/join-consensus.png)

In Paxos-family algorithms, we focus on events on the master timeline and ignore micro-level details—including when a leader switches. Paxos’s safety comes from the fact that at a designated instant t only one quorum can write (quorum mutual exclusion). Whether that write is initiated by a leader is irrelevant. Paxos does not rely on leader election; leaders are a performance optimization.

Membership change is a switch from C1’s timeline to C2’s. At t1 (C1’s master logical time), the cluster config is C1. At t2 we initiate the switch—align the two timelines. Therefore, at t2 we must write the “join” configuration to both C1 and C2 timelines: from t2 onward the two timelines are synchronized; any event must occur in both C1’s and C2’s quorums. This implies that t2’s commit of the new config is Major(C1) + Major(C2). At t3 we switch to C2’s config; this config takes effect when it is written to Major(C1) and Major(C2). After t3, only C2 is alive; C1’s unused nodes can be retired.

From t2 to t3, all proposals must pass Major(C1) and Major(C2) simultaneously. We switch first to a broader timeline, then to a narrower one. We cannot jump directly from C1’s timeline to C2’s; that would cause split brain at the intersection. We need an interval [t2, t3] that bonds the two timelines.

With the master timeline picture, joint consensus is intuitive: the master timeline exists because quorums agree; if we bond two timelines, the bonding interval must achieve agreement in Major(C1)+Major(C2).

### Ghost reappearance

See [How to solve “ghost reappearance” in distributed systems](https://www.infoq.cn/article/YH6UVyFLyN3oOOk1Ag7B), [Consensus protocols: post-leader-switch dilemmas — log recovery and ghost reappearance](https://zhuanlan.zhihu.com/p/652849109)

In MultiPaxos, “ghost reappearance” may occur: previously unconfirmed logs reappear later, causing inconsistency or duplicated processing. That is, entries not reaching majority under the previous leader may later reach consensus under a new leader, turning unknown into committed.

![](https://pic3.zhimg.com/80/v2-5035bf2fa2a2fdceabe67334d5517834_1440w.webp)

Round 1: A is Leader, writes logs 1–10. Logs 1–5 are majority-confirmed; client acknowledged. Logs 6–10 time out without client ack.
Round 2: A crashes; B becomes Leader. Since B and C’s max logID is 5, B won’t re-confirm 6–10; it starts writing from 6. Clients querying now won’t see 6–10. Then round 2 writes 6–20; only 6 and 20 are majority-persisted.
Round 3: A becomes Leader again. From the majority it sees max logID=20; it re-confirms logs 7–20, including re-confirmation of A’s 7–10. Clients now observe 7–10 “ghosting” back.

To avoid this, a new Leader must first write a StartWorking log to open a new epoch, then ignore all work not completed in prior epochs.

## Generalized Paxos

See [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://mwhittaker.github.io/publications/bipartisan_paxos.pdf)

MultiPaxos and Raft assume linear logs—entries form a total order: earlier entries must run first. In practice, some entries’ order is interchangeable if they don’t conflict (e.g., read then write vs write then read). For instance, commands `a=1` and `b=2` are independent and can be swapped. Generalized Paxos computes a partial-order dependency graph via a dependency service.

> Partial order is a mathematical notion describing “partial comparability” among elements: some pairs can be ordered; others cannot.

1. Dependency service: computes dependencies among log entries—data dependencies (e.g., write → read) or control dependencies (e.g., required sequencing).

2. Conflict graph: nodes are log entries; edges are dependencies. If there is no path between two nodes, they are commutable—execution order can be swapped.

Deployment configuration for generalized Paxos:

proposers: at least f+1 nodes

dependency service nodes: 2f+1 nodes

acceptors: 2f+1

replicas: at least f+1

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/BPaxos.png)

$deps(v_x)$ is the union of dependencies computed by at least f+1 dependency nodes.

If the dependency service sees x first and later a conflicting y, it adds an arrow from node $v_y$ to $v_x$; $v_x \in deps(v_y)$

Two invariants hold:

Consensus invariant: for any vertex v, at most one value `(x, deps(v))` is chosen—value and dependencies are committed together.

Dependency invariant: formalizes conflicts in the dependency graph. If x and y conflict, then either $v_i \in deps(v_y)$, or $v_y \in deps(v_x)$, or both.

![](https://picx.zhimg.com/80/v2-e367275aa567652df6bbc7d3ff79aab1_1440w.webp)

Two conflicting operations may be issued simultaneously. Message arrival order to different dependency nodes may differ, leading to differences in their dependency graphs. Even if the dependency service’s conflict graph is acyclic, replicas may form cycles.

Once a log item and its dependencies reach consensus, each replica can deterministically sort the items. The sorted order is identical across replicas, enabling consistent application to the state machine.

## VII. Other techniques around timelines

For mortals, time feels like a magical a priori. It seems all coordination fundamentally leverages time’s arrow. In our realm, Newton first discovered that time cuts causality in half—cause on the left, effect on the right—and wrote his immortal second law:

> F = m* a, cause = linear coefficient * effect

Later, the unparalleled genius Albert Einstein shattered millennia of dogma, pointing out that time is not a single unique line.

If timelines are not unique, how do we avoid losing direction? One option: remember all timelines—this yields vector clocks. Alternatively, align all timelines into a single one—this is Paxos.

Do we have other options? Imagine being free from causality’s shackles—walking freely on timelines, indifferent to past and future. Why can’t cause and effect be inverted? Essentially, because the system is not commutative: swapping left and right yields different results. Only in a high-magic world—no left or right, no before or after—can we cast the tenth-level spell: inversion of causality. Enter CRDTs.

### Vector clocks

Paxos compresses multiple local timelines into one coarse master timeline. Vector clocks keep multiple timelines simultaneously, preserving partial-order causal dependencies.

In vector clocks, each node maintains a vector of logical clocks for itself and all others. Here is an example from Zhipu Qingyan AI.

### Assumptions

Assume three nodes: A, B, C.

### Initial state

Each node’s vector clock is:

- A: [0, 0, 0]

- B: [0, 0, 0]

- C: [0, 0, 0]
  Each position is the corresponding node’s logical clock.

### Event sequence
1. Event 1: A experiences an event; A’s clock updates:
   
   - A: [1, 0, 0]

2. Event 2: A sends a message to B; B receives and updates:
   
   - B: [1, 1, 0] (A’s values copied; B’s own clock increments by 1)

3. Event 3: B experiences an event; B’s clock updates:
   
   - B: [1, 2, 0]

4. Event 4: B sends a message to C; C receives and updates:
   
   - C: [1, 2, 1] (A and B from B; C increments its own)

5. Event 5: C experiences an event; C’s clock updates:
   
   - C: [1, 2, 2]
     
     ### Analysis
     
     Comparing vector clocks:
- Event 1 precedes Event 2—A’s clock changed and is reflected in B.
- Event 2 and Event 3 are concurrent in the sense they both involve B’s further updates.
- Event 4 precedes Event 5—C’s clock increased on receiving B’s message, then again on its own event.
Vector clocks record ordering and causality in distributed systems.

======Zhipu Qingyan AI creation completed=====

### CRDT data structures

See [How to design CRDT algorithms](https://www.zxch3n.com/crdt-intro/design-crdt/)

CRDTs (Conflict-free Replicated Data Types) are special data structures designed for eventual consistency in distributed systems. They allow replicas to update independently without a central coordinator. When updates merge, the merge is conflict-free and yields consistent state. The mathematical basis is the semi-lattice.

Key relations between CRDTs and semi-lattices per Zhipu Qingyan AI:

1. Partially ordered sets (POS):
   
   - A POS is a set with a partial order relation (reflexive, antisymmetric, transitive).
   - CRDT states form a POS; the merge defines the partial order. If A merges into B, then A ≤ B.

2. Lattices:
   
   - A lattice is a POS where any two elements have a least upper bound (join) and a greatest lower bound (meet).
   - In CRDTs, any two states can be merged—the merge is their join. Merge must be associative and commutative, matching lattice laws.

3. Upper and lower bounds:
   
   - Merge corresponds to join (least upper bound). For example, merging two G-Counters takes per-position maxima—i.e., the join.
   - CRDTs often omit meet because they usually allow only monotonic growth (e.g., counters only increase). If needed, meet can be a minimum.

4. Consistency guarantees:
   
   - Lattice theory provides consistency proofs: since merge yields the join, even if replicas undergo updates in different orders, merging yields the same final state.
   - Regardless of merge order or grouping, the final state is identical.

5. Commutativity and associativity:
   
   - CRDT merge must be commutative and associative—matching lattice operations.
   - Thus, merge order and grouping do not alter outcomes.
   
   =====Zhipu Qingyan AI creation completed====

In CRDT applications, if each edit is treated as a Delta, the key is that the combination operation over Deltas must be commutative. Then, as long as we gather all Deltas, regardless of reception or combination order, we get the same final result.

## Conclusion

The magical image here is not merely a heuristic analogy; it can be formalized mathematically by defining local timelines → master timeline → Time Freeze on the master timeline. In this way, the magical description translates to rigorous proof.

Below is a more formal statement assembled by Zhipu Qingyan AI based on this article and hints:

1. Logical timestamps—definition and properties:
   
   - Each Acceptor maintains a logical timestamp t, strictly monotonically increasing throughout execution.
   - At any t, at most one event (e.g., writing a specific value) is recorded. Any Promise or Accept occurring at a timestamp less than or equal to the maximum accepted ts is ignored, preserving monotonicity.
   - The increment in t reflects state changes. No change implies no significant event; each t corresponds to a unique, significant state transition.

2. The master timeline—concept and maintenance:
   
   - The master timeline emerges by aligning timestamps across a majority of Acceptors, ensuring continuity and consistency.
   - When a majority accepts a Promise request with timestamp t, they align to t—creating a new master-time point.
   - If a majority accepts and writes the same value X at t, X is determined on the master timeline.
   - If writes are inconsistent, or a subset hasn’t written, t is indeterminate; if a majority hasn’t written, t is “unwritten.”
   - In a system with 2f+1 Acceptors allowing up to f failures, there is always a living majority; overlaps ensure continuity of the master timeline. Each t records at most one determined value, and as long as a majority is alive, that value isn’t lost. If no consensus exists at t—even if some Acceptors wrote and crashed—that indeterminacy doesn’t affect the eventual consensus. We care about values determined or potentially determined on the master timeline.

3. Role of Promise requests:
   
   - Proposers send Promises to a majority, carrying timestamp t to unify time.
   - On receiving Promise replies, if any reply includes a value already determined by consensus, the Proposer must adopt it to avoid overturning consensus. If the values are not consensual yet, the Proposer may choose any. Adopting the value with the largest timestamp among replies is always safe.

4. Accept requests and logical Time Freeze:
   
   - After obtaining a majority of Promises, Proposers send Accept for t.
   - If a majority accepts, it means t remained frozen throughout communication, preserving consistency and correctness.
   - Acceptors ignore Promises with timestamps less than or equal to the largest they have accepted—ensuring t’s monotonic increase.
   - With multiple competing Proposers, at most one can write at any t on the master timeline, coordinating concurrency and preserving order.

=======Zhipu Qingyan AI creation completed======   
Core picture: use Time Freeze to ensure single-write consistency + analyze on the master timeline.

For any t, if a majority accepts writing the same value X, then the master timeline records writing X—consensus is achieved and the algorithm may terminate. Otherwise, increase logical time and retry. The micro-level condition for writing success on the master timeline is: freeze time at t across a majority of Acceptors, then write. If any other events occur in the micro process between freeze and write, the write fails, and we retry.

To ensure consensus, once reached, is never overturned, we must read-then-write on the master timeline. If the prior instant wrote X, the subsequent instant must write X as well. Non-overturnable consensus implies monotonic evolution: “no value” → “has a value but indeterminate” → “determinately has value.” Therefore, we only need to inspect the latest t to obtain the latest knowledge.

To analyze Paxos correctness, consider only events on the master timeline, ignoring leader existence, leader switches, random Acceptor failures, etc. Regardless of micro variability, macroscopic knowledge on the master timeline is deterministic and unique. This is like molecular chaos at the micro level producing stable macroscopic phenomena like temperature.

<!-- SOURCE_MD5:e59a174e15a6aa4b8954a6d2dfb40947-->
