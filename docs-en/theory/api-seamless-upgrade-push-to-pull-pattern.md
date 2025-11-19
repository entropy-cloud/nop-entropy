# API Seamless Upgrade Solution: Architectural Evolution from Push Mode to Pull Mode

Someone on Zhihu asked a question: **How to achieve smooth upgrades for Java microservice API version compatibility?**

In microservice architecture, frequent service iterations lead to increasing differences in API versions, while the upgrade pace of clients (such as Apps, Web frontends) often lags behind. This frequently causes compatibility issues and can even lead to online failures. Common version control strategies, such as adding version numbers in the URL path (`/v1/user`) or using request headers to distinguish versions, although they can clearly differentiate between different versions, also bring the heavy cost of maintaining multiple versioned interfaces and increase the adaptation difficulty for clients.

This article will analyze the root cause of this problem and introduce **how the NopGraphQL framework innovatively solves this issue**.

## 1. Root Cause: The Covariance Problem Caused by Push Mode

REST is essentially a "push mode," which inevitably leads to the **covariance problem** at a theoretical level.
The design paradigm of REST APIs is for the server to predefine the complete data structure (DTO) returned by each endpoint. The client passively receives this data and cannot control the content granularity. This model of "server push, client full acceptance" is, in information theory, a **closed output system**.

Once the server makes changes to the return structure—whether adding fields, modifying nested structures, or adjusting field semantics—all clients consuming this interface must adapt synchronously. This forms a typical **covariance coupling**: the server and client are forced into a strong binding on versions, violating the core principle of "independent evolution" in microservices.

Even using version control methods like URL paths (`/v1/user`) or Accept Headers only makes the coupling explicit but does not eliminate the fundamental problem: **each version is still a rigid, full data contract**, and the maintenance cost increases linearly or even exponentially with the number of versions.

```java
// Rigid data contract of REST interface
@GetMapping("/api/v1/users/{id}")
public UserDTOV1 getUserV1() {  // Fixed structure of version 1
  return userService.getUser();
}

@GetMapping("/api/v2/users/{id}")
public UserDTOV2 getUserV2() {  // Fixed structure of version 2
  return userService.getUser();
}
// Each version is a complete DTO, changes require new interfaces
```

Worse, when `UserDTO` is embedded in the responses of multiple different APIs (such as order details, approval workflows, notification centers), any change to it will trigger a **ripple effect**, causing a chain reaction of modifications across numerous interfaces, forming a typical "combinatorial explosion."

## 2. Solution: Reverse the Information Flow, Shift to Pull Mode

GraphQL proposes a disruptive idea: **the client declares the required fields, and the server returns them on demand**. This "client-driven pull model" naturally supports **progressive evolution**, and its core lies in **decoupling the strong binding between server information integrity and client consumption granularity**.

```graphql
# 2018 Client - Only requests basic fields
query {
  getUser(id: "123") {
    id
    name
    email
  }
}

# 2020 Client - Starts using newly added security fields
query {
  getUser(id: "123") {
    id
    name
    email
    twoFactorEnabled  # New field, old clients unaffected
    lastLoginIp
  }
}

# 2023 Client - Uses the full feature set
query {
  getUser(id: "123") {
    id
    name
    email
    twoFactorEnabled
    lastLoginIp
    preferences {     # New nested object
      theme
      language
    }
  }
}
```

In the "push mode" of traditional REST, the server must predefine a fixed response structure for each interface. This means:
- The server and client must have a completely consistent understanding of "what constitutes valid data";
- Once the server model expands (e.g., the user object adds a `twoFactorEnabled` field), either all clients must be forced to upgrade to handle the new field, or multiple versions of DTOs and endpoints must be maintained.

The pull model fundamentally changes this paradigm:
- **The server acts as a complete, authoritative source of information, continuously evolving its domain model**;
- **The client, based on its own scenario, only pulls the required subset of fields**.

### Core Advantages of GraphQL Pull Mode:
- New fields are invisible to old clients;
- Field removal can be gradually phased out through deprecation markers;
- Nested queries avoid multiple round trips while maintaining fine-grained control.

