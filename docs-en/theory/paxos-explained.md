Explainer video: https://www.bilibili.com/video/BV1F52aY2EXz/

The Paxos algorithm is a very fundamental algorithm in distributed systems and has long been known for its obscurity and brain-bending nature. However, the reason it feels bewildering is mainly because it’s not easy to intuitively grasp why it’s designed as it is. While we can verify its correctness via concrete examples, and even use rigorous mathematical proofs to convince ourselves it’s right, we still struggle to answer: why must we choose this approach? Is it the only possible method? Is there a way to find an explanation that makes Paxos feel self-evident on an intuitive level, without relying on mathematical derivations?

In my article “[A Magical Research Report on Paxos](https://mp.weixin.qq.com/s/CVa_gUdCtdMEURs40CiXsA),” I built a magical image of the Paxos algorithm from the perspective of extra-dimensional magic, attempting to provide a “Why” answer, rather than just introducing how to do Paxos. This article is a supplemental explanation to the above.

For the basic knowledge of the Paxos algorithm, you can refer to xp’s articles on Zhihu: [200 lines of code to implement a Paxos-based KV store](https://zhuanlan.zhihu.com/p/275710507), [Reliable distributed systems—an intuitive explanation of Paxos](https://zhuanlan.zhihu.com/p/145044486)

## I. Why learn the Paxos algorithm?

Some may doubt: my work doesn’t involve distributed systems much, so is it necessary to learn Paxos? The answer is yes. As long as your problem involves multiple state spaces, or coordinating multiple independently acting entities, you’ll encounter similar problems, and the solutions provided by Paxos can offer insights.

Some may argue Raft is now more popular and Paxos seems less common. We can understand it this way: Raft is essentially a variant of Paxos, choosing specific implementation strategies guided by Paxos’s basic principles.

More precisely, you can view Paxos as a general framework in the domain of distributed consensus, defining the core mechanisms needed to achieve consensus. Raft can be seen as a concrete implementation within this framework, introducing additional rules and steps to implement consensus. These rules provide convenience but are not strictly necessary to achieve consensus. Once you understand Paxos, you’ll find it easier to understand Raft and the principles behind other consensus algorithms.

## II. What is the Paxos algorithm?

Paxos solves the simplest consensus problem in distributed systems: how multiple nodes can agree on a single value in the presence of possible failures.

A correct consensus algorithm must satisfy the following properties:

**Agreement**: All nodes must agree on the same value, and once consensus is reached, it will not change.

**Validity**: The value agreed upon must originate from some node’s proposal.

**Termination**: Eventually, all nodes reach agreement.

The above conditions are also known as Safety + Liveness. This is the typical “have your cake and eat it too”: both correctness and feasibility. Agreement reflects the basic meaning of consensus, while Validity excludes trivial cases—for example, all nodes agree that regardless of the external proposals, we always choose the value 3. That would indeed form a “consensus,” but it lacks dynamism and is not useful.

The scenario depicted by a consensus algorithm is: initially, the entire system is in an uncertain state, with many possibilities allowed—for example, values x and y are both possible. However, after executing a critical action, the entire system suddenly transitions (similar to a phase transition in physics, like water freezing into ice) into a deterministic, globally coordinated state (frozen on a selected value). Continuing to execute the algorithm’s rules will ultimately lead all nodes to acknowledge that the value can only be x and cannot be y.

> If we organize the actions of all nodes participating in the consensus algorithm into a single sequence, then there must exist a critical action such that multiple possibilities are allowed before this action, and the results become fixed after this action. For example, if an Acceptor records a value and forms a majority, the value becomes fixed. If it fails to record, no majority is formed yet, and new possibilities are still allowed.
> 
> Although all nodes in the consensus algorithm run in parallel, in hindsight we can always organize all actions into a sequence and identify the critical transition action within it.

Curiously, is it possible for the system to enter a Schrödinger’s cat-like state, simultaneously both having selected a value and not having selected a value? From the observer’s perspective, such a state can indeed exist. However, the Paxos algorithm addresses this problem by embedding an observation mechanism that ensures an eventual wavefunction collapse, yielding a definite result.

### The FLP Theorem

Unfortunately, a consensus algorithm that satisfies the above three conditions does not exist in the absolute sense! The FLP theorem (Fischer, Lynch, and Paterson) states that in a completely asynchronous distributed system, there is no consensus algorithm that can simultaneously guarantee Agreement, Validity (often discussed as Reliability) and Termination.

An asynchronous model means there is no global clock, processes can proceed at arbitrary speeds, messages can arrive at arbitrary times, but messages are guaranteed to be eventually delivered.

FLP essentially says: if an omniscient, omnipotent deity maliciously disrupts the progress of consensus and, each time consensus is about to be achieved (at the moment a critical transition is about to occur), indefinitely suspends a critical node, then no algorithm can ensure consensus is reached. Fortunately, in our world, such a bored deity has not been discovered. Try enough times, and you’ll eventually get lucky.

### Paxos Algorithm at a Glance

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos-diagram.webp)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/phase1.png)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/phase2.png)

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos-algorithm.png)

