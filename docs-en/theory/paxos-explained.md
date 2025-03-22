
The Paxos algorithm is one of the most fundamental algorithms in distributed systems, known for its complexity and intellectual challenge. However, its design choices can feel elusive because we often struggle to intuitively understand why it takes such an approach. While specific examples can validate its correctness and even rigorous mathematical proofs can convince us of its validity, we still find it difficult to answer why this particular method was chosen. Is there no other viable alternative? Can we find an explanation that makes the Paxos algorithm seem almost self-evident without relying solely on mathematical derivations?

In my article "[A Research Report on Paxos' Magical Theory](https://mp.weixin.qq.com/s/CVa_gUdCtdMEURs40CiXsA)", I attempted to build a conceptual image of the Paxos algorithm from the perspective of dimensional magic, aiming to provide a "Why" explanation rather than just a "How-to." This supplement serves as an elaboration on that article.

For foundational knowledge about the Paxos algorithm, refer to xp's articles on Zhihu: [200-line implementation of a key-value store based on Paxos](https://zhuanlan.zhihu.com/p/275710507) and [A Clear Explanation of Reliable Distributed Systems - The Paxos Algorithm](https://zhuanlan.zhihu.com/p/145044486).


## Why Learn the Paxos Algorithm?

Perhaps someone will ask, if my work doesn't involve distributed systems, why bother learning the Paxos algorithm? The answer is yes. Any problem you face that involves multiple state spaces or requires coordinating multiple independent entities will lead you to similar challenges, and the solutions provided by the Paxos algorithm can offer valuable insights.

Some might argue that the Raft algorithm is more popular these days, making the Paxos algorithm seem less common. This perspective can be understood as follows: The Raft algorithm is essentially a variation of the Paxos algorithm, choosing specific implementation strategies under the basic principles of the Paxos algorithm.

In fact, the Paxos algorithm can be viewed as a general framework in the field of distributed consensus, defining the core mechanisms required for achieving agreement. On the other hand, the Raft algorithm is a concrete implementation within this framework, introducing additional rules and steps to achieve consensus. While these rules provide convenience, they are not essential conditions for achieving consensus. Understanding the Paxos algorithm will make it easier to grasp the underlying principles of the Raft algorithm and other consensus algorithms.


## What Is the Paxos Algorithm?

The Paxos algorithm solves the simplest problem of reaching consensus in distributed systems: how multiple nodes can agree on a single value even when failures occur.

A correct consensus algorithm must satisfy the following properties:

**Agreement (Agreement):** All nodes must agree on the same value, and once agreed upon, it will not change.

**Validity (Validity):** The value that reaches agreement must originate from a specific node's proposal.

**Termination (Termination):** Eventually, all nodes will reach an agreement.

These conditions are collectively referred to as "Safety + Liveness," which are the typical requirements for algorithms that need to be both correct and operational. Agreement embodies the basic meaning of consensus, while validity excludes trivial cases, such as all nodes agreeing on a fixed value regardless of external proposals, thus forming a consensus without dynamicity.

The scenario depicted by consensus algorithms: Initially, the system is in an uncertain state, allowing multiple possibilities (e.g., values x and y). **After executing a critical action, the entire system transitions (like water freezing into ice in physics) into a deterministic global coordinated state (freezing on a specific chosen value)**. Following the algorithm's operational rules, all nodes will eventually acknowledge that the value must be x, not y.

> If we sequence all actions of nodes involved in the consensus algorithm in a predetermined order, there will inevitably be a critical action that solidifies the value before and after its execution. For example, if an Acceptor records a value, it forms a majority; if it fails, the majority is still pending, allowing new possibilities.
> 
> Although all nodes operate concurrently, we can later sequence their actions into a single action list and identify the critical transition actions.

Interestingly, could the system enter a state akin to Schrödinger's cat, where it has chosen a value but hasn't actually chosen one? From an observer's perspective, such a state does exist. However, the Paxos algorithm resolves this by incorporating an observation mechanism that ensures ultimate collapse into a deterministic outcome.


### FLP Theorem

Unfortunately, **no consensus algorithm can simultaneously satisfy all three conditions (Agreement, Validity, and Termination) in an asynchronous distributed system**! The FLP theorem (Fischer, Lynch, and Paterson theorem) states that no consensus algorithm can achieve Agreement, Validity, and Termination in a completely asynchronous environment.

The asynchronous model refers to the absence of a global clock, where processes can execute at any speed, and messages can arrive arbitrarily late but are eventually guaranteed delivery.

  
> brilliant. Having realized it, everything else was trivial. **Because
> Thomas and Johnson didn’t understand exactly what they were doing**, their algorithm permitted
> anomalous behavior that essentially violated causality. I quickly wrote a
> short note pointing this out and correcting the algorithm.

Lamport holds a B.S. in mathematics from MIT (1960) and a Ph.D. in mathematics from Brandeis University (1972). He has systematically studied strict causality. Lamport's self-proclamation fully demonstrates that **mastering computer science requires some understanding of physics**!

Einstein imagined sending a photon to explore the surrounding world and discovered a shocking secret: **different places' time is only correlated due to causal order, and event sequences can be different from observers' perspectives**! After reading others' articles describing message sending and timestamps, Lamport immediately realized that message transmission and photon propagation are the same thing. The underlying physical image here is strict causality. Once he understood this, the subsequent logical derivations became trivial! However, **Thomas and Johnson missed out because they didn't fully understand physics. They had no idea of what their own algorithm was really doing**, leading to subtle flaws in their algorithm. Without the guidance of physical imagery, they inevitably stumbled at critical moments.

Interestingly, despite being guided by strict causality, Lamport's article discusses logical clocks extensively but never mentions strict causality, making some people think that his insights were merely the result of a sudden inspiration.

Lamport's original paper on Paxos, *The Part-Time Parliament* (1998), was finally published after a long wait. When it came out, many people claimed they couldn't understand it. In 2001, Lamport wrote *Paxos Made Simple*, opening with the statement: "The Paxos algorithm, when presented in plain English, is very simple." In this article, Lamport provides a rationale for why Poxos was designed that way, but his explanation relies on step-by-step mathematical reasoning. It's like forcing others to accept the algorithm's validity through sheer logic, resulting in people thinking they understand it initially but getting confused later.

Physical education and research place great emphasis on physical imagery; physics students never mechanically apply mathematical rules. They believe in a result because it aligns with reasonable physical explanations. So, for Poxos, we must ask: What is its underlying physical imagery? Does Lamport secretly hold an non-mathematical understanding of Poxos' inevitability, similar to how he kept silent about relativity for those computer scientists?

