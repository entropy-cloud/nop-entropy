# Leader Election

## Based on Database Storage

### Follower

1. Check whether the Cluster already has a Leader record. The leader in the Leader record should not be the current node; if it is, an unknown error has occurred—delete the Leader record and retry.
2. If it exists and has not timed out, schedule the next check.
3. If it exists and has timed out, attempt to update and increment the epoch. If the update fails, schedule the next check.
4. If it does not exist, attempt to insert; if the insert fails, schedule the next check.

For a follower, when the database time exceeds expireAt, the Leader lease is considered expired.

### Leader

1. Check whether the Cluster already has a Leader record. The leader in the Leader record should be the current node.
2. If the Leader record does not exist, or the leaderId is not the current node, an unknown error has occurred; switch to follower mode, then retry.
3. If `currentTime < expireAt - leaseSafeGap`, it is safe to assume no timeout.
4. Otherwise, first attempt to renew the lease; if renewal fails, re-enter the election process.
<!-- SOURCE_MD5:78621df8b54fa03b682618a1f0c75176-->