The above images are from the Alibaba Infrastructure Division’s presentation by Hedengcheng: [PaxosRaft Distributed Consistency Algorithm Principles and Their Practical Applications.pdf](https://github.com/hedengcheng/tech/blob/master/distributed/PaxosRaft%20%E5%88%86%E5%B8%83%E5%BC%8F%E4%B8%80%E8%87%B4%E6%80%A7%E7%AE%97%E6%B3%95%E5%8E%9F%E7%90%86%E5%89%96%E6%9E%90%E5%8F%8A%E5%85%B6%E5%9C%A8%E5%AE%9E%E6%88%98%E4%B8%AD%E7%9A%84%E5%BA%94%E7%94%A8.pdf)

Definition of a value being chosen: A value is chosen if it is accepted by a majority (more than half) of acceptors.

### Relativity, Time, and Distributed Systems

[Time, Clocks and the Ordering of Events in a Distributed System (1978)](https://dl.acm.org/doi/pdf/10.1145/359545.359563)

This is Lamport’s most-cited paper and is reputed to be the most important article in distributed systems. In his retrospective, Lamport wrote:

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

Lamport holds a B.S. in Mathematics from MIT (1960) and a Ph.D. in Mathematics from Brandeis University (1972), and studied special relativity systematically. His account fully illustrates that to master computer science, you must understand some physics!

Einstein imagined sending out a photon to probe the world and discovered an earth-shattering secret: time in different places only has a partial order induced by causal relationships; the order of events can be observed differently by different observers! When Lamport saw others discuss message sending and message timestamps, he immediately realized that message propagation and photon propagation are the same thing, and the underlying physical image is special relativity. Once you realize this, the subsequent logical derivation becomes completely trivial! Thomas and Johnson suffered because they hadn’t learned physics well; they fundamentally didn’t understand what their algorithm was doing, leading to subtle errors. Without the guidance of a physical image, it’s easy to get confused at critical moments.

Interestingly, although Lamport was guided by special relativity, in the paper itself he only discussed logical clocks and never mentioned special relativity, leading some to mistakenly believe it was a stroke of genius out of thin air.

Lamport’s original paper on Paxos [The Part-Time Parliament](https://ying-zhang.github.io/dist/1989-paxos-cn/) was finally formally published in 1998, after which many people exclaimed that they couldn’t understand it. So in 2001, Lamport wrote [Paxos Made Simple](https://www.jianshu.com/p/1bbbfbe300d1), beginning with the sentence: “The Paxos algorithm, when presented in plain English, is very simple.” In this article, Lamport provides a “Why” for the design of Paxos, but this explanation is based on step-by-step mathematical reasoning—effectively forcing others to accept the rationality of the algorithm. The inevitable result is that many people think they understand it at first glance, but are confused again in the next moment.

Physics emphasizes physical imagery in learning and research. Physicists never obediently follow mathematical rules step by step; they believe a derivation because it corresponds to a reasonable physical explanation. So for the Paxos algorithm, we can’t help but ask: what is its underlying physical image? In Lamport’s heart, is there still a hidden, non-mathematical understanding of the inevitability of Paxos—like when he quietly concealed relativity from that group of computer folks?

## III. A Magical Imagery of Paxos

The backdrop of distributed systems is a chaos of life’s freedom and death’s randomness, where contradictions and conflicts are everywhere. Yet, Paxos establishes a unified, consistent consensus world atop this chaos—it appears miraculous. However, mortals find miracles hard to understand; they cannot stand at the height of gods and survey all beings, and can only use limited life experience to guess divine intent, inevitably generating confusion unique to mortals.

The distinction between gods and mortals lies in the divine realm. In the divine realm, words enforce reality. Setting rules is a god’s starting point, whereas humbly accepting rules—and cunningly exploiting them—is the essence of being human. Between heaven and man lies the chasm. But if humans develop a rebellious heart and fancy themselves as rulers of rules, can they grasp the unbelievable in the mortal world? A single thought of transgression, and the world expands in an instant. We suddenly realize that a god, as the most perfect existence above all finite objects, needs only three steps to solve consensus:

1. God said: Let there be time.

2. God said: Time freezes.

3. God said: The value shall be X.
* A prerequisite for freezing time is that time must first exist.

* Once time exists, if all time in the universe freezes, then no unexpected events can occur. At this point, a god can calmly do anything he wishes. Note that we always recognize time through observing change, such as comparing the oscillation of a pendulum with other changes. If time freezes, it means no observable changes occur. But this doesn’t mean absolutely no changes happen. For example, when you pause a game and later resume, the NPCs in the game don’t perceive any change.

* After freezing time, the god directly sets the same value in multiple places. When time resumes, people at different locations will find the same value suddenly appearing before them—consensus has been reached.

Our world is now in a low-magic era. Spiritual energy is depleted; magic has dissipated; true magic no longer exists here. Yet we still have a computer at hand. If the world’s substrate were a supercomputer, all physical laws could be simulated by this machine. So in this low-magic world, can we use our computers to simulate the magic of a high-magic world?

> A computer’s essence is the Turing machine, and the Turing machine’s essence is that it’s a universal simulator—it can simulate any computation. This is Turing completeness.

Paxos is a simulated implementation of the ninth-level magic of time-freezing.

Once you realize Paxos’s true secret lies in extra-dimensional magic, the rest are just mundane technical details.

> What’s needed here is a shift in worldview or cognitive paradigm: we design a law of nature to achieve a goal, then think about how to implement that law. It’s like designing a set of interfaces first in programming, and then implementing them.

Recall that the series of actions by Proposers and Acceptors in Paxos fundamentally ensure that time flows in one direction.

1. The Proposer generates a globally unique, monotonically increasing Proposal ID. This Proposal ID is a marker of time; each ID corresponds to a unique moment.

2. Why does an Acceptor, after receiving Propose, refuse to respond to Propose with Proposal IDs less than or equal to the current request? Because time flows one way, a successful Propose indicates the start of a time freeze. If time freezes at moment t, it cannot freeze again at any moment less than t. Likewise, since time is already frozen, another Proposer should not freeze the same moment t again, so no response should be given for Proposal ID equal to the current Propose.

3. Why does an Acceptor, after receiving Propose, refuse to respond to Accept requests with Proposal IDs less than the current Propose? From Propose to Accept is the time-freeze phase, so we can accept Accept at the start time t of the freeze, but cannot accept Accept with Proposal ID less than t.

In our low-magic world, the most basic method to simulate magic is cognitive deletion—that is, delete from our cognition all facts that don’t conform to magical principles. If you can’t see it, it doesn’t exist! The Acceptor’s seemingly odd behaviors simply ignore facts that would cause the time-freezing magic to be exposed.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/time_arrow.png)

Each Acceptor records a Proposal ID that only increases and never decreases, establishing a local arrow of time. The entire system aligns Proposal IDs to the same point, effectively tying multiple local time arrows together into a coarse-grained, unified arrow of time. The flow of time resembles a wavefront sweeping across the system.

Thus, from a god’s perspective, Paxos merely uses the time-freezing magic to forcibly align multiple timelines into a single primary timeline—nothing but a minor trick.

> Note that the “time” here refers to logical time we define ourselves, not physical time.

### An interesting application: stop-and-align and optimistic locking

This “stop-and-align” technique is a basic strategy for achieving consensus in distributed systems. For example, in Kafka, multiple consumers in the same group act independently but must agree on how to divide work. Therefore, when the group’s membership changes or the topic structure changes, a rebalance is triggered. During rebalance, the Coordinator first asks all workers to stop their current work, collectively switch to the next generation (epoch), and then distribute a new assignment plan. A plan is valid only within a single epoch.

Optimistic locking in databases uses the same strategy. At the start of a handler, we read the MainRecord’s version, then modify the MainRecord and associated SubRecords. Finally, in a single transaction, we commit the changes while attempting to update the main record’s version.

```
 update MainRecord
 set version = version + 1
 where version = :record_version
```

If the update succeeds, it indicates that throughout the entire processing window, time was frozen—no one else executed conflicting actions.

### Micro vs Macro

A local arrow of time can be interrupted at any time due to various anomalies. This requires some broader perspective—distinguishing between micro and macro. At the micro level, some may succeed and others fail, but as long as the majority succeeds, we define the operation as having succeeded at the macro level. A set cannot simultaneously have two majorities, and a majority cannot choose both value X and value Y. Hence, the path from micro to macro is clear.

A key to understanding Paxos is that we only need to focus on events that ascend to the macro world—i.e., what happens on the primary timeline of the primary world. Each time point on the primary timeline corresponds to a process in the small world: time-freeze start to time-freeze end. At each time point, a value may be set (if the setting succeeds, consensus is reached). The effect of time freezing eventually manifests fully in the primary world: on the primary timeline, it appears as isolated time points that do not overlap, corresponding to process intervals in the small world that do not interleave.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_phase2.png)

Only events that occur in most small worlds ascend to the primary world and become events in the primary world. The first step in Paxos is to obtain promises from a majority of Acceptors. If successful, the majority of Acceptors’ times are aligned as one, creating a new time point on the primary timeline. In other words, only time points recognized by a majority of Acceptors appear on the primary timeline, and they record the start of time freezing on that timeline. If multiple Proposers compete concurrently, multiple Proposers may access different Acceptors with the same Proposal ID; if none obtains majority recognition, that time is automatically discarded and will not be observed on the primary timeline. There can be many competition processes at the micro level; what we observe on the primary timeline is the macro result of successful competition that obtains majority recognition. In analyzing the algorithm, we only need to consider results on the primary timeline.

Because competition may fail and each Proposer independently chooses Proposal IDs, visible Proposal IDs on the primary timeline are not continuous. However, the absolute value of logical time is not important; we only need their relative order. In addition, if needed, once the primary timeline is established, we can renumber time points consecutively starting from 0.

The mutual exclusion property—if a majority chooses A at one time, there can be no other majority choosing B at the same time—ensures uniqueness and determinism at the macro level. When most small worlds write value A at time t, this fact will not be changed or obscured by subsequent changes; no second voice can offer a valid objection. Only then does the write of value A happen in the primary world.

> The Proposer sends a message to a majority of Acceptors: I want time to freeze at t3.
> 
> A majority of Acceptors successfully replies: Agreed. Time is now at t3, and the previously set value at time t is X.
> 
> Proposer: At freeze time t3 I want to set the value to X.
> 
> A majority of Acceptors successfully processes: The value at t3 has been set to X.
> 
> Note that once the majority successfully processes, a reply is not required; consensus is already achieved.

### An interesting application: avoiding split brain

In algorithms requiring leader election, a classic problem is how to avoid split brain. What if a newly elected leader gains popular support while the old leader refuses to step down and keeps interfering? A general solution is to directly define the old leader as a zombie and completely ignore all information from the previous epoch (e.g., reject all requests with smaller epochs). In practice, we do not restrict the old leader’s behavior; in its small world, it can do whatever it thinks is right. But its actions cannot ascend to the collective will and cannot affect the primary world. When a new leader takes office, it must write-before-read: first mark its epoch in the primary world (similar to updating a global shared variable). Then, when the old leader tries to commit its results, it discovers via optimistic locking that it has lost authority and must abandon its processing result.

In our physical plane, as quantum mechanics has developed, observation or measurement has acquired profound theoretical meaning. In the view presented by quantum field theory, in unseen imaginary time, countless wild entities compete and annihilate each other; what reflects in the real world is merely the outcome of some aggregate computation. Through the eerie quantum tunneling effect, we can glimpse the surging waves behind the scenes.

## IV. Monotonicity: From the Schrödinger cat state collapse

Phase 2 of Paxos has a particularly puzzling operation: after the Proposer collects Phase 1 responses from a majority, if any response contains a value, it chooses the value of the proposal with the largest Proposal ID among the responses as the value for the Phase 2 Accept. Why does the Proposer abandon its original proposal and choose someone else’s value? Why must it choose the value of the proposal with the largest Proposal ID?

These questions exist essentially due to the limitations of mortals.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

Consider 5 Acceptors and multiple Proposers. At ProposalID=t1, proposal P1 was accepted by A1 and A2, but did not reach a majority, so no value was determined in that round. ProposalID=t2 (P2) likewise failed to reach a majority. ProposalID=t3 (P3) was accepted by the majority A2, A3, A4, thus reaching consensus.

Once consensus is reached at t3, is it possible to reach a new consensus P4 at t4? In that case, the consensus at t3 is P3, and at t4 it is P4, with no consensus at t1 and t2. For a god, choosing different values at different moments is perfectly fine—no problem—because the god is omniscient and omnipotent. But for dull mortals, if different moments can have different consensuses, they will experience cognitive overload.

If consensus can be overturned, how does a mortal with limited cognition know which value to use? Many moments simply may not have reached consensus (e.g., t1 and t2). Must one traverse all moments from t1 to tn to learn all consensus values? Therefore, fundamental consensus algorithms directly require that once consensus is reached, it remains unchanged.

> In fact, the definition of consensus already excludes the possibility of overturning consensus after it is reached.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus_fail.png)

