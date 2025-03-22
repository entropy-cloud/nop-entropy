
In my previous article, [A Simple Introduction to the Paxos/Raft Algorithm](https://mp.weixin.qq.com/s/LD8fDbyPohJkA9sbWGjXkQ), I briefly introduced a visual way to understand the Paxos algorithm. However, some readers have mentioned that even with this simplified explanation, the content still feels overwhelming. This might be because the article covered various aspects of distributed consensus algorithms, which can be complex. Therefore, in this text, I will focus on the core concepts and simplify the explanation further.



The Paxos algorithm is a simulation of a **static nine-level hierarchy**. Once you understand this hierarchy, the rest of the algorithm boils down to some plain technical details. The key idea behind the algorithm is that it allows multiple instances (or nodes) to agree on the same value, even in a distributed system.



Here’s a step-by-step breakdown of how the Paxos algorithm works:

1. **First Step: Check if the first round was successful**  
   If the first round is successful and all instances agree on the same value, we can be 100% sure that consensus has been reached.

2. **Second Step: Retrying if unsure**  
   If there's any doubt about whether the first round was successful, the algorithm will retry the entire process from scratch.

3. **Third Step: Avoiding conflicts in subsequent rounds**  
   Before choosing a new value in the next round, we need to ensure that it doesn’t conflict with the value already agreed upon by other instances.

4. **Fourth Step: Finalizing the decision**  
   If the majority of instances agree on the same value, then this value is accepted as the final result.



The main challenge in distributed systems is ensuring that all nodes reach agreement despite potential delays, failures, or discrepancies in their data.

1. **Understanding the Problem: How to Make Everyone Agree**  
   Imagine three people—A, B, and C—trying to agree on a value. Each person sends their decision to the others. If A says "X," B says "Y," and C says "Z," it’s clear that they’re not in agreement. This is the problem that distributed consensus algorithms like Paxos aim to solve.

2. **The Complexity of Distributed Systems**  
   In a real-world scenario, things get much more complicated. Nodes can be slow, lose messages, or even crash. This adds layers of complexity because the algorithm must account for these uncertainties.

3. **Simplifying the Problem**  
   The Paxos algorithm simplifies this by assuming that most nodes work correctly and only a few might fail. This assumption allows the algorithm to focus on handling failures without needing to consider an endless number of possible scenarios.



1. **The Algorithm in Practice**  
   In practice, the algorithm is implemented with additional steps to ensure that it works even when some nodes are slow or have lost messages. For example:
   - If a node has not received a message by a certain timeout, it assumes the value has already been agreed upon.
   - Nodes can also "guess" which round they are in and use this information to synchronize their behavior.

2. **Why It’s Important**  
   While the algorithm is complex, its simplicity makes it one of the most studied and implemented distributed consensus algorithms. It’s widely used in systems like Google’s ChubbyStornage (a distributed file system) and Apache Kafka (a distributed streaming platform).

3. **For Beginners**  
   If you’re just starting with distributed systems, don’t worry—this is exactly why we have tools like Paxos to help us manage complexity.



Understanding the core concepts of the Paxos algorithm is a great first step toward grasping distributed consensus. Once you get the basics down, the rest is just technical details and edge cases. If you have any questions or feel like something was missing in my explanation, feel free to comment below!

If someone sends a request to stop at 10 AM, ABC will also approve the request because ABC treats everyone equally. This is because ABC only enforces the one-way nature of time concepts and does not specially support your preferences.
If ABC makes such a choice, it means that the previous round's time stopping simulation has been automatically terminated without completing the setting process. Additionally, since the time has moved to 10 AM, the request to stop at 9 AM will be denied.

Why does ABC not persist in waiting for your second message after receiving your time stopping request? This is to ensure system fault tolerance.
If your message gets lost along the way or you write a partial message about something else, ABC will wait indefinitely for your second message. To prevent intermediate processes from failing and allowing the system to continue operating, ABC's best choice is to accept the first message from others and restart the simulation round.

Note that if ABC has previously agreed to stop in the past, it means that at 9 AM, a stopping occurred, even if it was unexpectedly terminated. This implies that the previous time stopping did not successfully set the value.
The previous time stopping was automatically terminated to ensure that stopping does not interfere with each other and can only be executed one after another.

(Indeed, it's magic simulation, so we must consider failure cases. If a simulation fails, as long as no conflicts arise, it can still proceed. Once a simulation fails, it can be retried even by a student. The difference between real magic and simulation magic is clear.)


Will others later successfully simulate stopping at 10 AM again, changing the value back to 2? Because we are implementing a basic consensus algorithm, **there's an additional technical requirement**: once a value is agreed upon, it should not be modified. (If modification is allowed, consider majority response which can lead to undecided cases). Therefore, we need an additional mechanism to prevent such scenarios.

To avoid this situation, the simplest solution is to inform ABC immediately after receiving the first request to stop at 10 AM. This way, if ABC discovers that it has already accepted a value of 1, it will not allow further modifications.
At the time of setting, if ABC finds that a previous value of 1 was set by others, it will communicate this information to the next round's initiator. The initiator then continues the simulation with the value 1.

(Indeed, T1 and T2 are two stopping magics. They cannot interfere with each other and must be executed in sequence.)


The complexity arises when considering cases like message loss or slow responses. To improve fault tolerance, we can modify the strategy so that not all nodes need to respond every time. Instead, allow any two nodes (out of three) to reach consensus.

Let's test this strategy:
- At t time, if node A and B agree on value 1, then node C will follow.
- If node A and C agree, node B will follow.
- If node B and C agree, node A will follow.

This ensures that as long as two nodes agree, the third will follow. The strategy is simple but effective for the given scenario.

(Indeed, it's magic simulation. We must consider cases like simultaneous failures. If both A and B fail simultaneously, C can still continue.)


When performing the initialization and time stabilization simulation of the first phase, we can also require majority response from the network. If AB successfully responds to the Prepare(N) request with a time stabilization at 9:00, then as per the previous section's reasoning, it is clear that no other node in the majority will respond to the Prepare(N) request with a time stabilization at 9:00.

At this point, if both A and B have not received any value, we can arbitrarily choose a value for the second phase. However, if either A or B has already reported having set a value at 8:00, we need to carefully analyze the following two scenarios:
1. Both A and B have responded with a value of 2 at 8:00. In this case, the majority has already accepted the requirement for setting a value, and consensus has been achieved. We can then proceed by selecting a value of 2 for the second phase.
2. A and B have provided conflicting responses. At this point, since no C has reported back, there is a possibility that either the majority has reached a consensus or it hasn't. In such cases, we cannot determine the outcome definitively without further information.

We observe that values are always accepted in the time stabilization state, meaning:
- If A accepts the value at 8:00 (i.e., A has set its local time to 8:00 and committed to stabilizing it), then the majority will also stabilize their times at 8:00.

Because of this, when handling conflicting responses from A and B, we can simply select the largest timestamp value. This ensures that if a previous timestamp was already stabilized by the majority, the same choice will be made in subsequent steps.

This leads to a situation where:
- Initially, no node has any values.
- Then, there might be a value being set by the majority.
- Finally, the majority has definitely agreed on a value.

Selecting the largest timestamp value is not something that is difficult to understand in this context.


## Overall Strategy
The overall strategy is based on the following assumptions:
1. No exceptional conditions are considered during normal operation.
2. The time stabilization simulation is assumed to have successfully completed.
3. We can definitively determine whether consensus has been reached based on the responses from the majority nodes.

In cases where no exceptional conditions are encountered, and the time stabilization simulation is successful, we can conclude that a value has been determined for the consensus. If we encounter an error during this process, we should repeat the simulation until it succeeds.

Finally, after careful analysis:
- The sequence of timestamps forms a strictly increasing order.
- Each timestamp corresponds to a unique value.
- For cases where A and B provide inconsistent responses, selecting the largest timestamp ensures that once a value is chosen by the majority, it will not be changed in subsequent steps.

This means that if at any point a timestamp has been agreed upon by the majority, all subsequent timestamps will follow this decision without any possibility of reversal.

**Multiple Proposals Issue**：In a distributed system, there may be multiple proposers, which can lead to "proposal conflicts". By using a timestamp-based approach, the acceptor will discard all proposals that were made before the current timestamp, thereby reducing some of the conflicts. Then, at each timestamp where a majority of the nodes agree, the majority will only accept one value, simplifying the conflict graph on the majority level. Since each proposer's timestamp is unique and increasing, there will be no duplicates in a single proposal. Therefore, at each timestamp, the latest proposal will be set as the current value if it hasn't been updated yet, or it may have already been set by a local acceptor. However, as long as the majority does not agree on the same value at the same timestamp, consensus has not been reached.

**Failure and Recovery Handling**：In a distributed system, nodes may fail and then recover. Previous analysis shows that if the majority agrees on starting the timestamp-based approach, it can be considered as having started. If the majority considers the timestamp approach successful, it can be considered as successful. Therefore, regardless of when a node fails or recovers, only the majority's aggregated opinion needs to be considered. Additionally, an implicit assumption is that nodes have memory; if a node fails and then recovers, it should not lose its previous memories, i.e., accepted values and committed values.
