# 并发性能优化指南

## 目录
1. [线程池优化](#1-线程池优化)
2. [锁优化](#2-锁优化)
3. [并发容器](#3-并发容器)
4. [无锁编程](#4-无锁编程)
5. [异步处理](#5-异步处理)

---

## 1. 线程池优化

### 1.1 线程池参数计算

**CPU密集型任务：**
```
核心线程数 = CPU核心数 + 1
```
原因：充分利用CPU，+1防止线程偶发缺页中断

**IO密集型任务：**
```
核心线程数 = CPU核心数 * 2
或
核心线程数 = CPU核心数 / (1 - 阻塞系数)
阻塞系数：0.8 ~ 0.9

示例：4核CPU，阻塞系数0.9
核心线程数 = 4 / (1 - 0.9) = 40
```

### 1.2 线程池配置示例

```java
public class ThreadPoolConfig {
    
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();
    
    /**
     * CPU密集型线程池
     */
    public ThreadPoolExecutor cpuIntensivePool() {
        return new ThreadPoolExecutor(
            CPU_COUNT + 1,                      // 核心线程数
            CPU_COUNT + 1,                      // 最大线程数
            0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(1000),    // 有界队列
            new NamedThreadFactory("cpu-pool"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    /**
     * IO密集型线程池
     */
    public ThreadPoolExecutor ioIntensivePool() {
        return new ThreadPoolExecutor(
            CPU_COUNT * 4,                      // 核心线程数
            CPU_COUNT * 8,                      // 最大线程数
            60L, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(5000),     // 有界队列
            new NamedThreadFactory("io-pool"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

### 1.3 线程池监控

```java
@Component
public class ThreadPoolMonitor {
    
    @Scheduled(fixedRate = 60000)
    public void monitor() {
        ThreadPoolExecutor executor = (ThreadPoolExecutor) asyncTaskExecutor;
        
        log.info("线程池状态 - 活跃: {}, 核心: {}, 最大: {}, 队列: {}/{}",
            executor.getActiveCount(),
            executor.getCorePoolSize(),
            executor.getMaximumPoolSize(),
            executor.getQueue().size(),
            executor.getQueue().remainingCapacity() + executor.getQueue().size()
        );
        
        // 告警检查
        if (executor.getQueue().size() > 1000) {
            alert("线程池队列堆积");
        }
    }
}
```

---

## 2. 锁优化

### 2.1 锁的选择

| 锁类型 | 适用场景 | 特点 |
|--------|----------|------|
| synchronized | 简单同步 | JVM优化好，自动释放 |
| ReentrantLock | 复杂同步 | 可中断、可超时、公平锁 |
| ReadWriteLock | 读多写少 | 读读并行，读写互斥 |
| StampedLock | 读多写少 | 乐观读，性能更好 |

### 2.2 synchronized优化

```java
public class SynchronizedOptimization {
    
    private final Object lock = new Object();
    private volatile int counter;
    
    // ❌ 锁整个方法
    public synchronized void increment() {
        counter++;
    }
    
    // ✅ 缩小锁粒度
    public void incrementOptimized() {
        synchronized (lock) {
            counter++;
        }
    }
    
    // ✅ 锁分离（细粒度锁）
    private final Object[] locks = new Object[16];
    private final AtomicInteger[] counters = new AtomicInteger[16];
    
    public void incrementWithSegmentLock(int key) {
        int index = key % 16;
        synchronized (locks[index]) {
            counters[index].incrementAndGet();
        }
    }
}
```

### 2.3 读写锁

```java
public class ReadWriteLockExample {
    
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final Lock readLock = rwLock.readLock();
    private final Lock writeLock = rwLock.writeLock();
    
    private Map<String, Object> cache = new HashMap<>();
    
    // 读操作
    public Object get(String key) {
        readLock.lock();
        try {
            return cache.get(key);
        } finally {
            readLock.unlock();
        }
    }
    
    // 写操作
    public void put(String key, Object value) {
        writeLock.lock();
        try {
            cache.put(key, value);
        } finally {
            writeLock.unlock();
        }
    }
}
```

### 2.4 StampedLock（Java 8+）

```java
public class StampedLockExample {
    
    private final StampedLock lock = new StampedLock();
    private double x, y;
    
    // 乐观读
    public double distanceFromOrigin() {
        long stamp = lock.tryOptimisticRead();
        double currentX = x, currentY = y;
        
        // 验证读期间是否有写操作
        if (!lock.validate(stamp)) {
            // 升级为悲观读锁
            stamp = lock.readLock();
            try {
                currentX = x;
                currentY = y;
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        return Math.sqrt(currentX * currentX + currentY * currentY);
    }
    
    // 写操作
    public void move(double deltaX, double deltaY) {
        long stamp = lock.writeLock();
        try {
            x += deltaX;
            y += deltaY;
        } finally {
            lock.unlockWrite(stamp);
        }
    }
}
```

---

## 3. 并发容器

### 3.1 并发容器选择

| 容器 | 特点 | 适用场景 |
|------|------|----------|
| ConcurrentHashMap | 分段锁/CAS | 高并发读写 |
| CopyOnWriteArrayList | 写时复制 | 读多写少 |
| ConcurrentLinkedQueue | 无锁队列 | 高并发队列 |
| BlockingQueue | 阻塞操作 | 生产者消费者 |
| ConcurrentSkipListMap | 有序 | 需要排序的高并发Map |

### 3.2 ConcurrentHashMap优化

```java
public class ConcurrentHashMapOptimization {
    
    private final ConcurrentHashMap<String, Object> map = new ConcurrentHashMap<>();
    
    // ✅ 使用computeIfAbsent（原子性）
    public Object getWithInit(String key, Supplier<Object> supplier) {
        return map.computeIfAbsent(key, k -> supplier.get());
    }
    
    // ✅ 使用merge（原子性累加）
    public void increment(String key) {
        map.merge(key, 1, Integer::sum);
    }
    
    // ✅ 批量操作
    public void batchPut(Map<String, Object> data) {
        map.putAll(data);
    }
    
    // ❌ 避免先判断再操作（非原子性）
    public void badIncrement(String key) {
        // 线程不安全！
        if (!map.containsKey(key)) {
            map.put(key, 0);
        }
        map.put(key, (Integer) map.get(key) + 1);
    }
}
```

### 3.3 缓存实现

```java
public class ConcurrentCache<K, V> {
    
    private final ConcurrentHashMap<K, V> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<K, Future<V>> loading = new ConcurrentHashMap<>();
    
    public V get(K key, Callable<V> loader) throws Exception {
        V value = cache.get(key);
        if (value != null) {
            return value;
        }
        
        // 防止缓存穿透
        Future<V> future = loading.computeIfAbsent(key, k -> 
            CompletableFuture.supplyAsync(() -> {
                try {
                    return loader.call();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            })
        );
        
        try {
            value = future.get();
            cache.put(key, value);
            loading.remove(key);
            return value;
        } catch (Exception e) {
            loading.remove(key);
            throw e;
        }
    }
}
```

---

## 4. 无锁编程

### 4.1 CAS操作

```java
public class CASExample {
    
    private final AtomicInteger counter = new AtomicInteger(0);
    private final AtomicReference<String> reference = new AtomicReference<>();
    
    // ✅ 原子自增
    public int increment() {
        return counter.incrementAndGet();
    }
    
    // ✅ CAS更新
    public boolean compareAndSet(String expected, String update) {
        return reference.compareAndSet(expected, update);
    }
    
    // ✅ 自旋CAS
    public void spinCAS(AtomicInteger value, int newValue) {
        while (true) {
            int current = value.get();
            if (value.compareAndSet(current, newValue)) {
                break;
            }
            // 可选：Thread.yield() 或 LockSupport.parkNanos()
        }
    }
}
```

### 4.2 LongAdder（高并发计数器）

```java
public class HighConcurrencyCounter {
    
    // ❌ 高并发下AtomicInteger竞争激烈
    private final AtomicInteger atomicCounter = new AtomicInteger();
    
    // ✅ LongAdder分散热点，性能更好
    private final LongAdder longAdder = new LongAdder();
    
    public void increment() {
        longAdder.increment();
    }
    
    public long sum() {
        return longAdder.sum();
    }
}
```

### 4.3 Unsafe与VarHandle（高级）

```java
public class VarHandleExample {
    
    private volatile int value;
    
    // VarHandle（Java 9+ 替代Unsafe）
    private static final VarHandle VALUE_HANDLE;
    
    static {
        try {
            VALUE_HANDLE = MethodHandles.lookup()
                .findVarHandle(VarHandleExample.class, "value", int.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
    
    public boolean compareAndSet(int expected, int update) {
        return VALUE_HANDLE.compareAndSet(this, expected, update);
    }
    
    public int getVolatile() {
        return (int) VALUE_HANDLE.getVolatile(this);
    }
}
```

---

## 5. 异步处理

### 5.1 CompletableFuture

```java
@Service
public class AsyncService {
    
    @Autowired
    private ThreadPoolExecutor executor;
    
    /**
     * 并行查询多个服务
     */
    public UserDetails getUserDetails(Long userId) {
        CompletableFuture<User> userFuture = CompletableFuture
            .supplyAsync(() -> userService.getUser(userId), executor);
        
        CompletableFuture<List<Order>> ordersFuture = CompletableFuture
            .supplyAsync(() -> orderService.getOrders(userId), executor);
        
        CompletableFuture<List<Coupon>> couponsFuture = CompletableFuture
            .supplyAsync(() -> couponService.getCoupons(userId), executor);
        
        // 等待所有结果
        CompletableFuture.allOf(userFuture, ordersFuture, couponsFuture).join();
        
        return new UserDetails(
            userFuture.join(),
            ordersFuture.join(),
            couponsFuture.join()
        );
    }
    
    /**
     * 链式异步处理
     */
    public CompletableFuture<Order> createOrder(CreateOrderRequest request) {
        return CompletableFuture
            .supplyAsync(() -> validateRequest(request), executor)
            .thenApplyAsync(this::calculatePrice, executor)
            .thenComposeAsync(price -> deductStock(request, price), executor)
            .thenApplyAsync(this::saveOrder, executor)
            .exceptionally(ex -> {
                log.error("创建订单失败", ex);
                throw new OrderException("创建订单失败");
            });
    }
}
```

### 5.2 响应式编程（Reactor）

```java
@Service
public class ReactiveService {
    
    @Autowired
    private ReactiveUserRepository userRepository;
    
    /**
     * 响应式数据流处理
     */
    public Mono<User> getUserWithOrders(Long userId) {
        return userRepository.findById(userId)
            .flatMap(user -> 
                orderRepository.findByUserId(userId)
                    .collectList()
                    .map(orders -> {
                        user.setOrders(orders);
                        return user;
                    })
            )
            .timeout(Duration.ofSeconds(3))
            .retryWhen(Retry.backoff(3, Duration.ofMillis(100)))
            .onErrorResume(e -> {
                log.error("查询用户失败", e);
                return Mono.empty();
            });
    }
    
    /**
     * 背压处理
     */
    public Flux<Order> getLargeOrderStream() {
        return orderRepository.findAll()
            .onBackpressureBuffer(1000)  // 缓冲1000条
            .delayElements(Duration.ofMillis(10));  // 控制速率
    }
}
```

---

## 6. 虚拟线程（JDK 21+）

### 6.1 虚拟线程概述

虚拟线程是JDK 21的核心特性，它改变了Java并发编程的方式：

| 特性 | 平台线程 | 虚拟线程 |
|------|----------|----------|
| 内存占用 | ~1MB栈空间 | ~几百字节 |
| 创建成本 | 高（需要OS线程） | 低（JVM管理） |
| 数量 | 数千个 | 数百万个 |
| 适用场景 | CPU密集型 | IO密集型 |
| 阻塞成本 | 高（占用OS线程） | 低（自动让出） |

### 6.2 虚拟线程使用

```java
// ✅ 创建虚拟线程（方式1：直接创建）
Thread.startVirtualThread(() -> {
    System.out.println("运行虚拟线程: " + Thread.currentThread());
});

// ✅ 创建虚拟线程（方式2：使用Builder）
Thread vt = Thread.ofVirtual()
    .name("virtual-thread-", 0)
    .unstarted(() -> System.out.println("Hello"));
vt.start();

// ✅ 虚拟线程工厂
ThreadFactory factory = Thread.ofVirtual().factory();

// ✅ 使用ExecutorService（推荐）
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    IntStream.range(0, 10_000).forEach(i -> {
        executor.submit(() -> {
            Thread.sleep(Duration.ofSeconds(1));
            return i;
        });
    });
}  // 自动关闭，等待所有任务完成
```

### 6.3 虚拟线程最佳实践

```java
@Service
public class VirtualThreadService {
    
    /**
     * ✅ 使用虚拟线程处理IO密集型任务
     * 
     * 优势：可以轻松创建数万个并发任务
     */
    public List<String> fetchFromMultipleSources(List<String> urls) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var futures = urls.stream()
                .map(url -> executor.submit(() -> fetchUrl(url)))
                .toList();
            
            return futures.stream()
                .map(Future::get)
                .toList();
        }
    }
    
    /**
     * ❌ 不要在虚拟线程中使用同步阻塞（synchronized）
     * 
     * 虚拟线程遇到synchronized会pin住载体线程
     * 降低虚拟线程的效率
     */
    public void badPractice() {
        synchronized (this) {  // ❌ 避免在虚拟线程中使用
            // do something
        }
    }
    
    /**
     * ✅ 使用ReentrantLock替代synchronized
     */
    private final ReentrantLock lock = new ReentrantLock();
    
    public void goodPractice() {
        lock.lock();  // ✅ ReentrantLock不会pin载体线程
        try {
            // do something
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * ✅ 使用ThreadLocal的替代方案
     * 
     * 虚拟线程数量巨大，ThreadLocal可能占用大量内存
     * JDK 21引入ScopedValue作为替代
     */
    public void useScopedValue() {
        // ScopedValue是ThreadLocal的轻量级替代
        // 适用于虚拟线程场景
    }
}
```

### 6.4 虚拟线程监控

```java
@Component
public class VirtualThreadMetrics {
    
    /**
     * 监控虚拟线程调度器
     */
    public void printVirtualThreadInfo() {
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        
        // 获取所有线程信息
        ThreadInfo[] threadInfos = threadBean.dumpAllThreads(false, false);
        
        long virtualThreadCount = Arrays.stream(threadInfos)
            .filter(info -> info.getThreadName().startsWith("virtual-"))
            .count();
        
        System.out.println("虚拟线程数量: " + virtualThreadCount);
    }
}
```

### 6.5 虚拟线程适用场景

| 适用场景 | 不适用场景 |
|----------|------------|
| HTTP/RPC客户端调用 | 纯CPU计算（无IO） |
| 数据库查询 | 需要严格线程绑定的操作 |
| 文件IO | 大量使用synchronized的代码 |
| 定时任务 | 需要线程优先级的场景 |
| 批处理任务 | 实时性要求极高的任务 |

---

## 7. 并发检查清单

### 6.1 线程安全检查
- [ ] 多线程访问的共享变量是否有同步保护？
- [ ] 使用的集合类是否是线程安全的？
- [ ] 单例对象是否是线程安全的？

### 6.2 性能检查
- [ ] 锁的粒度是否足够小？
- [ ] 是否存在锁竞争热点？
- [ ] 读多写少场景是否使用了读写锁？
- [ ] 高并发计数是否使用了LongAdder？

### 6.3 死锁检查
- [ ] 是否存在嵌套加锁？
- [ ] 加锁顺序是否一致？
- [ ] 是否使用了定时锁（tryLock）？

---

## 7. 相关代码示例

- [ThreadPoolOptimization.java](../src/main/java/com/perf/sop/concurrency/threadpool/ThreadPoolOptimization.java)
- [LockOptimization.java](../src/main/java/com/perf/sop/concurrency/lock/LockOptimization.java)
- [VirtualThreadOptimization.java](../src/main/java/com/perf/sop/concurrency/virtualthread/VirtualThreadOptimization.java) ⭐ JDK 21+