Now consider the case in the figure above. Suppose A3 crashed while processing P3. From the outside, there are two possibilities:

1. A3 has already accepted P3, so consensus is reached.
2. A3 has not yet accepted P3, so consensus is not yet reached.

Except for A3 itself, no one knows its processing status. But A3 is down—it cannot answer any questions! So if different moments can have different consensuses, we may end up in an awkward situation where the historical outcome is entirely in a quantum-undetermined state, with no simple yes-or-no answer.

For mortals, the ideal choice is for the system to have a certain monotonicity: it only moves forward in one direction, and once it reaches the target state, it remains there forever. Then whenever we want to extract information from the system, we can move the system forward one step. If consensus has been reached, moving forward still yields the consensus value; if not, we actually choose a value and escape the uncertainty. For example, in the above case, continuing to run Paxos one more step will ensure that regardless of what A3 did at t3, we will obtain P3 at t4, thereby eliminating uncertainty in the system after t4. In Yu Bai’s article, this is also called the “max-commit principle.”

Using the macro-world analysis from the previous section, we find that because the micro-world is full of spontaneous birth-and-death fluctuations, facts we perceive in the macro world may be in a Schrödinger cat state. When the Proposer obtains responses from a majority of Acceptors, it effectively completes a read on the primary timeline, albeit with some side effects.

1. We know all facts on the primary timeline must be ordered by “time points,” and consensus is a write occurring at some time point. The simplest way to maintain consensus consistency is read-before-write—peek at the previous situation before writing.

2. If the majority’s read of values and moment t are identical, then t’s value is already determined; essentially, consensus on the primary timeline has been reached. There’s no need to continue—just return the result.