## 3. Paxos的魔法学图像

The foundation of distributed systems is a chaotic world where entities are born free and die randomly. Conflicts and contradictions are everywhere, yet the Paxos algorithm somehow creates a consistent consensus world within this chaos, resembling a miracle. However, humans are limited by their finite experience and can only speculate about the intentions behind such a phenomenon, inevitably leading to confusion.

The divide between human and divine lies in the divine realm. In that realm, actions follow rules. Rules are established by gods, while people humbly accept them and cleverly exploit them. The so-called "heavenly chasm" is essentially about this. But if humans dare to challenge authority and imagine themselves as rule-breakers, can they truly comprehend the unimaginable in this mortal world? Such presumption leads to a sudden awakening: gods, as the ultimate existence above all finite entities, solve consensus problems in just three steps:

1. God says: There must be time.
2. God says: Time is static.
3. God says: The value should be X.
* Time being static requires that time already exists.

* When time is static, no observable changes occur (e.g., paused gameplay). However, this doesn't mean no changes happen at all—NPCs in a paused game don't notice the pause, for instance.


After time stands still, the divine specifies the same value directly in multiple places. When time flows again, people in different locations will notice this same value suddenly appearing before them, and consensus is thus established.

Our world is now in the Final Epoch, where spiritual energy in heaven and earth has been depleted, magic has dissipated, and true magic no longer exists in this world. However, we still possess a computer. If the underlying foundation of our world is a supercomputer, it is entirely possible that all physical laws in this world are simulated by this machine. Therefore, can we use the computer in our current low-magic world to simulate the high-magic world's magic?

> The essence of a computer is the Turing machine, and the essence of the Turing machine is its ability to simulate any computational process—this is what is referred to as "Turing completeness."

**Paxos Algorithm is an implementation of the ninth-level magic: time stopped.**

Once the true secret of Paxos is understood—which it originates from dimensional magic—the rest merely involves some mundane technical details.

> What we need here is a shift in worldview or cognitive framework: We first design a set of natural laws to achieve our goal and then think about how to implement those laws. It's akin to programming, where we first design an interface before implementing it.

Reflecting on the **Paxos Algorithm**, the series of actions by the Proposer and Acceptor are fundamentally designed to ensure the unidirectional flow of time.

1. The Proposer generates a globally unique and incrementally increasing Proposal ID. This Proposal ID acts as a timestamp, with each ID corresponding to a specific moment in time.

2. The Acceptor receives a Proposal and does not respond with a Proposal ID less than or equal to the current request's Proposal because time flows unidirectionally. A successful Proposal indicates the start of time stopping at moment t. Once time has stopped at t, it cannot stop again at an earlier moment t'. Additionally, since time is already stopped at t, no other Proposer should attempt to stop time at the same moment t; hence, the Acceptor does not respond.

3. The Acceptor receives a Proposal and does not respond with a Proposal ID less than the current request's Accept because the period from Proposal to Accept represents the duration during which time is stopped. Therefore, the Acceptor accepts the Accept corresponding to time t but rejects those for moments before t.

In our low-magic world, simulating magic relies on **cognitive deletion**—removing everything that contradicts magical principles from our awareness. **What you cannot see does not exist!** The Acceptor's peculiar behaviors are merely a matter of ignoring facts that would expose the stopping of time through magic.

![Time Arrow](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/time_arrow.png)

Each Acceptor maintains an ever-increasing Proposal ID, akin to establishing a local timeline. The system aligns these Proposal IDs across all Acceptors, effectively bundling multiple localized timelines into a single coarse-grained, unified timeline. Time's progression resembles waves sweeping across the entire system.

From the divine perspective, the Paxos Algorithm is but a trivial technique: forcibly aligning multiple timelines into a single master timeline using the magic of time stopped.

> Note: Here, "time" refers to our defined logical time, not physical time.


### Interesting Application: Stop Alignment and Optimistic Locking

This "stop-alignment" technique is a fundamental strategy for achieving consensus in distributed systems. For example, in Kafka's message queue, consumers within the same consumer group operate independently but must reach agreement on how to assign work. When the consumer group's membership changes or the topic structure changes, a rebalancing process is triggered. During this process, the Coordinator instructs all workers to stop their current work, migrate to the next generation (epoch), and then receive a new assignment scheme. Each scheme is valid within a single epoch.

The optimistic locking mechanism commonly used in databases employs the same strategy. When entering a processing program, read the version number of MainRecord, then modify MainRecord and its associated SubRecords in a transaction. Finally, attempt to update MainRecord's version number.

```sql
update MainRecord
set version = version + 1
where version = :record_version
```

If the update is successful, it indicates that time stopping occurred during processing, meaning no other users executed conflicting actions.


### Microscopic and Macroscopic Perspectives

Local timelines can be disrupted by various anomalies. In such cases, we need a broader view to distinguish between microscopic and macroscopic perspectives. At the microscopic level, some proposals may succeed while others fail, but as long as the majority (Majority) are successful, we define it as successful at the macroscopic level. A set cannot simultaneously have two Majorities, and a Majority cannot choose both values X and Y; thus, the transition from microscopic to macroscopic is clear.

Understanding the Paxos algorithm starts with focusing on events that rise to the macroscopic world, i.e., those occurring on the main world timeline. Each point in time on the main timeline corresponds to a process of "time halt" in the micro-world—specifically, from a start of time halt to an end of time halt. At each such point in time, there may be an attempt to set a value (if successful, consensus is reached). **The ultimate effect of time halt is fully manifested in the macro world**: on the main timeline, this appears as isolated time points that do not overlap, corresponding to non-intersecting process intervals in the micro-world.

![Paxos Phase 2](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_phase2.png)

**Only events that occur in most small-worlds can ascend to the macro world and become events in the macro world.** The first step of the Paxos algorithm is to obtain commitments from a majority of Acceptors. If successful, the time of these Acceptors aligns, creating a new time point on the main timeline. In other words, **only time points recognized by a majority of Acceptors will appear on the main timeline**, and they also record the start of time halt on the main timeline. If multiple Proposer compete simultaneously, it is possible for different Acceptors to be accessed under the same ProposalID, resulting in no successful commitments. In such cases, this moment is automatically discarded and does not appear on the main timeline. **On a micro-level, multiple competition processes may occur, but what we observe on the main timeline is only the successful competition and the macro-level result of obtaining majority acceptance. During algorithm analysis, we only need to consider the outcomes visible on the main timeline.

