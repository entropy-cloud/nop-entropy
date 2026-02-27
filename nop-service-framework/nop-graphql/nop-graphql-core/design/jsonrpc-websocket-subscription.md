# JSON-RPC WebSocket Subscription Protocol

## 1. Overview

JSON-RPC 2.0 compatible WebSocket subscription protocol. Uses `method` as the GraphQL operationName (`BizObjName__bizAction` format). Auth token is passed via HTTP headers during WebSocket handshake.

## 2. Protocol Format

### 2.1 Request

```json
{
  "jsonrpc": "2.0",
  "method": "TestSubscription__onUserChanged",
  "params": { "userId": "1001" },
  "selection": "id,name",
  "id": "sub-1"
}
```

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `jsonrpc` | string | Yes | Fixed value "2.0" |
| `method` | string | Yes | Operation name in `BizObjName__bizAction` format |
| `params` | object | No | Subscription parameters |
| `selection` | string | No | Field selection (e.g., "id,name,status") |
| `id` | string | Yes | Unique subscription ID |

### 2.2 Streaming Response

Data is returned directly in the `result` field (RPC style, not wrapped in `data`):

```json
{
  "jsonrpc": "2.0",
  "id": "sub-1",
  "result": { "id": "1001", "name": "Alice" }
}
```

### 2.3 Complete Notification

```json
{
  "jsonrpc": "2.0",
  "id": "sub-1",
  "result": { "complete": true }
}
```

## 3. Built-in Methods

| Method | Description |
|--------|-------------|
| `ping` | Heartbeat check |
| `unsubscribe` | Cancel a subscription |
| `tokenRefresh` | Refresh authentication token |
| `<BizObjName__bizAction>` | Subscribe to a GraphQL subscription |

### 3.1 Unsubscribe

```json
{
  "jsonrpc": "2.0",
  "method": "unsubscribe",
  "params": { "id": "sub-1" },
  "id": "cancel-1"
}
```

Response:
```json
{
  "jsonrpc": "2.0",
  "id": "cancel-1",
  "result": { "cancelled": true }
}
```

### 3.2 Token Refresh

```json
{
  "jsonrpc": "2.0",
  "method": "tokenRefresh",
  "params": { "authToken": "Bearer new-token" },
  "id": "refresh-1"
}
```

## 4. Error Codes

### Standard JSON-RPC

| Code | Meaning |
|------|---------|
| -32700 | Parse error |
| -32600 | Invalid Request |
| -32601 | Method not found |
| -32602 | Invalid params |
| -32603 | Internal error |

### Extended for Subscription

| Code | Meaning |
|------|---------|
| -32502 | Too many subscriptions |
| -32503 | Forbidden |
| -32504 | Subscription exists |

**Error Response:**
```json
{
  "jsonrpc": "2.0",
  "id": "sub-1",
  "error": {
    "code": -32603,
    "message": "Internal error"
  }
}
```

## 5. Client Example

```typescript
// Connect with auth header
const ws = new WebSocket('ws://localhost:8080/ws', {
  headers: {
    'Authorization': 'Bearer my-token'
  }
});

// Subscribe
ws.send(JSON.stringify({
  jsonrpc: '2.0',
  method: 'TestSubscription__onUserChanged',
  params: { userId: '1001' },
  selection: 'id,name',
  id: 'sub-1'
}));

// Handle messages
ws.onmessage = (event) => {
  const response = JSON.parse(event.data);
  
  if (response.error) {
    console.error('Error:', response.error);
    return;
  }
  
  if (response.result?.complete) {
    console.log('Subscription completed:', response.id);
    return;
  }
  
  // RPC style: data is directly in result
  console.log('Data received:', response.result);
};

// Unsubscribe
ws.send(JSON.stringify({
  jsonrpc: '2.0',
  method: 'unsubscribe',
  params: { id: 'sub-1' },
  id: 'cancel-1'
}));
```

## 6. Implementation

Uses RPC-style execution (`IGraphQLEngine.subscribeRpc`) instead of GraphQL string parsing:

- `IGraphQLEngine.subscribeRpc` - RPC-style subscription method
- `RpcSubscriptionPublisher` - Transforms subscription data to `ApiResponse`
- `JsonRpcWebSocketHandler` - JSON-RPC WebSocket handler
- `JsonRpcWebSocketErrorCodes` - Error code definitions
- `JsonRpcWebSocketEndpoint` - Quarkus/Spring endpoint