3. But if the majority’s read returns differing values or moments t, then the previous moment may or may not have achieved consensus, and the system is uncertain. In this case, we must ensure that if consensus has been reached, our subsequent behavior won’t break it. That is, if a majority at time t-1 has accepted value X, we cannot propose a different value Y at time t. If consensus is reached, the consensus value must be the value with the largest Proposal ID. Because if consensus is reached at t3, then the immediately following t4 must see t3’s result; irreversibility of consensus implies t4’s value must be the consensus value. So if the largest Proposal ID’s value is not the consensus, then consensus could not have been reached before it. Paxos evolves monotonically on the primary timeline: from no value, to uncertain whether there is a value, to a definite value. Therefore, we need only inspect the final step’s result. Since a given Proposal ID is used by at most one Proposer, a Proposal ID maps to at most one value.

4. In Phase 2 of Paxos, once a value is read from a majority, the Proposer essentially abandons its own write; it degenerates into a pure observation action with a side effect: the state at the current time point on the primary timeline collapses from “maybe set (consensus achieved) or maybe not” to a definitive “has a value.” In essence, no new value is written; we choose one from the current possible values—consistent with the role of observation in quantum mechanics.
   
   $$
   |X\rangle + |0\rangle \longrightarrow |X\rangle
   $$

5. If Phase 2 succeeds, Paxos completes an atomic read+write after time freezing.

> A Schrödinger cat state means the system is in a quantum superposition: if you don’t observe it, the cat is neither dead nor alive—a superposition of both possibilities. Upon observation, the system collapses to either dead or alive.

Returning to the example above, there is a subtle case. At t1 only two P1 are set—no consensus. At t2, only two P2—no consensus. But at t3, the majority will certainly see P1 or P2, so in fact P3 can only be P1 or P2 and not an arbitrary value. If the majority responses are all empty, we can safely determine that no consensus has been reached. If there are values, for safety, Paxos rules require choosing from existing values. If we explicitly receive five responses for the previous moment and know that consensus has not been reached, then not following Paxos’s selection and choosing another value won’t cause a conflict. Paxos’s choice is not strictly necessary, but it is simpler and can accelerate convergence to some extent.

For example: at t3, five nodes return values 1, 2, 3, 4, 5. According to Paxos, we may only return one of these five values, and it must be the one with the largest Proposal ID. Now suppose we ignore Paxos rules and return 6—would that cause a contradiction? If you learned Paxos in the traditional way, this deviation from the original analysis would confuse you. But with the time-freeze magic + primary timeline imagery, the answer is easy: it won’t cause a contradiction. Values are updated on each time point via time-freezing, so they are isolated and executed in order. A primary time point’s value may be “not written” (if three nodes return “not written,” it’s certainly not written), “unknown whether written” (but known that an attempt was made), or “written” (three nodes return the same value for the same time point), and the conversion between these three is one-way. Only the “written” state affects correctness—writing a different value after a write causes a conflict. But if we know it’s “not written,” there’s no conflict.

### When is consensus reached?

When consensus is determined, do participants in the system instantly realize it has been achieved? Interestingly, the instant consensus is reached, no participants—Acceptors nor Proposers—know that consensus has been reached! However, over time, the algorithm’s execution gradually reveals the fact that consensus has been reached.

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

First, note that before consensus is reached, an Acceptor may change the value it has accepted—for example, A3 first accepted P2, then accepted P3. Because a Proposer may become unreachable at any time, Acceptors can only choose to accept newer values. Therefore, when A3 accepts P3, it cannot know that consensus has been reached and that P3 is the final chosen value. By the same token, A2 and A4 only know their local states and cannot judge whether the system as a whole has reached consensus. On the Proposer side, it cannot know whether P3 will be accepted by a majority or become the final consensus until it has received successful responses from a majority of Acceptors. Consensus belongs to the whole; a single participant needs a process of understanding whether consensus has been reached. This is the key to the Learner role. The Learner first recognizes that consensus has been reached and then disseminates this information, avoiding the need for each participant to independently collect and reason.

There’s another interesting detail. At t2, if the Phase 1 majority responses came from A3, A4, A5, we can propose any value P2 (if A1 and A2 were in the majority, then per Paxos rules P2 must equal P1). However, writing to the majority does not require writing to A3, A4, A5; we may choose any majority. Thus we can write P2 (different from P1) to A1. (Being able to write at t implicitly means no changes occurred after t.) To emphasize this, Lamport wrote: “This need not be the same set of acceptors that responded to the initial requests.” Without the primary timeline imagery, it’s easy to get confused about this point.

## V. Leader-based: Copy and paste

Casting ninth-level magic consumes immense magical power. A thrifty, socialist-value-aligned god would never waste magic. Once time is frozen, to maintain consistent behavior of nodes across locations, the god’s best choice is to cast an eighth-level magic “Grand Puppetry,” replicating a leader node’s behavior to all other nodes.

This replication originates from divine power. Once the leader initiates a new action, it traverses mountains and seas, ignores physical barriers, and directly descends upon remote followers. Followers have no right to object—only the freedom to execute. However, as the saying goes: “The top moves mouth; the bottom runs legs.” In our low-magic world, implementing Grand Puppetry is not easy and typically done by adding a log file at the sender and the receiver.

The sender writes decisions into the log, making them immutable oracles. The sending component scans the log and ensures it is delivered to the remote side entry by entry. If the receiver cannot be reached, or sending fails, or the expected response is not received, the sender cannot complain or give up; it must work hard and keep retrying until a successful response is received. This guarantees at least once delivery. The receiver must unconditionally accept all messages, neither refusing nor tampering. Because duplicate messages may arrive, it must perform idempotence checks via local logs, filtering duplicates and achieving at most once processing. If messages need to be relayed through a streaming system, to avoid replaying from the source each time, intermediate nodes must record results via a snapshot mechanism.

> End-to-end exactly-once = at-least-once at the initiator + at-most-once at the processor

Without doubt, MultiPaxos and Raft implement this replication strategy. Once a leader is elected, the Term number representing the tenure can be reused multiple times. Multiple commands can be issued under the same Term, as long as they are distinguished by log index.

If we analyze carefully, messages received over the network fall into two categories: a Request, for which the receiver can freely choose processing and the result is uncertain (it may succeed or throw an exception), and a Notice, whose handling is fixed and the receiver cannot object.

A good example is Two-Phase Commit (2PC). In Prepare, the Participant receives a request message and may choose to commit or roll back according to its own will. Once the Participant returns its possible choice to the Coordinator, it surrenders its autonomy to the Coordinator, promising to accept only notice messages thereafter and to keep its behavior aligned with the Coordinator. If the Coordinator decides to commit, the Participant never chooses rollback. Similarly, if the Participant rolls back, we know the Coordinator can only roll back. Their choices are no longer made independently; they are entangled.

### 2PC and quantum entanglement

2PC can be seen as the Coordinator providing a source of consistency, with Participants gradually becoming entangled with the Coordinator. Paxos gradually builds a Quorum, with participants in the Quorum entangled together.

