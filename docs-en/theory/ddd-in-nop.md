# DDD in Low-Code Platforms: Best Practices

In the context of microservices, DDD has experienced a new wave of popularity. However, standardizing its best practices into a cohesive technical framework remains a subject of debate. During the implementation of DDD, many design meetings are dominated by unnecessary disputes over technical details, leaving little room for constructive dialogue.

Is DDD better suited to adapt to object ecosystems or to unify domain knowledge, address management layer resistance, and establish mappings between technology and business? Are there mathematical layers that can provide evidence for a necessary technological evolution? This text combines the technical implementation of the open-source low-code platform "Nop" with an analysis of DDD's technical core.

In object-oriented programming, state encapsulation is less critical than it seems. The most chaotic code often stems from encapsulated states that cannot be legally operated on using legitimate means; instead, developers resort to hack methods to bypass these restrictions.

DDD's value lies in its ability to achieve maximum impact either within a low-code platform or model-driven frameworks.

The evolution of platforms and business logic is not about fixing rules and letting businesses operate within them. Instead, businesses can continuously define new rules without relying on third-party vendors for platform customization. This allows the business layer to take ownership of its own rules.

## Effective Organization of Design Space

1. Vertical Splitting
2. Horizontal Splitting
3. Time and Space Organization: Lifecycle, Service Singleton

After splitting, look for similarities and extract common parts.

1. Divide et impera leads to multi-level decomposition.
2. Setting boundaries can help avoid bottlenecks.

Structured models over flat models are generally preferred. Dependency injection is better understood in terms of object trees and business flow separation.

## Domain Language

- A language can be seen as a coordinate system.
- Through the coordinate system, define a domain semantic space.
- Different domains may correspond to different coordinate systems; equivalent meanings must be merged.
- Business expressions are independent of technical implementations.
- Value chains: guided by financial considerations.
- The value of modeling lies in abstracting and refining domain concepts, such as converting cashiers into roles, deeper understanding of business essence rather than surface-level details.

## Aggregation Root
Aggregations aren't just for transaction management.

- Primary decomposition: decompose based on main properties.
- Facade pattern: handle complex operations without exposing internal details.
- Not object-oriented but focused on DO (data objects).
- Global relationships and local relationships must be considered simultaneously.
- Entities, lazy loading, and session caching contribute to structural aggregation.
- BizObject for behavior aggregation.
- GraphQL for structural selection and combination.

Aggregation roots begin with logical aggregation. BizModel's slice construction is an aggregation approach, not traditional object-oriented inheritance or composition. Dynamic slicing via GraphQL provides a dynamic slicing capability at information retrieval time. Without dual slicing concepts, the aggregation root becomes bloated and drags performance.

From a coordinate system perspective, understanding aggregation.

Data collection for use is the primary goal. Pure historical data queries should be handled by dedicated query services.

## Business Facade
Same entity with different configurations leads to distinct objects, which can be modified via extends.

## Blood or Nutrient?

Balanced partitioning.
Different stability and scope levels.
Common CRUD problems vs specific business challenges. BizService doesn't include CRUD operations.

## Command Query Responsibility Separation (CQRS)
- Is there a need to encapsulate commands as clear DomainCommand forms? ApiRequest + domain-specific structure, general structures for universal data extension. No explicit abstraction needed at the implementation level.
- Combine with TCC for persistent transactions.
- Boundary layer and internal models differ in representation: ID vs entity.

## State Delta
Domain events as deltas.
Reversible transformations require completeness.
Fixed notifications vs variable commands.
Fact (determined) vs operation (indeterminate).

## Data Separation

Reads and writes separated by domain.

## Logical Arrangement
Logic is a function. Each unit is a function. Traditional inheritance vs composition.

From the domain perspective, DDD also involves coding practices: abstraction, separation of concerns, testing patterns. However, these are not covered in this text.

## Implementation Technology

sqlalchemy's lazy loading attribute can be changed to eager loading by specifying it during data retrieval.

Through deep abstraction:
A generates B, then runs B, while keeping B simple. Each phase handles partial complexity without passing it to the next stage.

## Summary

1. Objectification is a natural abstraction method.
2. Aggregation stems from aggregation, leading to selection.
3. IoC manages object lifecycles through containers.

4. A reasonable distribution of information can minimize the disturbance impact range
5. A differential reconstruction approach provides a comprehensive understanding