Due to the possibility of failure in competition and the independent selection of ProposalIDs by each Proposer, the ProposalIDs visible on the main timeline are not continuous. However, the absolute value of logical time is irrelevant; only their relative ordering matters. Additionally, if necessary, we can renumber the time points on the main timeline after it has been established, starting from 0 and incrementing sequentially.

The mutual exclusivity inherent in a Proposer and Acceptors choosing the same ProposalID at the same time ensures uniqueness and determinism at the macro level. When most small-worlds have written a value A at time t, this fact remains unaltered and undiminished by any subsequent changes or challenges, leading to the event of writing value A being recorded in the macro world.

> Proposer sends a message to multiple Acceptors: "I want time halt at time t3."
> 
> Multiple Acceptors successfully reply: "Agreed. Time has reached t3. The last value set at time t was X."
> 
> Proposer: "At time t3, I will set the value to X."
> 
> Multiple Acceptors successfully process: "Value X has been set at t3."
> 
> Note: Consensus is achieved as long as the Acceptors successfully process; no reply is required.

### Interesting Application: Avoiding Split-Brain

In leader election algorithms, a classic issue is **how to avoid split-brain scenarios**. If a new leader has already been accepted by the majority while an old leader refuses to step down and continues to meddle, what can be done? A general solution is to **treat the old leader as a zombie and ignore all requests from older epochs** (e.g., reject requests based on smaller epoch values). In reality, we do not restrict the behavior of the old leader in its own micro-world; it can delusionally act as it wishes. However, its actions will never rise to the collective intention or influence the macro world. The new leader must **act proactively** by first writing its epoch number to the macro world (similar to modifying a global shared variable), ensuring that any submission from the old leader is automatically rejected via optimistic locking once the new leader has established its position.

In our quantum mechanics-based physics analogy, as quantum theory evolves, observation itself assumes unique theoretical significance. According to quantum field theory's depiction, only certain aggregated outcomes from the virtual time realm reach our visible world. Through the strange phenomenon of quantum tunneling, we can also catch a glimpse of the chaotic processes hidden behind. 

## Four. Monotonicity: From Schrödinger Cat Superposition

In the second phase of Paxos, there is an particularly enigmatic operation: after collecting responses from a majority of Acceptors, if any value is present in the responses, the Proposer selects the Proposal ID with the largest value and proposes it for acceptance. Why would the Proposer discard its original proposal and instead choose another's value? Why specifically the one with the largest Proposal ID?

These questions essentially arise from human limitations.

