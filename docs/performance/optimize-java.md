# Java配置优化

## ZGC

```
--add-opens=java.base/java.lang=ALL-UNNAMED -Xms3500m -Xmx3500m -XX:ReservedCodeCacheSize=256m -XX:InitialCodeCacheSize=256m -XX:+UnlockExperimentalVMOptions -XX:+UseZGC -XX:ConcGCThreads=1 -XX:ParallelGCThreads=3 -XX:ZCollectionInterval=60 -XX:ZAllocationSpikeTolerance=4 -XX:+UnlockDiagnosticVMOptions -XX:-ZProactive  -Xlog:safepoint,classhisto*=trace,age*,gc*=info:file=/opt/gc-%t.log:time,tid,tags:filecount=5,filesize=50m
```
