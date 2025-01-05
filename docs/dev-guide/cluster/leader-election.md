# Leader选举

## 基于数据库存储

### Follower

1. 检查Cluster是否已经有Leader记录存在。Leader记录中的leader不应该是当前节点，如果是，则出现未知错误，删除Leader记录，重试。
2. 如果存在，且未超时则调度下一次检查任务。
3. 如果存在，且超时，则尝试更新，提升epoch。如果更新失败，则调度下一次检查任务。
4. 如果不存在，则尝试插入，如果插入失败，则调度下一次检查任务。

对于follower，当数据库时间超过expireAt之后，认为Leader租期超时。

### Leader

1. 检查Cluster是否已经有Leader记录存在。Leader记录中的leader应该是当前节点。
2. 如果Leader记录不存在，或者leaderId不是当前节点，则出现未知错误，则触发转入follower模式，然后重试。
3. 如果`currentTime < expireAt - leaseSafeGap`，则可以安全的认为没有超时。
4. 否则先尝试续期，如果续期失败，则重新进入选举流程
