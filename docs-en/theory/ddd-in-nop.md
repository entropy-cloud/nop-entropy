# A Long-Form Article Explaining DDD Best Practices in Low-Code Platforms

A wave of renaissance for DDD has been triggered in the context of microservices, yet there is much debate over whether its best practices can be standardized into a technical framework. In the process of implementing DDD, many design meetings are filled with pointless disputes over technical details, with nobody able to convince anyone else.

Is DDD’s advantage that it better adapts to the object ecological environment? Or that it better unifies stakeholders’ mental models, making management-level resistance explicitly expressed in the technical world? Better mapping between technology and business? Or is there some mathematically provable technical inevitability? This article, combined with the technical implementation of the open-source low-code Nop platform, analyzes the technical core of DDD.

The concept of state encapsulation in object orientation is not that important. The most chaotic code actually arises when encapsulation prevents legitimate operations, leading to hacky workarounds.

DDD can only realize its greatest value in a low-code platform or model-driven framework.

Co-evolution of platform and business logic. It is not that platform rules are fixed with business expressed under those rules; rather, new rules can be continuously customized. This customization does not require going through the platform vendor; the business layer can customize on its own.

## Effective Organization of the Design Space

1. Vertical partitioning
2. Horizontal partitioning
3. Organization across time and space: lifecycle, service singleton

After partitioning, look for similarities, then abstract the common parts.

1. Divide-and-conquer naturally yields multi-layer decomposition
2. Set bottlenecks at the boundaries

Structured models rather than flat domain services.
Relationship to dependency injection: separate the object tree from the business flow

## Domain Language

* A language can be regarded as a descriptive coordinate system
* Define a domain semantic space through the coordinate system
* Multiple domain spaces correspond to different coordinate systems; semantically equivalent parts need to be glued together
* Business expression is independent of technical implementation
* Value chain: some propose being guided by finance
* The value of domain modeling is to transform domain nouns into domain models, requiring abstraction and refinement. For example, abstract tellers and lobby managers into roles, digging deeper into the business essence rather than staying at the surface—this is the most important outcome of DDD.

## Aggregate Root
Aggregation is not for implementing transactions

* Primary decomposition dimension, Facade pattern
  Object-oriented rather than DTO-oriented, not ID-oriented
* Global relationships + internal local relationships. Maps at different scales
* Information at your fingertips
* Entity + lazy loading + session cache: structural aggregation
* BizObject: behavioral aggregation
* GraphQL: selection and composition of structure

An aggregate root is first and foremost a logical aggregation. The slicing construction of BizModel is a form of aggregation, which is not the traditional inheritance or composition in object orientation. Moreover, the dual concept of aggregation is dynamic slicing, and GraphQL happens to provide a dynamic slicing capability when retrieving information. This allows us to maintain a large aggregate-root concept at the conceptual level while loading only small amounts of data in practice each time. Without the dual slicing concept, the aggregate root itself becomes bloated and a performance burden.

Understand aggregation from the perspective of coordinates

Data is fetched for use; purely historical data queries should go through dedicated query services.

## Business Aspect
The same entity + different configurations produce differently named objects. These objects can use extends to inherit existing parts and apply Delta modifications.

## Anemic or Rich

* Balanced division of labor
* Different stability and information scope
* Generic CRUD problems + specific business problems. Different problem subspaces can use different solutions; there is no need for a single uniform treatment. BizService does not include CRUD.

## CQRS
* Should it be wrapped as an explicit Command form? ApiRequest + domain-specific structure; the only purpose of the generic structure is to introduce extension data in a standard way. But at the implementation layer, there is no need to express it explicitly; an explicit DomainCommand abstraction is not necessary.
* Combined with TCC, requests need to be persistable
* Representations differ between the boundary layer and the internal model layer: id => entity. Internal vs external perspectives
* Difference between domain events and commands. Events as a modeling entry point. State Delta

## Delta Update

* Domain events as Delta
* Completeness required by reversible transformations
* Fixed notifications vs mutable commands
  Facts that have occurred (certain) vs operations that trigger events (uncertain)
* Multiverse: read-write separation

## Logical Orchestration

Logical orchestration is function-oriented; each unit is a function. The implementation of a function may simply trigger a method on a service object—this is a secondary concern.

From domain concepts, DDD should also include orchestration; the elements involved in orchestration can be based on domain terminology. However, traditional DDD is oriented toward data modeling, and orchestration is content not yet addressed.

Domain-oriented orchestration requires abstracting domain logic, mapping it to corresponding concrete technical entities, and then manipulating them through various means. Traditionally, data modeling is relatively mature, but logic modeling is weak, especially lacking systematic means to use the modeling results after modeling.

## Implementation Technology

In SQLAlchemy, to change a lazy-loaded attribute to eager loading, you need to specify it when fetching the data.

Abstract via deep implementation: A generates B, then run B; this is simpler than handling multiple possible Bs at runtime simultaneously. Multi-stage, with each stage handling part of the complexity, and that complexity not being propagated to the next stage but gradually reduced.

## Summary

1. Object orientation is a very natural abstraction approach
2. Aggregate roots originate from aggregation and boil down to selection
3. IoC containers manage object lifecycles along the time dimension
4. Proper distribution of information can minimize the scope of perturbations
5. Refactor all understanding around Delta

<!-- SOURCE_MD5:27420dd642b67301b95ef42539505c4c-->
