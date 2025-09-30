# Performance Optimization Tools

## Using Async Profiler

```
jbang --javaagent=ap-loader@jvm-profiling-tools/ap-loader=start,event=cpu,file=profile.html -m dev.morling.onebrc.CalculateAverage_yourname target/average-1.0.0-SNAPSHOT.jar
```

or directly on the .java file:

```
jbang --javaagent=ap-loader@jvm-profiling-tools/ap-loader=start,event=cpu,file=profile.html src/main/java/dev/morling/onebrc/CalculateAverage_yourname
```

## Using perf

```
perf stat -e branches,branch-misses,cache-references,cache-misses,cycles,instructions,idle-cycles-backend,idle-cycles-frontend,task-clock -- java --enable-preview -cp src Blog1
```
<!-- SOURCE_MD5:2b2d856419b279a671ae82a362709084-->