![Paxos Consensus](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

Consider the case with 5 Acceptor nodes and multiple Proposer nodes. At ProposalID=t1, proposal P1 is accepted by A1 and A2 but does not reach a majority, so no value is determined in this round. Similarly, at ProposalID=t2, proposal P2 also fails to reach a majority. At ProposalID=t3, proposal P3 is accepted by the majority nodes A2, A3, and A4, thus reaching consensus.

**Once consensus is reached at t3, is it possible for us to reach a new consensus P4 at t4?** In this scenario, t3's consensus is P3, while t4's consensus would be P4, with no consensus at t1 and t2. For the almighty God, it is perfectly acceptable to have different values at different times—no problem, because God is all-knowing and all-powerful. However, **for imperfect human beings with limited cognitive abilities, allowing different times to have different consensuses would lead to a conflict of awareness**.

If we allow consensus to be overturned by a being with finite cognitive capacity, how can such a being know which value to use? In many cases (e.g., t1 and t2), no consensus is reached. Would it mean that the being has to traverse from t1 to tn to learn all the values of the consensuses? This would render any foundational consensus algorithm inherently unstable.

> In fact, the definition of consensus explicitly rules out the possibility of a reached consensus being overturned.

![Paxos consensus failure](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus_fail.png)

Considering the scenario depicted in the image above, assume A3 crashes while handling P3. From an external perspective, two possibilities exist:

1. A3 has already accepted P3, thus achieving consensus.
2. A3 has not yet accepted P3, meaning consensus has not been reached.

**Except for A3 itself, no other node knows its operational status. However, since A3 has crashed, it cannot respond to any queries!** This creates a potential quandary: if different times can have different consensuses, the system's history could be in a quantum state of uncertainty, making it impossible to definitively answer whether a consensus was reached or not.

For human beings, the most ideal scenario is for the system to exhibit **monotonicity**: it continually progresses in one direction and, once it reaches the target state, remains perpetually confined within that state. This ensures that at any point, we can extract information by advancing the system further. If the system has reached a consensus, continuing the process will yield the same consensual value. If no consensus exists, we effectively select a value to resolve the uncertainty—e.g., in the example above, running one more step of the Paxos algorithm ensures that regardless of A3's decision at t3, by t4, **the system's uncertainty is eliminated**. This principle is also referred to as the maximum commit rule.

From the macroscopic perspective analyzed earlier, the microscopic world's inherent randomness (constant fluctuations in quantum mechanics) implies that our perceived macroscopic facts may exist in a Schrödinger cat-like state of uncertainty. When a Proposer successfully obtains responses from a majority of Acceptor nodes, it is akin to performing a read operation on the main timeline—but with a side effect.

1. We know that all events on the main timeline can be ordered by their "time points." The consensus occurs at some specific time point on this timeline. The simplest way to maintain consensus consistency is **read before write**—peeking at the current state before performing the write operation.

2. If the read operation returns a value and/or time point t that matches those of the majority, it indicates that time point t's value has been determined. Essentially, consensus has already been reached on the main timeline, and no further action is necessary—simply return the result.

3. However, if the read operation returns values or time points different from those of the majority, it suggests that either one previous time point has achieved consensus or none has. In this ambiguous state, we must ensure that if a consensus exists at t-1, our subsequent actions do not undermine it. Specifically, **if a consensus has been reached, its value is guaranteed to be the ProposalID with the highest value**. Because if t3 achieved a consensus, t4 will necessarily observe that result, and the consensus cannot be overturned. Therefore, if the highest ProposalID does not correspond to the consensual value, it implies that no consensus could have occurred before that time. **The Paxos algorithm's development is monotonic along the main timeline, moving from having no value to uncertainty about a value and finally to a confirmed value**. Thus, we only need to examine the last step's outcome because each ProposalID can correspond to at most one value.

4. In the second phase of the Paxos algorithm, after reading values from a majority of Acceptor nodes, the write operation effectively becomes a purely observational action. This observation has a side effect: the system's state on the main timeline transitions from possibly having set a value (reached consensus) to a deterministic state with a definite value. **In essence, no new value is written; instead, a value is selected from those that were potentially available**—a process analogous to quantum measurement.

$$
|X\rangle + |0\rangle \longrightarrow |X\rangle
$$


The second phase of the Paxos algorithm performs a non-blocking read and write operation atomically once it successfully completes.

> Schrödinger's cat state represents a quantum superposition where the system is in a state of death or alive simultaneously, but not yet observed. Until an observer measures it, the system remains in a state of uncertainty. Upon measurement, the system collapses to either a dead or alive state, and this fact becomes known.

Returning to our earlier example, there's a subtle situation here. At time t1, only two instances of P1 were set, but consensus wasn't reached. At time t2, similarly, only two instances of P2 were set, but no consensus was reached. However, at time t3, in the majority view, either P1 or P2 will be observed, so P3 can only be either P1 or P2 and cannot be any arbitrary value. In the majority view, if all responses are empty, it's safe to conclude that consensus hasn't been achieved yet. If there are already values present, for safety reasons, according to Paxos' rules, the algorithm will select one of these existing values. **Even if we don't follow Paxos' selection rule and directly choose a different value, no conflict will arise**. While Paxos' selection isn't strictly necessary, it's simpler and can help speed up the convergence of the algorithm.

For example, suppose at time t3, all five nodes return values 1, 2, 3, 4, and 5. According to Paxos' rules, we must return one of these values, specifically the one with the largest ProposalID. The question arises: what happens if we choose value 6 instead? If you're following a traditional learning of the Paxos algorithm, this will leave you confused because it deviates from the original analysis. However, using the concept of frozen time and the main timeline, the answer becomes clear: no conflict will arise. Each write operation on the main timeline is performed in isolation, meaning they occur sequentially. The value at a particular point in time can be:

- Not written (if three nodes return empty for that time point),
- Unknown but previously attempted to be written (if it was ever written before),
- Written (if all three nodes returned the same value for that time point).

These states are unidirectional, meaning they transition in only one direction. The only state that affects the correctness of the algorithm is the "written" state. Once a value is written, any subsequent writes to other values would cause conflicts. However, if we explicitly know that no write has occurred, we can safely conclude that consensus hasn't been achieved yet.


### When Consensus Is Reached?

When consensus is reached, does every participant in the system immediately realize that consensus has been achieved? Interestingly, at the exact moment when consensus is reached, **no one, including the Acceptor and Proposer nodes, knows that consensus has been achieved**. However, over time, the algorithm's operation will **gradually reveal this fact to all participants**.

![Paxos Consensus Image](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_consensus.png)

Firstly, we note that before consensus is reached, **Acceptors can change their accepted values**, such as A3 accepting P2 and then accepting P3. This is because the Proposer may become disconnected at any time, so Acceptors must be able to accept new values. When A3 accepts P3, it cannot know whether consensus has been achieved; P3 will become the final chosen value. The same logic applies to A2 and A4, which only know their local situations and cannot determine if the entire system has reached consensus. From the Proposer's perspective, before receiving majority acceptance for its proposal, it also doesn't know if its submitted P3 will be accepted by the majority as the final consensus.

Therefore, **consensus belongs to the system as a whole**, and individual participants need a process to understand whether it has been achieved. This is where the Learner (Learner) role comes into play. The learner first identifies that consensus has been reached and then propagates this information, avoiding the need for each participant to independently collect and reason about information.

There's also an interesting detail here: at time t2, if the majority response comes from A3, A4, and A5, we can propose any value P2 (if the majority includes A1 and A2, then P2 must equal P1 according to Paxos' rules). However, when writing into the majority, it doesn't require writing into A3, A4, or A5; instead, it allows choosing any one of them. This means we can write a different value P2 into A1 (implying no changes occur after that time). To emphasize this point, Lamport explicitly states in his paper: "This need not be the same set of acceptors that responded to the initial requests."

If you haven't established the timeline image, this is an easy point to confuse.


## Five. Leader Based: Copy and Paste

Once time has been frozen, it's wasteful to spend divine power on such unnecessary behavior. A deity with socialist core values and a commitment to frugality wouldn't engage in wanton misuse of divine power. Therefore, once time is frozen, the best course of action for maintaining consistent behavior across dispersed nodes is to use an eight-level magic called "Big Puppeteering," which copies the behavior of one node (referred to as the Leader) onto all other nodes.

### Two-Phase Commit (2PC) and Quantum Entanglement

Two-Phase Commit (2PC) can be viewed as a consistency source provided by the Coordinator, with each Participant gradually becoming entangled with the Coordinator. On the other hand, Paxos establishes a Quorum incrementally, where participants become entangled within the Quorum.

Before 2PC runs, each Participant can independently choose to succeed or fail, meaning the outcome is random. After the first phase completes, if we examine each Participant individually, it can still either succeed or fail, and the outcome remains random. However, when examining the entire state space, only certain entangled states survive.

$$
|成功, 成功\rangle + |失败, 失败\rangle
$$

Regarding quantum entanglement states, here's an introduction from IQ:

Quantum entanglement is one of the most special and non-classical phenomena in quantum mechanics. It describes a situation where two or more particles exhibit strong correlations, even when separated by large distances, with their states influencing each other instantaneously.

Consider two particles, A and B, prepared in a special quantum state, such as a Bell state. One common Bell state example is:

$$
\frac{1}{\sqrt{2}} (|00\rangle + |11\rangle)
$$

In this state, "0" and "1" represent the possible quantum properties of each particle (e.g., spin direction), while $|00\rangle$ denotes particle A in state "0" and particle B in state "0", and $|11\rangle$ denotes particle A in state "1" and particle B in state "1".

#### Behavior of Entangled Particles

When particles A and B are in the above entangled state:

- **Consistency Check**: If we measure particle A's state and find it to be "0", particle B's state will immediately collapse to "0", regardless of the distance between them. Conversely, if we measure particle B and find it to be "1", particle A's state will immediately collapse to "1". This immediate state correlation is a key feature of quantum entanglement.