Before 2PC runs, each Participant can independently choose success or failure; outcomes are random. After Phase 1, if you observe each Participant individually, it still could succeed or fail—outcomes remain random. But if you observe the entire state space, you find the feasible states shrink—only part of the entangled states remain.

$$

|success, success\rangle + |failure, failure\rangle
$$

On quantum entangled states, here is an introduction returned by Zhipu Qingyan AI:

Quantum entangled states are a special, non-classical phenomenon in quantum mechanics, describing a strong correlation between two or more particles. Even if these particles are separated extremely far, their states can instantly affect each other.

Suppose we have two particles A and B prepared in a special quantum state—an entangled state. A simple example is one of the Bell states:

$$
\frac{1}{\sqrt{2}} (|00\rangle + |11\rangle)
$$

Here “0” and “1” represent two possible states of a quantum property (e.g., spin direction), and $|00\rangle$ means particle A is in state “0” and particle B is in state “0,” while $|11\rangle$ means A and B are both in “1.”

#### Behavior of entangled particles

When A and B are in the above entangled state, regardless of how far apart they are, the following happens:

- Measurement consistency: Suppose we measure A’s state and find it is “0.” Because A and B are entangled, B’s state instantly becomes “0,” even if far away. If we measure B and find “1,” then A’s state instantly becomes “1.” This immediate correlation is a key feature of entanglement.
- Randomness: In the entangled state, when we measure A or B, we cannot predict whether we will get “0” or “1,” since the entangled state is a superposition of these outcomes. However, once we measure one, the other’s state becomes determined and correlated with the first measurement.
- Non-locality: Quantum entanglement exhibits non-locality, meaning A’s state can instantly influence B’s state without any signal traveling between them. This violates classical physics’ local realism, which says physical effects cannot be transmitted instantaneously.

===========Zhipu Qingyan AI creation completed========

### The most fundamental difficulty in distributed systems: Not knowing

In mortal eyes, the world is full of annoying uncertainty. Every action has three possible outcomes: 1) success, 2) failure, 3) unknown. Once upon a time, isolated single-machine systems offered a utopia—binary worlds of good and bad, success and failure, light and darkness. But the real world sobers us: in a world ruled by contingency, inherent uncertainty creates the essential difficulty of distributed systems.

When the outcome of an action is unknown, what can we do? The answer: we can only wait for feedback—either passively wait for the executing entity to return a result or actively probe and wait for the outcome. For example, to determine whether a data export task successfully produced an output file, the only method is to check whether the file exists and the data is complete after executing the generation task. If probing fails, we can only retry repeatedly.

## VI. Variants of Paxos

See [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://escholarship.org/uc/item/9w79h2jg)

### Fast Paxos

If you are very confident you’re the first to propose a value, you can safely skip Phase 1 and go straight to Phase 2 commit. Fast Paxos uses rnd=0 to immediately attempt a Phase 2 write, but requires the quorum size to be `n*3/4`.

> In round one, Proposers bypass the Leader and connect directly to Acceptors, attempting to write.

If a value wants to be committed quickly, it must not only be recognized by a majority, but also by the majority of the majority to be safely committed.

At `t=0` multiple Proposers may run concurrently and attempt to write multiple values. If round one fails, round two uses normal Paxos. But at the start of round two, we cannot, as in normal Paxos, choose the value of the largest time because at `t=0` there might be multiple values. Fortunately, we can still choose the value of the majority of the majority. If the fast round wrote `n*3/4`, then the majority-of-majority’s value must be the consensus value.

$$
[\frac n 2] = (\frac n 4) + [\frac n 4]
$$

> If at most less than 1/4 of values differ from consensus, then in the majority (1/2+), more than 1/4 of values must be the consensus value.

Of course, the rnd=0 phase may have failed to write a fast quorum; choosing a majority-of-majority is still safe. If no majority-of-majority exists, consensus has definitely not been reached, and you may choose freely.

### Flexible Paxos

To strive for survival in a world of chance and uncertainty, we can only cooperate sincerely to form a collective consciousness that transcends the individual. Individuals can perish, while the collective achieves eternal life via metabolism. An interesting question is: is a Majority the only means to form collective consciousness? Clearly not. Spiritual inheritance only needs seeds to exist.

Consider a Grid Quorum example:

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_grid_quorum.png)

For the `3*6` Acceptors composing this grid, we can stipulate that writing any single column’s quorum suffices to achieve consensus. Clearly, any two columns are disjoint. To avoid contradictions, we must build a horizontal bridge: require Phase 1 reads to read at least one row. Suppose a consensus has emerged at some time; then the next consensus must read a row and then write a column. Because any row and any column intersect, a row read must see the consensus value; thus the newly written value must remain consistent with the previous consensus. Note that the row quorum and the intersecting column quorum in this example do not reach a majority, and their total size is 3+6-1=8, still not a majority. So the read and write quorums need not be identical nor be a majority; as long as they intersect, information can be transmitted.

Flexible Paxos points out that Phase 1 and Phase 2 quorums must intersect, but quorums within the same phase need not intersect. This means any quorum system with this property can be used, not just majority quorums. Phase 1 (read) + Phase 2 (write) is an atomic operation during time freezing (logical time fixed), so only the results of Phase 1 Quorum + Phase 2 Quorum need be mutually exclusive. Then two time points on the primary timeline won’t overlap, and a later time point can see the value at an earlier one.

Define $Q_{2c}$ as the set of Phase 2 quorums in classic Paxos:

$$
\forall Q\in Q_1,\forall Q' \in Q_{2c}: Q\cap Q' \ne \emptyset
$$

> Original Paxos requires all quorums used to intersect.

Flexible Paxos helps performance. For example, in Multi-Paxos, Phase 1 runs far less often than Phase 2, so we can minimize Phase 2’s quorum size.

If applied to Fast Paxos, we must also consider the fast round quorum requirement:

$$
\forall Q\in Q_1, \forall Q',Q''\in Q_{2f} : Q\cap Q'\cap Q'' \ne \emptyset
$$

$Q\cap Q' \cap Q''$ being non-empty excludes the conflict where $Q\cap Q'$ selects X and $Q\cap Q''$ selects Y.

Translating to inequalities over quorum sizes:

$$
q_1 + q_{2c} \gt n\\
q_1 + 2q_{2f} \gt 2n
$$

Two $Q_{2f}$ overlap in $2q_{2f}-n$ elements; the condition that this overlap intersects $Q_1$ is:

$$
q_1 + (2q_{2f} -n) > n
$$

### Quorums need not be majorities

The only requirement for a Quorum is: quorums must intersect. For example, requiring all quorums to include a designated element a is legal, though not fault tolerant: `{{a,d},{a,b},{a,c}}`.

