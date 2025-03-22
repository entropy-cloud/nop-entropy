# Leader Election


## Database Storage Based


### Follower

1. Check if the Cluster already has a Leader Record. The leader in the Leader Record should not be the current node. If it is, then an unknown error occurs, delete the Leader Record and retry.
2. If there exists a Leader Record but it is not yet timeout, schedule the next check task.
3. If there exists a Leader Record and it has become timeout, attempt to renew it using epoch. If renewal fails, schedule the next check task.
4. If no Leader Record exists, attempt to insert a new one. If insertion fails, schedule the next check task.

For follower, if the database time exceeds expireAt, consider the leader as having expired.


### Leader

1. Check if the Cluster already has a Leader Record. The leader in the Leader Record should be the current node.
2. If no Leader Record exists or the leaderId does not match the current node, then an unknown error occurs, transition to follower mode and retry.
3. If `currentTime < expireAt - leaseSafeGap`, consider the leader as not yet timeout.
4. Otherwise, attempt to renew the lease. If renewal fails, transition back to election flow

