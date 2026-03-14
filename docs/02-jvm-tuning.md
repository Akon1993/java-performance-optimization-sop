# JVM调优完整指南

## 目录
1. [JVM内存模型](#1-jvm内存模型)
2. [垃圾回收器选择](#2-垃圾回收器选择)
3. [内存参数配置](#3-内存参数配置)
4. [GC日志分析](#4-gc日志分析)
5. [内存泄漏排查](#5-内存泄漏排查)
6. [性能监控](#6-性能监控)

---

## 1. JVM内存模型

### 1.1 内存结构图

```
┌──────────────────────────────────────────────────────┐
│ 堆内存（Heap）                                         │
│  ┌─────────────┬─────────────┬──────────────────┐    │
│  │  Eden区     │ Survivor 0  │ Survivor 1       │    │
│  │  （新生代）  │   （From）   │    （To）         │    │
│  └─────────────┴─────────────┴──────────────────┘    │
│  ┌────────────────────────────────────────────────┐  │
│  │ 老年代（Old Generation）                        │  │
│  └────────────────────────────────────────────────┘  │
└──────────────────────────────────────────────────────┘

非堆内存（Non-Heap）：
- 元空间（Metaspace）：类元数据
- Code Cache：JIT编译后的本地代码
- 直接内存（Direct Memory）：堆外内存
- 线程栈（Thread Stack）：每个线程的私有内存
```

### 1.2 内存参数速查

| 参数 | 说明 | 推荐值 |
|------|------|--------|
| -Xms | 初始堆内存 | 与-Xmx相同 |
| -Xmx | 最大堆内存 | 物理内存的1/4~1/2 |
| -Xmn | 新生代大小 | 堆内存的1/4~1/3 |
| -XX:MetaspaceSize | 元空间初始大小 | 128m~256m |
| -XX:MaxMetaspaceSize | 元空间最大大小 | 256m~512m |
| -Xss | 线程栈大小 | 512k~1m |

---

## 2. 垃圾回收器选择

### 2.1 GC选择指南

| GC算法 | 适用场景 | 特点 | JDK版本 |
|--------|----------|------|---------|
| Serial | 单核CPU、小内存 | 单线程，简单 | 全版本 |
| Parallel | 批处理、高吞吐 | 多线程并行 | 全版本 |
| CMS | 低延迟要求（已废弃） | 并发回收 | JDK 8-14 |
| G1 | 大堆内存、平衡型 | 分区回收 | JDK 9+默认 |
| ZGC | 超大堆、超低延迟 | 并发整理，JDK21默认分代 | JDK 11+ |
| Shenandoah | 低延迟 | 并发整理 | OpenJDK |

### 2.2 推荐配置

**G1 GC（通用推荐）：**
```bash
-XX:+UseG1GC
-Xms4g -Xmx4g
-XX:MaxGCPauseMillis=200
-XX:+ParallelRefProcEnabled
-XX:+AlwaysPreTouch
-XX:+DisableExplicitGC
-Xlog:gc*:file=logs/gc.log:time,uptime:filecount=5,filesize=100m
```

**ZGC（超低延迟）- JDK 21+推荐：**
```bash
-XX:+UseZGC
-Xms16g -Xmx16g
# JDK 21+ 分代ZGC默认启用，无需额外配置
# -XX:+ZGenerational  # JDK 21之前需要显式开启
-XX:+DisableExplicitGC
-Xlog:gc*:file=logs/gc.log:time,uptime:filecount=5,filesize=100m:async
```

---

## 3. 内存参数配置

### 3.1 生产环境配置模板

```bash
#!/bin/bash

# 基础配置
JAVA_OPTS="-server"
JAVA_OPTS="$JAVA_OPTS -Xms8g -Xmx8g"                    # 堆内存
JAVA_OPTS="$JAVA_OPTS -XX:MetaspaceSize=256m"           # 元空间初始
JAVA_OPTS="$JAVA_OPTS -XX:MaxMetaspaceSize=512m"        # 元空间最大

# GC配置（G1）
JAVA_OPTS="$JAVA_OPTS -XX:+UseG1GC"
JAVA_OPTS="$JAVA_OPTS -XX:MaxGCPauseMillis=200"
JAVA_OPTS="$JAVA_OPTS -XX:+ParallelRefProcEnabled"
JAVA_OPTS="$JAVA_OPTS -XX:+AlwaysPreTouch"

# GC日志配置（JDK 21+）
JAVA_OPTS="$JAVA_OPTS -Xlog:gc*:file=logs/gc.log:time,uptime:filecount=10,filesize=100m:async"

# OOM处理
JAVA_OPTS="$JAVA_OPTS -XX:+HeapDumpOnOutOfMemoryError"
JAVA_OPTS="$JAVA_OPTS -XX:HeapDumpPath=/var/log/app/heapdump.hprof"
JAVA_OPTS="$JAVA_OPTS -XX:OnOutOfMemoryError='sh /scripts/alert.sh'"
JAVA_OPTS="$JAVA_OPTS -XX:+ExitOnOutOfMemoryError"

# JIT优化（JDK 21+）
JAVA_OPTS="$JAVA_OPTS -XX:+UseStringDeduplication"
JAVA_OPTS="$JAVA_OPTS -XX:+OptimizeStringConcat"

# 性能优化
JAVA_OPTS="$JAVA_OPTS -XX:+UseLargePages"
JAVA_OPTS="$JAVA_OPTS -XX:+UseNUMA"

# JDK 21+ 虚拟线程相关（如果使用）
# JAVA_OPTS="$JAVA_OPTS -Djdk.virtualThreadScheduler.parallelism=8"
# JAVA_OPTS="$JAVA_OPTS -Djdk.virtualThreadScheduler.maxPoolSize=256"

java $JAVA_OPTS -jar application.jar
```

### 3.2 各代内存比例

```bash
# 新生代:老年代 = 1:2
-XX:NewRatio=2

# Eden:Survivor = 8:1:1
-XX:SurvivorRatio=8

# 直接进入老年代的对象大小
-XX:PretenureSizeThreshold=1m

# 晋升老年代的年龄阈值
-XX:MaxTenuringThreshold=15
```

---

## 4. GC日志分析

### 4.1 GC日志解读

**JDK 9+ 统一日志格式：**
```
[2024-01-15T10:23:45.123+0800][gc,start] GC(123) Pause Young (Normal) (G1 Evacuation Pause)
[2024-01-15T10:23:45.124+0800][gc,task] GC(123) Using 4 workers of 4 for evacuation
[2024-01-15T10:23:45.125+0800][gc,phases] GC(123) Pre Evacuate Collection Set: 0.1ms
[2024-01-15T10:23:45.126+0800][gc,phases] GC(123) Evacuate Collection Set: 0.8ms
[2024-01-15T10:23:45.127+0800][gc,phases] GC(123) Post Evacuate Collection Set: 0.2ms
[2024-01-15T10:23:45.128+0800][gc,heap] GC(123) Eden regions: 24->0(24)
[2024-01-15T10:23:45.129+0800][gc,heap] GC(123) Survivor regions: 3->4(4)
[2024-01-15T10:23:45.130+0800][gc,heap] GC(123) Old regions: 45->45
[2024-01-15T10:23:45.131+0800][gc,heap] GC(123) Humongous regions: 0->0
[2024-01-15T10:23:45.132+0800][gc,metaspace] GC(123) Metaspace: 12345K->12345K(106496K)
[2024-01-15T10:23:45.133+0800][gc] GC(123) Pause Young (Normal) (G1 Evacuation Pause) 150M->50M(512M) 1.123ms
```

### 4.2 关键指标

| 指标 | 健康阈值 | 说明 |
|------|----------|------|
| GC频率 | Young GC < 1次/秒 | 过高说明新生代太小 |
| GC耗时 | Young GC < 50ms | 用户可感知延迟 |
| GC耗时 | Full GC < 1s | 影响较大 |
| 吞吐量 | GC时间占比 < 5% | 业务可用时间 |
| 堆使用率 | 峰值 < 80% | 预留缓冲空间 |

### 4.3 GC分析工具

1. **GCEasy** (在线工具): https://gceasy.io/
2. **GCViewer** (桌面工具): https://github.com/chewiebug/GCViewer
3. **jconsole**: JDK自带可视化工具
4. **VisualVM**: 功能强大的监控工具

---

## 5. 内存泄漏排查

### 5.1 常见内存泄漏场景

1. **静态集合持有对象**
```java
// ❌ 静态集合导致内存泄漏
private static final List<Object> STATIC_CACHE = new ArrayList<>();
public void addToCache(Object obj) {
    STATIC_CACHE.add(obj);  // 永远不会释放
}

// ✅ 使用WeakHashMap或设置过期策略
private final Cache<String, Object> cache = Caffeine.newBuilder()
    .maximumSize(1000)
    .expireAfterWrite(1, TimeUnit.HOURS)
    .build();
```

2. **未移除的监听器**
```java
// ❌ 监听器未移除
public class LeakyComponent {
    public void init() {
        eventBus.register(this);  // 注册后未注销
    }
}

// ✅ 确保在销毁时移除监听器
public void destroy() {
    eventBus.unregister(this);
}
```

3. **ThreadLocal未清理**
```java
// ❌ ThreadLocal未清理
private static final ThreadLocal<Object> context = new ThreadLocal<>();
public void process() {
    context.set(new Object());  // 使用完后未清理
}

// ✅ 使用try-finally确保清理
public void process() {
    context.set(new Object());
    try {
        // 业务逻辑
    } finally {
        context.remove();  // ✅ 必须清理
    }
}
```

### 5.2 内存分析步骤

1. **生成堆转储文件**
```bash
# 自动在OOM时生成
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/path/to/dump.hprof

# 手动生成
jmap -dump:format=b,file=heap.hprof <pid>
jcmd <pid> GC.heap_dump heap.hprof
```

2. **分析堆转储**
```bash
# 使用Eclipse MAT
# 1. 打开.hprof文件
# 2. 运行Leak Suspects Report
# 3. 查看Dominator Tree

# 使用jhat（JDK自带）
jhat heap.hprof
# 访问 http://localhost:7000
```

---

## 6. 性能监控

### 6.1 命令行工具

```bash
# 查看JVM进程
jps -lvm

# 查看GC统计
jstat -gc <pid> 1000  # 每秒输出一次

# 查看堆内存详情
jmap -heap <pid>

# 查看堆中对象统计
jmap -histo <pid> | head -20

# 查看线程栈
jstack <pid>

# 综合诊断
jcmd <pid> VM.native_memory summary
```

### 6.2 编程方式监控

```java
// 获取GC信息
List<GarbageCollectorMXBean> gcBeans = 
    ManagementFactory.getGarbageCollectorMXBeans();
for (GarbageCollectorMXBean gcBean : gcBeans) {
    System.out.println("GC名称: " + gcBean.getName());
    System.out.println("收集次数: " + gcBean.getCollectionCount());
    System.out.println("收集时间: " + gcBean.getCollectionTime() + " ms");
}

// 获取内存信息
MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
System.out.println("堆内存使用: " + heapUsage.getUsed() / 1024 / 1024 + " MB");
```

### 6.3 监控指标采集

```java
@Component
public class JvmMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    @Scheduled(fixedRate = 60000)
    public void collectMetrics() {
        // 堆内存使用率
        MemoryUsage heapUsage = ManagementFactory.getMemoryMXBean()
            .getHeapMemoryUsage();
        double heapUsedPercent = 100.0 * heapUsage.getUsed() / heapUsage.getMax();
        meterRegistry.gauge("jvm.memory.heap.used.percent", heapUsedPercent);
        
        // GC次数和耗时
        ManagementFactory.getGarbageCollectorMXBeans().forEach(gc -> {
            meterRegistry.counter("jvm.gc.count", "gc", gc.getName())
                .increment(gc.getCollectionCount());
            meterRegistry.timer("jvm.gc.time", "gc", gc.getName())
                .record(gc.getCollectionTime(), TimeUnit.MILLISECONDS);
        });
    }
}
```

---

## 7. JVM调优流程

```
1. 确定目标
   ├─ 低延迟（GC停顿短）
   ├─ 高吞吐（业务处理快）
   └─ 大内存（堆容量大）

2. 选择GC
   ├─ 低延迟 → ZGC / Shenandoah
   ├─ 高吞吐 → Parallel / G1
   └─ 平衡 → G1

3. 配置内存
   ├─ 堆大小（Xms=Xmx）
   ├─ 新生代大小
   └─ 元空间大小

4. 监控验证
   ├─ GC日志分析
   ├─ 内存使用监控
   └─ 业务性能测试

5. 调优迭代
   ├─ 根据监控调整参数
   └─ 验证效果
```

---

## 8. 相关代码示例

- [MemoryOptimization.java](../src/main/java/com/perf/sop/jvm/memory/MemoryOptimization.java)
- [GCOptimization.java](../src/main/java/com/perf/sop/jvm/gc/GCOptimization.java)