Also note: if $A \cap B \ne \emptyset$ and A is not contained in B, then the intersection of A with B’s complement is also non-empty, i.e., $A\cap \bar B \ne \emptyset$. This implies for a set of intersecting, mutually non-containing quorums:

$$
Q_i \cap Q_j \ne \emptyset \Longrightarrow Q_i \cap \bar {Q_j} \ne \emptyset
$$

That is, in a set of quorums, if we pick any $Q_j$ and take its complement, that complement intersects every other quorum.

### Even-numbered node clusters

Typically, a cluster has an odd number of nodes (e.g., 5). If it has 4 nodes, network partitions can cause insufficient fault tolerance. Suppose the cluster’s nodes {a,b} are in datacenter 1 and {c,d} are in datacenter 2. A majority requires at least 3 nodes. If the two datacenters partition, each side has only 2 nodes—not enough for a majority—so Paxos cannot proceed.

One solution is to expand the quorum composition: add `{a,b},{b,c},{a,c}` into the quorum set (i.e., maximize the quorum set so Paxos can proceed by satisfying any one quorum; the more quorums available, the better). Originally, the 4-node majority quorums are `{a,b,c},{b,c,d},{a,c,d},{a,b,d}`. Clearly, `{a,b}` overlaps with each majority quorum, so it can be added to the quorum set. By adding some non-3-node quorums, we can overcome the network partition difficulty and still run Paxos during partitions.

This is a general strategy for even-node clusters: the expanded quorum set is $Major(D)\cup Major(D \backslash \{d\})$—take the even-node majority quorums as the base, then remove any one node d (choose arbitrarily based on needs), consider the majority quorums over the remaining odd-node set, and merge results.

> If the even-node count is 2n, then M(2n) = n+1, and M(2n-1) = n, so M(2n)+M(2n-1) = 2n+1, exceeding the total 2n. Every quorum in $Major(D)$ intersects every quorum in $Major(D \backslash \{d\})$.

### Multi-Paxos and Raft