- **Randomness**: In the entangled state, when measuring particle A or B, we cannot predict whether we will obtain "0" or "1" specifically because the entangled state is a superposition of these two outcomes. However, once we measure one particle, the state of the other particle will immediately be determined and correlated with the result of the first measurement.
- **Non-Locality**: Quantum entanglement exhibits non-locality, meaning that the state of particle A can instantaneously influence the state of particle B without any signal being transmitted between them. This contradicts the local realism of classical physics, which states that physical effects cannot propagate instantaneously.

===========智谱清言AI创作完毕========

### The Most Fundamental Challenge: Uncertainty

In the eyes of ordinary people, this world is filled with uncertainties that cause frustration and anxiety, as every action leads to three possible outcomes: **1. Success, 2. Failure, 3. Uncertainty about the outcome**. Once upon a time, isolated single-machine systems provided an illusion of utopia, making the world a binary place of good and evil, success and failure, light and darkness. However, the real world awakens us from this illusion. In a world dominated by chance, the inherent uncertainty creates the fundamental challenges of distributed systems.

When the outcome of an action is unknown, what can we do? The answer is that we must wait for feedback. This could mean either passive waiting for the entity responsible for executing the action to return its result or active exploration to determine the execution outcome. For example, after completing a data generation task, the only way to verify whether the derived file has been successfully generated and is complete is to check for its existence and integrity after the task has been executed. If an exploration fails, what then? We must simply repeat the attempt.

## Six. Variants of the Paxos Algorithm

See [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://escholarship.org/uc/item/9w79h2jg)

### Fast Paxos

If you are certain that you are the first to propose a value, you can safely skip the first phase and proceed directly to the second phase for submission. Fast Paxos uses rnd=0 to attempt writing in phase 2 once but requires the quorum size to be `n*3/4`.

> In the first round, the Proposer skips the Leader and connects directly with Acceptors to attempt writing.
**To have a value quickly submitted, it must receive majority approval from most members and also meet the requirement of a majority of majorities to ensure safe submission.**

At time t=0, there may be multiple parallel Proposers attempting to write. If the first round fails, the second round begins using the standard Paxos algorithm. However, at t=0, if multiple values have been written, we cannot select the value corresponding to the maximum timestamp from Acceptors as in normal Paxos because t=0 may have multiple values. Fortunately, we can still safely choose the majority-picked value because if fast round successfully wrote into `n*3/4`, then the majority of majorities will have agreed on a shared value.

$$
\left\lfloor \frac{n}{2} \right\rfloor = \left\lfloor \frac{n}{4} \right\rfloor + \left\lfloor \frac{n}{4} \right\rfloor
$$

> At most, less than 1/4 of the values differ from the consensus value. Therefore, among the majority (more than half), there must be more than 1/4 values that match the consensus value.

Of course, it's also possible that rnd=0 failed to write into the quorum. In such cases, selecting the majority-picked value is still a safe choice. If no majority-picked value exists, we can freely choose any value as long as it hasn't been reached yet.

### Flexible Paxos

To survive in an unpredictable world filled with uncertainty and chance, we must collaborate closely and form a collective consciousness that transcends individuality. While individuals may perish, the collective can perpetuate itself through metabolism. An interesting question arises: is the majority (Majority) the sole choice for forming collective consciousness? Clearly not. The propagation of consciousness only requires the existence of seeds.

Consider the example of a Grid Quorum:

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/paxos_grid_quorum.png)

In this example, with `3*6` Acceptors forming a Grid, we can define that **writing into any column** constitutes a quorum for reaching consensus. Obviously, any two columns are disjoint. To avoid making contradictory choices, we need to establish a bridge horizontally, requiring that in the first phase of Paxos, at least one row must be read. If consensus has already been reached at some moment, the next consensus will necessarily go through one row read followed by a column write. Since any row and any column are intersecting, reading a row will inevitably capture the consensus value, ensuring that the new write is consistent with the previous consensus. Note that in this example, neither the row quorum nor the intersecting column quorum constitutes a majority, and their total element count is 3+6-1=8, which also does not form a majority. Therefore, the quorum used for reading and writing does not need to be identical or occupy a majority; as long as they are intersecting, they can adequately transmit information.

Flexible Paxos indicates that the first stage and second stage quorums must intersect, but quorums within the same phase do not need to intersect. This means any system with such properties can be used, not just those relying on majority-based quorums. **The combination of the first stage (read) and the second stage (write) forms an atomic operation in a time-stopped process (with a fixed logical time), so it is sufficient for the results of the first stage Quorum and the second stage Quorum to be mutually exclusive**, ensuring that the timestamps on the two primary timelines do not overlap, and subsequent timestamps can observe the values from previous timestamps.

Define $Q_{2c}$ as the collection of quorums used in the second phase of the classical Paxos algorithm:

$$
\forall Q \in Q_1, \forall Q' \in Q_{2c}: Q \cap Q' \ne \emptyset
$$

> The original requirement of Paxos is that all used Quorums must intersect.

Flexible Paxos improves performance. For example, in the upcoming section on Multi-Paxos, its first phase has a significantly lower execution frequency compared to the second phase, allowing us to minimize the size of the second phase Quorum as much as possible.

If applied to Fast Paxos, we also need to consider the Quorum requirements for the fast round stage:

$$
\forall Q \in Q_1, \forall Q', Q'' \in Q_{2f}: Q \cap Q' \cap Q'' \ne \emptyset
$$

The condition $Q \cap Q' \cap Q'' \ne \emptyset$ helps avoid situations where $Q \cap Q'$ selects a value X and $Q \cap Q''$ selects a value Y, leading to conflicts.

When converted into inequalities regarding the number of members in each Quorum:

$$
q_1 + q_{2c} > n \\
q_1 + 2q_{2f} > 2n
$$

The overlapping elements between two $Q_{2f}$ Quorums is $2q_{2f} - n$, and the condition for intersection with Q₁ is:

$$
q_1 + (2q_{2f} - n) > n
$$


### Quorum Does Not Need to Be a Majority
A Quorum's requirement is that any two Quorums must intersect. For example, all Quorums could include a specific element 'a', making it a valid Quorum without fault-tolerance requirements: {{a, d}, {a, b}, {a, c}}.

Additionally, we note that $A \cap B \ne \emptyset$ and $A \not\subseteq B$ imply that the intersection of their complements is also non-empty:

$$
A \cap B \ne \emptyset \implies A \cap \overline{B} \ne \emptyset
$$

This means that for any set of Quorums, if you choose one $Q_j$, its complement will intersect with all other Quorums. In other words, a group of Quorums where any two have non-empty intersections inherently satisfies the condition that their complements also have non-empty intersections.