However, fully switching to the GraphQL protocol within existing Java microservice systems faces significant obstacles:
- Requires refactoring infrastructure like gateways, authentication, rate limiting, and monitoring;
- Clients (especially mobile or third-party) need to rewrite calling logic;
- Teams need to master new syntax, type systems, and performance tuning patterns;
- Difficult to unify with other communication methods like gRPC and message queues.

Therefore, although GraphQL's ideas are advanced, **protocol binding limits its implementation efficiency in legacy systems**.

## 3. Innovative Solution: NopGraphQL's Multi-Protocol Universal Framework

The key innovation of NopGraphQL is: **elevating GraphQL from a transport protocol to a universal information operation engine**. It extracts the core idea of GraphQL—"field-level dynamic selection"—and generalizes it into a capability reusable across protocols.

In Nop, the same service function can be exposed simultaneously as:
- A REST interface (via the `@selection=name,email` query parameter)
- A GraphQL query
- A gRPC method
- A Kafka message handler
- A batch job entry point

Developers only need to write the business logic once:

```java
@BizModel("NopAuthUser")
public class UserBizModel {
    @BizQuery
    public NopAuthUser getUser(
        @Name("id") String id,
        FieldSelectionBean selection  // Automatically injects client field selection info
    ) {
        // Same business logic, reusable across multiple protocols
        NopAuthUser user = dao.getById(id);

        // Optional: Decide whether to load expensive fields based on selection
        if (selection != null && selection.hasField("totalOrders")) {
            user.setTotalOrders(orderDao.countByUserId(id));
        }

        return user;
    }
}
```

It can then be invoked via multiple protocols.

### GraphQL Protocol Invocation:
```graphql
query {
  NopAuthUser__get(id: "123") {
    id
    name
    email
    roles {
      name
      permissions
    }
  }
}
```

### REST Protocol Invocation:
```http
GET /r/NopAuthUser__get?id=123&@selection=id,name,email,roles{name,permissions}
```

NopGraphQL converts requests from different protocols into a standardized internal representation through a unified protocol adaptation layer:

```
GraphQL Request → GraphQL Adapter → Unified Service Call Engine → Business Function
REST Request   → REST Adapter    → Unified Service Call Engine → Business Function
gRPC Request   → gRPC Adapter    → Unified Service Call Engine → Business Function
```

Business logic is completely decoupled from the protocol. Developers only need to focus on the domain model and field loading logic; protocol adaptation is handled automatically by the framework. This allows teams to **gradually introduce "pull mode" capabilities** without changing existing call chains.

## 4. Core Mechanism: Field Selection and Default Strategy

NopGraphQL features a refined design for field return strategies, simplifying GraphQL and making it naturally mappable to the REST protocol.

- Each entity type can define a default field set `F_defaults` (e.g., `id, name, status`);
- When the client does not explicitly pass `@selection`, the fields in `F_defaults` are automatically returned, behaving equivalently to traditional REST, ensuring backward compatibility;
- **All newly added fields are marked as `lazy` by default**: Unless explicitly requested by the client in `@selection`, they are not loaded or returned;
- Clients can use the `...F_defaults` syntax to quickly inherit the default field set and add new fields.

For example:
```http
GET /r/NopAuthUser__get?id=123&@selection=...F_defaults,avatarUrl,roles{name}
```
Means: "Return all default fields + `avatarUrl` + the `name` subfield of `roles`".

> 💡 **Implementation of Lazy Fields**: In the XMeta meta-model, a field can be declared as `<prop name="avatarUrl" lazy="true">`, and batch loading can be implemented using the `@BizLoader` annotation to avoid the N+1 problem.

This approach preserves the simplicity of REST while granting the flexibility of GraphQL. The server can freely extend the model, and the client consumes on demand, **completely decoupling the evolution pace of the API**.

### Conclusion

The essence of API version compatibility is not managing multiple versions, but **eliminating unnecessary coupling**.
By stripping the "pull idea" of GraphQL from the protocol and implementing it in multi-protocol scenarios through the `@selection` + `F_defaults` + `lazy` field mechanism, NopGraphQL provides a **low-intrusion, highly compatible, easily evolvable** smooth upgrade path for Java microservices.

In the future, the backend should no longer be a pile of rigid REST endpoints, but rather a **living information space**—where clients can pull the required knowledge precisely, securely, and efficiently, just like querying a database.