For a unifying view of Raft and Paxos, see xp’s article [Unifying Paxos and Raft into one protocol: abstract-paxos](https://zhuanlan.zhihu.com/p/488629044). For Raft’s paper translation, see [raft-zh_cn.md](https://github.com/maemual/raft-zh_cn/blob/master/raft-zh_cn.md)

Paxos solves a single-decision problem—how to agree on a single value in a distributed system. In practice, we need to make decisions continuously.

Multi-Paxos applies Paxos to the Replicated State Machine problem. In RSM, each state change is recorded as a log entry. Multi-Paxos ensures that the order and values of these entries are consistent across replicas. This is achieved by running Paxos per-entry—i.e., each log entry corresponds to a Paxos instance.

To ensure in-order commit of log entries, Multi-Paxos assigns an increasing logIndex to each entry, requiring logIndex to be continuous with no holes (otherwise it’s hard to determine whether all valid entries exist). This implicitly correlates adjacent entries.

Essentially, Multi-Paxos supports some parallelism across multiple log entries. Determining each entry’s value can be out of order, but commit (applying to the state machine) must be strictly ordered. For example, we can determine the value for `logIndex=100` first, but that entry’s action cannot be applied to the state machine directly; only when entries for logIndex=1..99 are all determined can we apply from 1 onward. So proposals can be out of order while application must be strictly ordered.

Multi-Paxos optimizes the continuous application of Paxos by introducing a Leader role and state sharing. First, all proposals are handled by the leader, reducing conflicts and simplifying interactions. Second, once the leader has obtained a Promise, it can reuse the Proposal ID repeatedly (distinguished with logIndex), skipping Phase 1—equivalent to adding multiple log entries during a single time-freeze.

Raft can be considered an improvement or supplement to Multi-Paxos. It provides clear solutions for many technical details that Multi-Paxos glosses over (but also introduces more constraints). Below are key points relative to Multi-Paxos returned by Zhipu Qingyan AI:

1. Clarity: Raft provides explicit solutions for many technical details not clearly specified in Multi-Paxos—e.g., log replication, leader election mechanisms, log consistency checks.
2. Understandability: Raft’s design goal includes improving understandability. It reduces the number of states and simplifies state transitions.
3. Restrictions: While Raft simplifies things, it introduces constraints—e.g., requiring in-order commit of entries, whereas Multi-Paxos allows out-of-order proposal (even though application must still be ordered).

Specific improvements and constraints:

- Leader election: Raft uses randomized timers to elect leaders, reducing conflicts and making the election process clearer.
- Log replication: In Raft, the leader directly replicates log entries to followers, whereas Multi-Paxos may need to handle more out-of-order cases.
- Safety: Raft includes mechanisms (e.g., pre-vote and log matching properties) to enhance safety.
- Cluster membership changes: Raft provides explicit joint consensus for membership changes; Multi-Paxos usually needs extra logic.

==========Zhipu Qingyan AI creation completed=========

Raft’s leader election uses local timers—effectively introducing comparable local time as new knowledge. Whereas Paxos only needs a logical clock induced by causality, Raft uses some form of physical clock. Note that time is our world’s intrinsic source of consistency; introducing a physical clock simplifies consistency handling. For example, Google’s TrueTime uses precisely timed atomic clocks—introducing a bounded-precision absolute clock—and simplifies distributed transactions.

Below is supplementary explanation from Zhipu Qingyan AI:

Physical vs logical clocks:

- Physical clocks are based on real time, while logical clocks (e.g., Lamport clocks) capture event ordering without relying on real time.
- Introducing physical clocks can simplify consistency because they provide a globally consistent time reference, allowing comparison of timestamps or intervals across nodes.

Google’s TrueTime:

- Spanner uses TrueTime, which combines atomic clocks and GPS clocks to provide very precise time. TrueTime offers a time interval ensuring bounded synchronization error across nodes.
- TrueTime simplifies distributed transactions because it allows higher certainty in transaction timestamps, supporting external consistency (ordering consistent with global time).

======Zhipu Qingyan AI creation completed=======

### Membership changes

See xp’s article [Pitfalls TiDB encountered in Raft membership changes](https://zhuanlan.zhihu.com/p/342319702)

Raft’s original paper proposed single-step changes (one member at a time) and joint consensus (changing multiple members at once), advocating single-step changes. Later, problems were found and there were flaws in the original algorithm; ultimately, joint consensus became best practice both in theory and in practice.

Consider migrating from cluster C1 (abc) to cluster C2 (def):

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/join-consensus.png)

In the Paxos family, we only need to consider what happens on the primary world’s timeline, ignoring micro-level details—this includes when the leader switches. Paxos’s safety essentially derives from the fact that at a designated moment t only one quorum performs writes (mutual exclusion of quorums). Whether the write is initiated by a leader is irrelevant. Paxos does not depend on leader election; the leader exists solely for performance optimization.

Membership change is essentially switching from C1’s timeline to C2’s. At t1 (C1’s logical time), the cluster config is C1. At t2, we initiate the switch—aligning the two timelines: at t2 we must write the joint cluster config on both C1’s and C2’s timelines. From t2 onward, the two timelines are synchronized; anything must happen simultaneously on C1’s quorum and C2’s quorum. That means t2 is the commit moment where Major(C1) + Major(C2) contains the configuration. At t3, we switch to C2’s configuration—its effect begins when the C2 config is successfully written in Major(C1) and Major(C2). After t3, only C2 remains alive; unneeded nodes in C1 can exit.

From t2 to t3, all proposals must pass Major(C1) and Major(C2) simultaneously. We first switch to a coarser timeline, then to a finer one. Directly switching from C1’s timeline to C2’s can cause split brain if both have majorities at the crossover. We need the interval t2..t3 to glue the two timelines together.

From the primary timeline imagery, joint consensus is intuitive: the primary timeline exists only with quorum agreement. If timelines need to be glued, the gluing segment must achieve agreement on Major(C1)+Major(C2).

### Ghost reappearance

See [How to solve “ghost reappearance” in distributed systems](https://www.infoq.cn/article/YH6UVyFLyN3oOOk1Ag7B), [Consensus protocols: post-leader-switch predicament—log recovery and ghost reappearance](https://zhuanlan.zhihu.com/p/652849109)

Multi-Paxos can encounter “ghost reappearance,” where logs not previously confirmed by a majority reappear in later operations, causing inconsistency or duplicate processing. That is, entries not committed under the previous leader may be committed by the next leader—transitioning from unknown to committed.

![](https://pic3.zhimg.com/80/v2-5035bf2fa2a2fdceabe67334d5517834_1440w.webp)

Round 1: A is elected leader and writes logs 1–10. Logs 1–5 form a majority and are acknowledged to clients; clients time out on 6–10.

Round 2: A crashes; B becomes leader. Since B and C’s largest logID is 5, B does not re-confirm 6–10 and writes new logs starting at 6. Clients cannot query 6–10 then. Logs 6–20 are written in round 2, but only 6 and 20 persist on a majority.

Round 3: A is elected leader again. From the majority it sees max logID=20 and re-confirms logs 7–20, including A’s 7–10. Now clients querying see 7–10 reappear like ghosts.

To avoid this, a new leader should first write a StartWorking log—a new epoch—and ignore all unfinished work from prior epochs.

## Generalized Paxos

See [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://mwhittaker.github.io/publications/bipartisan_paxos.pdf)

Multi-Paxos and Raft consider linear logs: entries form a total order, where earlier entries must execute first. In reality, some entries’ execution order can be swapped, as long as there are no conflicts (e.g., read-after-write, write-after-read). For example, commands `a=1` and `b=2` are independent and can swap order. Generalized Paxos computes a partial order of dependencies between log entries using a dependency service, forming a conflict graph.

> Partial order is a mathematical concept describing a “partial” ordering among elements of a set. In a partial order, some elements can be compared, others cannot.

1. Dependency service: This service computes dependencies between entries. Dependencies can be data (e.g., one operation writes a value that another reads) or control (e.g., specific sequence requirements).

2. Conflict graph: Based on dependencies, build a graph whose nodes are log entries and edges represent dependencies. If there is no path between two nodes, they are considered swappable—their execution order can be reversed.

Generalized Paxos deployment:

proposers: at least f+1 nodes

dependency service nodes: 2f+1 nodes

acceptors: 2f+1

replicas: at least f+1

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/BPaxos.png)

$deps(v_x)$ is the union of dependency sets returned by at least f+1 dependency service nodes.

If the dependency service receives x, then receives y conflicting with x, it adds an arrow from $v_y$ to $v_x$, and $v_x \in deps(v_y)$.

Two invariants are maintained:

Consensus invariant: For each vertex v, at most one value `(x,deps(v))` can exist—i.e., submit the value together with its dependencies.

Dependency invariant: A formal description of conflicts in the dependency graph. If x and y conflict, then either $v_x \in deps(v_y)$ or $v_y\in deps(v_x)$ (or both).

![](https://picx.zhimg.com/80/v2-e367275aa567652df6bbc7d3ff79aab1_1440w.webp)

Two conflicting operations may be initiated simultaneously; messages may arrive at different dependency service nodes in different orders, producing different dependencies across nodes. Even if the service’s conflict graph is acyclic, the replicas’ conflict graph can still have cycles.

Once a log item and its dependencies reach consensus, each replica uses a deterministic sorting algorithm to order log items. The sorted results are consistent across replicas, so they can be applied in the same order to the state machine.

## VII. Other timeline techniques

For mortals, time is a magical prior. It seems that all our coordination essentially exploits the direction provided by the arrow of time. In our plane, Sir Isaac Newton first discovered that time splits cause and effect—cause on the left, effect on the right—writing the immortal “Newton’s 2nd law”:

> F = m* a, cause = linear coefficient * effect

Later, the unparalleled genius Albert Einstein broke centuries of mental shackles by pointing out: the timeline is not unique!

If the timeline is not unique, how do we avoid losing direction? One choice is to remember all timelines—this forms “vector clocks.” If we choose to align all timelines into a single one, that becomes the Paxos algorithm.

Do we have other choices? Imagine if we could completely free ourselves from the shackles of causality, traversing the timeline freely, with no past or future—how cool would that be. Cause on the left, effect on the right; why not invert them? Essentially, systems are non-commutative: swapping left-right does not yield the same result. Only in a high-magic world—no left/right, no front/back—can we cast true tenth-level magic: reversing causality. Consider CRDT data structures?

### Vector clocks

Paxos fuses multiple local timelines into a single coarse timeline, while vector clocks record multiple timelines simultaneously, preserving partial-order causal dependencies.

In a vector clock, each node maintains a vector with logical clock values for itself and all others. Below is an example from Zhipu Qingyan AI.

### Assumptions

Assume a distributed system with three nodes: A, B, and C.

### Initial state

Each node’s vector clock starts as:

- A: [0, 0, 0]

- B: [0, 0, 0]

- C: [0, 0, 0]
  Each number in the vector represents the logical clock value of the corresponding node.
  
  ### Event sequence
1. Event 1: A experiences an event, updating A’s clock:
   
   - A: [1, 0, 0]

2. Event 2: A sends a message to B; upon receipt, B updates its clock:
   
   - B: [1, 1, 0] (A’s clock value does not change; B’s clock increases by 1)

3. Event 3: B experiences an event, updating B’s clock:
   
   - B: [1, 2, 0]

4. Event 4: B sends a message to C; upon receipt, C updates its clock:
   
   - C: [1, 2, 1] (A and B’s values obtained from B; C’s clock increases by 1)

5. Event 5: C experiences an event, updating C’s clock:
   
   - C: [1, 2, 2]
     
     ### Analysis
     
     By comparing vector clocks:
- Event 1 happened before Event 2, since A’s clock was already updated in Event 2.
- Events 2 and 3 are concurrent? (Note: the original explains 2 and 3 both happen at B with B’s clock increasing in 3; typically 3 happens after 2 at B. If treated as concurrent events across nodes, concurrency analysis relies on vector comparisons.)
- Event 4 happened before Event 5, since C’s clock increases after receiving B’s message.
  This simple example shows how vector clocks record event order and causal relationships in distributed systems.

======Zhipu Qingyan AI creation completed=====

### CRDT data structures

See [How to design CRDT algorithms](https://www.zxch3n.com/crdt-intro/design-crdt/)

CRDT (Conflict-free Replicated Data Type) is a specially designed data type for achieving eventual consistency in distributed systems. CRDT’s core is allowing replicas to update independently without a central coordinator; when replicas eventually merge, the merge is conflict-free and ensures consistency. CRDT’s mathematical foundation is Semi-Lattice theory.

Below are key relationships between CRDTs and Semi-Lattices, from Zhipu Qingyan AI:

1. Partially Ordered Set (POS):
   
   - Mathematically, a POS is a set with a partial order satisfying reflexivity, antisymmetry, and transitivity.
   - CRDT states form a POS, where the merge operation defines the partial order. If state A can become state B via merges, then A ≤ B.

2. Lattice:
   
   - A lattice is a POS in which any two elements have a least upper bound (join) and a greatest lower bound (meet).
   - In CRDTs, any two states can be merged to a new state, which is their join. The merge must be associative and commutative—consistent with lattice definitions.

3. Joins and meets:
   
   - In CRDTs, the merge corresponds to the lattice’s join. For example, G-Counter merges by taking the per-position maximum across arrays—i.e., the join.
   - CRDTs often don’t need meets because they are usually designed to change monotonically in one direction (e.g., counters only increase). If needed, meets can be defined by taking minima.

4. Consistency guarantees:
   
   - Lattice theory provides CRDT consistency proofs. Since merges always produce the join, even if replicas undergo updates in different orders, merging yields the same final state.
   - This is because lattice properties guarantee that, regardless of merge order, the final result is the same.

5. Commutativity and associativity:
   
   - CRDT merges must be commutative and associative, matching lattice operation properties.
   - This ensures the final result is invariant to merge order and grouping.
   
   =====Zhipu Qingyan AI creation completed====

In applying CRDTs, if we regard each edit as a Delta, the key is that the Delta composition operation satisfies commutativity. Then as long as we collect all Deltas, regardless of arrival order or composition order, we obtain a consistent final result.

## Summary

The magical imagery presented is not merely a heuristic analogy. It can be formally defined at the mathematical level: local timelines -> primary world timeline -> time freezing on the primary timeline, translating the magical description into a rigorous proof.

Below is a more formal statement organized by Zhipu Qingyan AI based on this article and hints:

1. Definition and properties of logical timestamps:
   
   - Each Acceptor maintains a logical timestamp t that strictly monotonically increases throughout the algorithm.
   - At any logical timestamp t, at most one event (e.g., writing a value) can be recorded. Any attempt to occur at earlier timestamps (Promise or Accept messages) is ignored to maintain monotonicity.
   - Logical timestamp increments reflect system state changes. No change indicates no significant event; each timestamp corresponds to a unique, significant change.

2. The concept and maintenance of the primary timeline:
   
   - The primary timeline is formed by aligning the logical timestamps of a majority of Acceptors, ensuring continuity and consistency.
   - When a majority accepts a Promise request, they update local timestamps to the request’s t, achieving alignment and creating a moment t on the primary timeline.
   - If a majority of Acceptors, at timestamp t, accept and write the same value X, the value X is determined on the primary timeline.
   - If writes are inconsistent or some Acceptors don’t write, time t is in an uncertain state. If a majority did not write, time t is considered “not written.”
   - In a system with 2f+1 Acceptors, at most f can fail. This ensures that at any moment, a majority is alive and intersects, maintaining the primary timeline’s continuity. Each logical timestamp records at most one determinate value; as long as a majority is alive, the value is not lost. If consensus wasn’t achieved at t, even if some Acceptors wrote and then failed, this uncertainty does not affect eventual consensus. We focus on values on the primary timeline that are determined or potentially determined.

3. Role and handling of Promise requests:
   
   - The Proposer attempts a proposal by sending Promise requests to a majority, carrying a logical timestamp t to unify timelines.
   - Upon receiving Promise responses, if any response contains a consensus-reached value, the Proposer must adopt it to prevent overturning consensus. If the value isn’t yet consensus, the Proposer may choose any value. In any case, adopting the value corresponding to the largest timestamp t among responses is safe.

4. Accept requests and time freeze:
   
   - After obtaining majority Promise responses, the Proposer sends Accept requests.
   - If a majority accepts an Accept request, the logical timestamp t remained frozen throughout communication—ensuring consistency and correctness.
   - Acceptors ignore Promise requests with t less than or equal to their max accepted t, ensuring t increases monotonically.
   - With multiple competing Proposers, at most one can write successfully at any logical time t on the primary timeline, preventing concurrency issues and ensuring sequentiality.

=======Zhipu Qingyan AI creation completed======   
Basic imagery: ensure single-write consistency via time freezing + analyze on the primary timeline

For any logical time t, once a majority of Acceptors accept the same value X, a write of X appears on the primary timeline—consensus is reached—and the algorithm can end. Otherwise, keep increasing logical time and retry. The micro condition for a successful write on the primary timeline is: freeze time t on a majority of Acceptors, then write the value. If any other event happens during the micro freeze-to-write window, the write fails and you retry.

To ensure consensus, once reached, cannot be overturned, the primary timeline must read-before-write. If the previous time point already wrote X, the subsequent time point must also write X. Irreversibility implies monotonic development on the timeline: not written -> written but value uncertain -> definite value. Therefore, we only need to inspect the latest time t to obtain the newest knowledge.

Analyzing Paxos correctness requires considering only the primary timeline, not whether there’s a leader, when the leader switches, nor micro-level random failures of Acceptors. Regardless of micro details, knowledge obtained on the primary timeline is deterministic and unique. It’s like the chaotic motion of individual molecules at the micro level—yet the collective exhibits stable macro phenomena like temperature.
<!-- SOURCE_MD5:0a9ae27ca650815a50e7723e35919a33-->