### Handling Even-Numbered Node Clusters
Typically, clusters have an odd number of nodes, such as 5. However, if the total number of nodes is even (e.g., 4), the system may face issues during network partitions. For instance, consider a cluster with four nodes: a and b in Data Center 1, and c and d in Data Center 2. The majority Quorum requires at least three nodes. If both Data Centers experience a partition, each will have only two nodes, failing to meet the majority requirement and causing the Paxos algorithm to halt.

One solution is to expand the Quorum set by including additional Quorums, such as {a, b}, {b, c}, and {a, c} (effectively maximizing the Quorum collection to ensure Paxos can run as long as at least one Quorum remains valid). Originally, the majority Quorum for a 4-node cluster is {a, b, c}, {b, c, d}, {a, c, d}, and {a, b, d}. Since {a, b} overlaps with each of these, it can also be included in the Quorum set. By adding some non-majority-based Quorums, we can overcome the partition issue while maintaining Paxos functionality.

This is a general strategy for even-numbered node clusters and can be seen as expanding the Quorum collection to $Major(D) \cup Major(D \setminus \{d\})$, where 'd' is an arbitrarily chosen node. This approach uses the majority of the remaining odd-numbered nodes from the original cluster, ensuring that any partition will leave at least one valid Quorum.

> For a cluster with 2n nodes, $Major(2n) = n + 1$, and for 2n - 1 nodes, $Major(2n - 1) = n$. Thus, $Major(2n) + Major(2n - 1) = 2n + 1$, which exceeds the total number of nodes (2n). Every Quorum in $Major(D)$ will intersect with every Quorum in $Major(D \setminus \{d\})$.


### Multi-Paxos and Raft

The unification of Raft and Paxos can be found in XP's article [Combining Paxos and Raft into a Single Protocol: abstract-paxos](https://zhuanlan.zhihu.com/p/488629044), while the translation of the Raft paper is available in [raft-zh_cn.md](https://github.com/maemual/raft-zh_cn/blob/master/raft-zh_cn.md).

Paxos algorithm addresses the problem of single decision-making, i.e., how to achieve consensus on a single value in a distributed system. However, in practical applications, we always need to make consecutive decisions.

Multi-Paxos is an application of the Paxos algorithm to the Replicated State Machine (RSM) problem. In RSM, each state change is logged as an entry. Multi-Paxos ensures that these log entries are consistent in order and value across all replicas. This is achieved by running the Paxos algorithm during the submission of each entry, meaning each log entry corresponds to a specific Paxos instance.

To ensure sequential submission of log entries, Multi-Paxos introduces an incremental `logIndex` and enforces that `logIndex` must be consecutive (no gaps, as this would make it difficult to determine if all valid log entries have been recorded). This implicitly links subsequent log entries together.

Essentially, the Multi-Paxos algorithm supports a certain degree of parallel processing for multiple log entries. While the determination of each entry's value can be done out of order, their submission (allowing them to be applied to the state machine) must follow a strict sequence. For example, we can propose a log entry with `logIndex=100`, but this entry cannot be directly applied to the state machine until all entries from `logIndex=1` to `logIndex=99` have been determined. Therefore, the proposal of log entries can be done out of order, but their application must follow a strict sequence to ensure consistency and correctness in the state machine.

Multi-Paxos improves the performance of Paxos' continuous application by introducing the Leader role and state sharing. First, all proposals are handled by the Leader, reducing conflicts and simplifying interaction logic. Second, once a Promise is obtained, the Leader can reuse previous Proposal IDs (distinguished by `logIndex`) without reprocessing the first phase of Paxos, effectively increasing the number of log entries submitted during a single period of stability.

Raft can be viewed as an improvement or complement to Multi-Paxos, addressing many technical details in Multi-Paxos with specific solutions while introducing additional restrictions. Below are key points from智谱清言AI's analysis of Raft compared to Multi-Paxos:

1. **Clarity**: Raft provides clear solutions for many technical details in Multi-Paxos that were not well-defined. For example, Raft explicitly defines the process of copying log entries, the mechanism for leader election, and log consistency checks.

2. **Understandability**: One of Raft's design goals is to improve the algorithm's understandability. It achieves this by reducing the number of states and simplifying state transition logic.

3. **Limitations**: While Raft simplifies the algorithm, it also introduces certain restrictions. For instance, Raft requires that log entries must be submitted in order, whereas Multi-Paxos allows log entries to be proposed out of sequence (though their application still needs to follow a strict sequence).

Below are specific improvements and limitations of Raft compared to Multi-Paxos:

- **Leader Election**: Raft uses a random timer for leader election, which helps reduce election conflicts and makes the process more explicit.
- **Log Replication**: Raft's log replication is straightforward, with the Leader directly copying log entries to followers. In contrast, Multi-Paxos may require handling more out-of-order scenarios during log replication.
- **Safety**: Raft enhances system safety through mechanisms such as pre-voting stages and log matching properties.
- **Cluster Membership Changes**: Raft provides a clear mechanism for cluster membership changes (joint consensus), which typically requires additional logic in Multi-Paxos.

==========智谱清言AI完成了创作========

Raft's leader election uses a local timer, effectively introducing a comparable local time, whereas the original Paxos algorithm only relies on causal ordering through logical clocks. Thus, Raft essentially uses a physical clock. It is important to note that time is our world's inherent source of consistency, and the introduction of a physical clock simplifies consistency handling. For example, Google's TrueTime technology uses precise atomic clock timing, which is akin to introducing an absolute clock with specific precision in the system, allowing for simplified distributed transaction handling.

Below are智谱清言AI补充的一些解释：

**Physical Clocks vs. Logical Clocks**

- A physical clock is based on actual time, while a logical clock (such as the Lamport clock) is used to capture the order of events and does not rely on actual physical time.
- Introducing a physical clock simplifies consistency handling, as it provides a global reference time that allows timestamp comparison or interval length assessment across different nodes.

**Google's TrueTime Technology**:

- Google's Spanner database system utilizes the TrueTime technology, which combines atomic clocks and GPS clocks to deliver extremely precise timing services. TrueTime offers a timeframe to ensure bounded synchronization errors across different nodes.
- The introduction of TrueTime simplifies distributed transaction handling by enabling higher determinism in timestamp ordering, thereby supporting external consistency (external consistency refers to the order of transactions matching their global timeline order).

======智谱清言AI创作完毕=======

### Member Changes

See xp's article [TiDB 在 Raft 成员变更上踩的坑](https://zhuanlan.zhihu.com/p/342319702)

The original Raft paper proposed two methods: single-step changes (individual member changes) and Join Consensus (simultaneous multiple-member changes), advocating for single-step changes but later found to have issues. The Join Consensus algorithm became the best choice both theoretically and practically, despite vulnerabilities in the original Raft paper's algorithms.

Considering a cluster C1 composed of members abc migrating to a cluster C2 composed of members def

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/join-consensus.png)

**In the Paxos algorithm series, we only need to consider events occurring on the global timeline, not micro-level details. This includes when a Leader changes, as the safety of the Paxos algorithm fundamentally relies on a specific time t where only one Quorum performs write operations (the mutual exclusivity of Quorums). The identity of the Leader initiating the write is irrelevant. The Paxos algorithm does not depend on leader election; a Leader's existence is merely an optimization for performance.

Member changes essentially involve switching from C1's timeline to C2's timeline. At time t1 (C1's logical time), the cluster configuration is C1, and at time t2, we initiate the switch, effectively preparing to align the two timelines. Thus, t2 must be recorded in both C1 and C2 timelines, meaning that from t2 onwards, both timelines synchronize, with all events occurring simultaneously in C1's Quorum and C2's Quorum. Therefore, t2 is the commit time when Major(C1) and Major(C2) are submitted as cluster configurations. At time t3, we switch to C2 configuration, which takes effect from Major(C1) and Major(C2)'s successful writes in C2. After t3, only C2 remains active, allowing unused nodes in C1 to exit.

From t2 to t3, all proposals must be simultaneously approved by Major(C1) and Major(C2), effectively transitioning from a broader timeline to a finer one. Directly switching from C1's timeline to C2's is impossible due to overlapping Quorums at the intersection point, leading to potential split-brain scenarios. The interval between t2 and t3 ensures both timelines are glued together.

The Join Consensus algorithm becomes highly intuitive when examining the main timeline: a consistent Quorum exists only on the global timeline, with the joining phase requiring consensus in Major(C1) + Major(C2).

### Ghost Replication Issue

See [How to solve "Ghost Replication" in distributed systems](https://www.infoq.cn/article/YH6UVyFLyN3oOOk1Ag7B), [Consensus Protocol: The Dilemma After Leader Election - Log Recovery and Ghost Replication](https://zhuanlan.zhihu.com/p/652849109)

The Multi-Paxos protocol may encounter a so-called "Ghost Replication" issue, which occurs when unconfirmed logs from previous rounds reappear in subsequent operations, leading to inconsistencies or duplicate processing. This means that logs not confirmed by a majority during the previous Leader's term can later be confirmed by a new Leader, transitioning from an unknown state to a committed state.

![](https://pic3.zhimg.com/80/v2-5035bf2fa2a2fdceabe67334d5517834_1440w.webp)

Round 1: A is selected as Leader and writes logs 1-10. Logs 1-5 form a majority and are acknowledged by clients, while logs 6-10 receive no acknowledgment due to client timeouts.
Round 2: A fails, B becomes Leader. B's largest log ID is 5, so it does not reconfirm logs 6-10 but starts writing new logs from 6 onwards. Clients querying at this stage will find logs 6-10 missing. Subsequent writes enter logs 6-20, with only logs 6 and 20 successfully persisted in the majority.
Round 3, A is again selected as the Leader, allowing it to obtain the maximum logID of 20. This requires executing a reconfirmation for logs numbered from 7 to 20, which includes A's logs 7-10. Subsequent queries from the client will reveal that these 7-10 logs have reappeared like ghosts.

To prevent this issue, the new Leader must first write a `StartWorking` log upon assuming leadership, initiating a new epoch and ignoring any incomplete work from previous epochs.

## Generalized Paxos

Refer to [SoK: A Generalized Multi-Leader State Machine Replication Tutorial](https://mwhittaker.github.io/publications/bipartisan_paxos.pdf)

Both Multi-Paxos and Raft algorithms assume a linear log where the entries form a total order (total ordering): any log entry that comes before another must be executed first. However, in practice, certain log entries may have their execution order reversed without causing conflicts, such as `a=1` and `b=2`, which are independent operations and can be executed in either order. Generalized Paxos addresses this by using dependency services to compute partial ordering relationships between log entries and constructing a conflict graph.

> Partial Order (Partial Ordering) is a mathematical concept used to describe "part of an ordering" within a collection. In a partial order, certain elements can be compared, while others cannot be directly compared.

1. **Dependency Service**: This service is responsible for computing dependencies between log entries. These dependencies can be data dependencies (e.g., one operation writes to a data item that another operation reads) or control dependencies (e.g., certain operations must be executed in a specific order).

2. **Conflict Graph**: Based on these dependencies, a conflict graph is constructed where nodes represent log entries and edges represent dependency relationships. In this graph, if two nodes are not connected by a path, they can be considered interchangeable, meaning their execution order can be swapped.

Deployment configuration for Generalized Paxos:

proposers: At least f+1 nodes

dependency service nodes: 2f + 1 nodes

acceptors: 2f+1 nodes

replicas: At least f+1 nodes

![](https://gitee.com/canonical-entropy/nop-entropy/raw/master/docs/theory/paxos/BPaxos.png)

$deps(v_x)$ represents the set of dependencies computed by at least f+1 dependency service nodes.

When the dependency service receives x and a conflicting y, it adds a node $v_y$ to the arrow pointing to $v_x$, where $v_x \in deps(v_y)$.

The algorithm maintains two invariants during its execution:

Consensus Invariant: For every vertex v, at most one value $(x, deps(v))$ can exist, meaning the value and its dependencies are submitted together.

Dependency Invariant: This formally describes the conflict relationships in the dependency graph. If x and y conflict, then either $v_i \in deps(v_y)$, or $v_y \in deps(v_x)$, or both must be satisfied.

![](https://picx.zhimg.com/80/v2-e367275aa567652df6bbc7d3ff79aab1_1440w.webp)

Two conflicting operations can be initiated simultaneously, and the order in which their messages reach different dependency service nodes may differ, leading to potentially differing dependency relationships across service nodes. Even if the conflict graph maintained by the dependency services is acyclic, the replica's conflict graph may still contain cycles.

Once a log entry and its dependencies achieve consensus, each replica can apply a deterministic sorting algorithm to order the log entries. Since all replicas produce consistent ordering results, these entries can be applied in the same sequence to the state machine.

## Other Time-Related Technologies

For mere mortals, time is a magical pre-existing entity. Our coordinating work essentially leverages the direction provided by time arrows. On this plane, Newton, the first of the greats, discovered that time splits causality, with cause on one side and effect on the other, leading to his famous Second Law:

> F = m * a, where force (F) is proportional to linear acceleration (a), cause (m) multiplied by effect.

Later, the unparalleled genius Albert Einstein broke free from centuries of thought constraints and first proposed that **time lines are not unique**!

If time lines are not unique, how do we avoid getting lost? One approach is to **remember all time lines**, forming what is known as **Vector Clock (Vector Clock) technology**. Alternatively, if we choose to align all **time lines into a single one**, this becomes the **Paxos algorithm**.
  We have other options? Imagine if we could completely break free from the chains of causality and freely navigate through timelines, neither caring about the past nor the future. What a breeze that would be! On one hand, cause; on the other, effect. Why not flip them? Essentially, this is because the system does not satisfy the exchange law. When you reverse left and right, you don't get the same result. Only in a world with high entropy can we not distinguish between left and right, nor past and future. There, we can truly wield **Level 10 Magic: Reverse Causality**. Ever heard of CRDT data structures?

### Vector Clock

Paxos essentially merges multiple local timelines into a coarse-grained timeline, while a vector clock simultaneously records multiple timelines, preserving the partial order dependency between causally related events.

In a vector clock, each node maintains a vector containing its own logic clock value and those of all other nodes. Below is an example provided by Zhiyu AI.

### Assumptions

Assume a distributed system with three nodes: A, B, and C.
    - The merge operations of CRDT must be commutative (order-independent) and idempotent (group-independent), which aligns with the properties of linear algebra operations.
    - This means that regardless of the order in which merge operations are performed or how they are grouped, the final result will always be the same.

    =====智谱清言AI创作完毕====

In the application of CRDT data structures, each edit is treated as a Delta. The key property of CRDT is that the combination operation for Deltas satisfies commutativity, ensuring that the collection of all Deltas, regardless of the order in which they are received or processed, results in a consistent final state.

## Summary

The magic image described in this paper is not merely an inspirational analogy but can be strictly defined at the mathematical level: local timelines → global timelines → global timeline's static point. This allows the description of magic images to be translated into a strict mathematical proof.

Below is a more rigorous formulation generated by智谱清言AI based on the text description and some hint information:

1. **Definition and Properties of Logical Timestamps**:
   
   - Each Acceptor maintains a logical timestamp t, which strictly increases throughout the execution of the algorithm.
   - At any logical timestamp t, at most one event can be recorded, such as writing a specific value. Any attempt to record an event with an earlier timestamp (such as Promise and Accept messages) will be ignored to maintain the monotonic increase of the timestamp.
   - The increasing nature of logical timestamps reflects changes in system state. No change indicates no important events have occurred, and each timestamp corresponds to a unique and important change in the system's state.

2. **Concept and Maintenance of the Global Timeline**:
   
   - The global timeline is formed by aligning the logical timestamps of the majority of Acceptors, ensuring continuity and consistency.
   - When a majority of Acceptors accept a Promise request, they update their local timestamps to the timestamp t provided in the request, creating a moment on the global timeline at time t.
   - If all accepting Acceptors write the same value X at time t, then X is considered determined on the global timeline.
   - If writes are inconsistent or only a minority of Acceptors have written, then time t is considered undecided. If a majority of Acceptors do not write, then time t is marked as unwritten.
   - In a system with 2f+1 Acceptors, at most f Acceptors can fail, ensuring that the majority of Acceptors are always alive and share some common state, maintaining continuity of the global timeline. Each logical timestamp can record at most one determined value, provided the majority of Acceptors are still active. Once consensus is reached on a value, it will not be lost. Even if some Acceptors write values before failing, as long as the majority has written, their undetermined state does not affect the final determination of consensus on the global timeline. We focus on values that have been determined or may have been determined on the global timeline.

3. **Function and Handling of Promise Requests**：
   
   - The Proposer sends a Promise request to the majority of Acceptors, carrying a logical timestamp t aimed at synchronizing their timelines.
   - Upon receiving a Promise response, if it contains a value that has already reached consensus, the Proposer must adopt this value as its proposal to prevent an already determined consensus from being overturned. If the response contains a value that is not yet determined, the Proposer can choose any value as its proposal. In either case, adopting the maximum timestamp t corresponding to the response as the new proposal is always a safe choice.

4. **Sending Accept Requests and Logical Time Stabilization**：
   
   - After receiving responses from the majority of Promise requests, the Proposer sends an Accept request.
   - If the majority of Acceptors accept the Accept request, it indicates that the logical timestamp t remains static during communication, ensuring the algorithm's consistency and correctness.
   - Acceptors ignore Promise requests with timestamps less than or equal to the largest timestamp they have already accepted, ensuring the logical timestamp t increases monotonically.
   - In cases of multiple Proposer competition, at most one Proposer can successfully write to the global timeline at any given logical timestamp t, preventing concurrency issues and ensuring operations are executed in sequence.

=======智谱清言AI创作完毕======
Basic Image: Ensuring consistency through time freezing at a single write + analysis on the global timeline

At any logical timestamp t, once a majority of Acceptors have accepted writes of the same value X, that value X is written to the global timeline, consensus is reached, and the algorithm can successfully terminate. Otherwise, the logical timestamp is incremented, and the process repeats. The micro conditions for successful writing on the global timeline are: freezing time t among the majority of Acceptors, followed by a write. If any other events occur during this frozen-to-write process, the write fails, and the process must be retried.

To ensure that once consensus is reached, it will not be overturned, the global timeline requires read before write. If the previous timestamp has already written a value X, subsequent timestamps can only write X. Consensus cannot be overturned, meaning the timeline's development is monotonic: unwritten -> written (but undetermined) -> determined value. Therefore, checking the result at the latest timestamp t will provide the most up-to-date information.
Analysis of the correctness of the Paxos algorithm only needs to consider what happens on the primary timeline, without needing to worry about whether a Leader exists, when the Leader changes, or whether the underlying Acceptors might fail randomly. Regardless of how fine-grained details change at a microscopic level, the macroscopic timeline yields deterministic and unique knowledge, much like how observing each individual molecule in the microscopic world may seem chaotic, but a large collection of molecules can lead to stable macroscopic phenomena such as temperature.
