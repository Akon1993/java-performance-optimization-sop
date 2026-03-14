/**
 * JDK 21 虚拟线程（Virtual Threads）优化最佳实践
 * 
 * 【SOP核心要点】
 * 1. 虚拟线程适用于IO密集型场景，不适用于纯CPU计算
 * 2. 使用Executors.newVirtualThreadPerTaskExecutor()简化并发编程
 * 3. 避免在虚拟线程中使用synchronized（会pin住载体线程）
 * 4. 考虑使用ScopedValue替代ThreadLocal
 * 5. 可以轻松创建数十万甚至数百万个虚拟线程
 * 
 * 【虚拟线程 vs 平台线程】
 * 
 * | 特性 | 平台线程 | 虚拟线程 |
 * |------|----------|----------|
 * | 内存占用 | ~1MB | ~几百字节 |
 * | 创建成本 | 高（OS线程） | 低（JVM管理） |
 * | 阻塞成本 | 高 | 低（自动让出） |
 * | 适用场景 | CPU密集型 | IO密集型 |
 * | 数量级 | 数千 | 数百万 |
 * 
 * 【使用场景】
 * - HTTP/RPC客户端调用
 * - 数据库查询
 * - 文件IO操作
 * - 批量任务并发处理
 * 
 * @author Performance Optimization Team
 * @version 2.0.0
 * @since 2026-03-14
 */
package com.perf.sop.concurrency.virtualthread;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class VirtualThreadOptimization {

    /**
     * ==================== 虚拟线程创建方式 ====================
     */

    /**
     * ✅ 方式1：直接启动虚拟线程
     */
    public void createVirtualThreadDirectly() {
        Thread.startVirtualThread(() -> {
            System.out.println("运行虚拟线程: " + Thread.currentThread());
            // 执行IO操作
            simulateIO(100);
        });
    }

    /**
     * ✅ 方式2：使用Thread.Builder
     */
    public Thread createVirtualThreadWithBuilder() throws InterruptedException {
        Thread thread = Thread.ofVirtual()
            .name("virtual-worker-")
            .inheritInheritableThreadLocals(false)  // 不继承父线程的ThreadLocal
            .unstarted(() -> {
                System.out.println("虚拟线程执行: " + Thread.currentThread().getName());
            });
        
        thread.start();
        return thread;
    }

    /**
     * ✅ 方式3：使用虚拟线程工厂
     */
    public ThreadFactory createVirtualThreadFactory() {
        return Thread.ofVirtual()
            .name("virtual-pool-", 0)  // 命名前缀和起始编号
            .inheritInheritableThreadLocals(false)
            .factory();
    }

    /**
     * ==================== 虚拟线程执行器 ====================
     */

    /**
     * ✅ 使用newVirtualThreadPerTaskExecutor（推荐）
     * 
     * 为每个任务创建一个新的虚拟线程
     * 适用于大量短期IO任务
     */
    public List<String> executeWithVirtualThreads(List<String> urls) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 提交大量IO任务
            var futures = urls.stream()
                .map(url -> executor.submit(() -> fetchUrl(url)))
                .toList();
            
            // 收集结果
            return futures.stream()
                .map(future -> {
                    try {
                        return future.get();
                    } catch (Exception e) {
                        return "Error: " + e.getMessage();
                    }
                })
                .toList();
        }
    }

    /**
     * ✅ 批量并发处理（虚拟线程的优势场景）
     */
    public void batchProcessWithVirtualThreads(int taskCount) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            AtomicInteger completed = new AtomicInteger(0);
            
            IntStream.range(0, taskCount).forEach(i -> {
                executor.submit(() -> {
                    // 模拟IO操作
                    simulateIO(10);
                    completed.incrementAndGet();
                    return i;
                });
            });
            
            System.out.println("提交了 " + taskCount + " 个任务");
        }
    }

    /**
     * ==================== 性能对比 ====================
     */

    /**
     * 虚拟线程 vs 平台线程性能对比
     */
    public void performanceComparison() {
        int taskCount = 100_000;  // 10万个任务
        int ioMillis = 1;         // 每个任务1ms IO
        
        System.out.println("性能对比测试：" + taskCount + " 个IO任务");
        System.out.println("=====================================");
        
        // 测试1：平台线程池（固定大小）
        long platformTime = measurePlatformThreads(taskCount, ioMillis);
        System.out.println("平台线程池（固定200线程）: " + platformTime + "ms");
        
        // 测试2：虚拟线程
        long virtualTime = measureVirtualThreads(taskCount, ioMillis);
        System.out.println("虚拟线程: " + virtualTime + "ms");
        
        // 测试3：缓存线程池（对比，不推荐用于大量任务）
        // long cachedTime = measureCachedThreads(taskCount, ioMillis);
        // System.out.println("缓存线程池: " + cachedTime + "ms");
    }

    private long measurePlatformThreads(int taskCount, int ioMillis) {
        long start = System.currentTimeMillis();
        
        try (var executor = Executors.newFixedThreadPool(200)) {
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    simulateIO(ioMillis);
                    latch.countDown();
                });
            }
            
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return System.currentTimeMillis() - start;
    }

    private long measureVirtualThreads(int taskCount, int ioMillis) {
        long start = System.currentTimeMillis();
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(taskCount);
            
            for (int i = 0; i < taskCount; i++) {
                executor.submit(() -> {
                    simulateIO(ioMillis);
                    latch.countDown();
                });
            }
            
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        return System.currentTimeMillis() - start;
    }

    /**
     * ==================== 最佳实践与注意事项 ====================
     */

    /**
     * ✅ 最佳实践1：使用结构化并发
     * 
     * StructuredTaskScope 是JDK 21的预览特性（需要--enable-preview）
     * 提供更安全的并发编程模型
     */
    public void structuredConcurrencyExample() {
        /*
        // 需要启用预览特性
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            Future<String> user = scope.fork(() -> fetchUser());
            Future<String> order = scope.fork(() -> fetchOrder());
            
            scope.join();           // 等待所有任务完成
            scope.throwIfFailed();  // 任一失败则抛出异常
            
            return user.resultNow() + ", " + order.resultNow();
        }
        */
        
        // 不使用预览特性的替代方案
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> userFuture = executor.submit(this::fetchUser);
            Future<String> orderFuture = executor.submit(this::fetchOrder);
            
            String user = userFuture.get();
            String order = orderFuture.get();
            
            System.out.println("User: " + user + ", Order: " + order);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * ⚠️ 注意事项1：避免在虚拟线程中使用synchronized
     * 
     * synchronized会pin住载体线程（carrier thread），
     * 降低虚拟线程的效率
     */
    private final Object syncLock = new Object();
    private final java.util.concurrent.locks.ReentrantLock reentrantLock = 
        new java.util.concurrent.locks.ReentrantLock();

    public void avoidSynchronizedInVirtualThread() {
        Thread.startVirtualThread(() -> {
            // ❌ 不推荐：会pin住载体线程
            synchronized (syncLock) {
                // do something
            }
            
            // ✅ 推荐：使用ReentrantLock
            reentrantLock.lock();
            try {
                // do something
            } finally {
                reentrantLock.unlock();
            }
        });
    }

    /**
     * ⚠️ 注意事项2：ThreadLocal的使用
     * 
     * 虚拟线程数量巨大，ThreadLocal可能占用大量内存
     * JDK 21引入ScopedValue作为替代，但目前仍是预览特性
     */
    private static final ThreadLocal<String> threadLocal = new ThreadLocal<>();
    
    public void threadLocalCaution() {
        Thread.startVirtualThread(() -> {
            // ⚠️ 在虚拟线程中使用ThreadLocal要谨慎
            // 如果创建数百万虚拟线程，每个都有ThreadLocal，会占用大量内存
            threadLocal.set("value");
            
            try {
                // do something
            } finally {
                threadLocal.remove();  // 使用完后必须清理
            }
        });
    }

    /**
     * ✅ 最佳实践2：超时控制
     */
    public String fetchWithTimeout(String url, Duration timeout) {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> fetchUrl(url));
            
            try {
                return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (TimeoutException e) {
                future.cancel(true);
                return "Timeout";
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * ✅ 最佳实践3：优雅关闭
     */
    public void gracefulShutdown() {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // 提交任务...
            executor.submit(() -> simulateIO(100));
            
            // try-with-resources会自动关闭executor
            // 等待所有任务完成
        }
    }

    /**
     * ==================== 适用场景分析 ====================
     */

    /**
     * ✅ 适用场景：Web服务器请求处理
     */
    public void webServerScenario() {
        // 在Tomcat/Jetty等服务器中配置使用虚拟线程
        // server.tomcat.threads.virtual=true (Spring Boot 3.2+)
        
        // 每个请求一个虚拟线程，可以轻松处理数万并发连接
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < 10_000; i++) {
                final int requestId = i;
                executor.submit(() -> handleHttpRequest(requestId));
            }
        }
    }

    /**
     * ✅ 适用场景：批量数据处理
     */
    public void batchProcessingScenario() {
        List<String> records = IntStream.range(0, 100_000)
            .mapToObj(i -> "record-" + i)
            .toList();
        
        // 并发处理大量记录
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            records.forEach(record -> 
                executor.submit(() -> processRecord(record))
            );
        }
    }

    /**
     * ❌ 不适用场景：纯CPU计算
     */
    public void cpuIntensiveNotRecommended() {
        // 纯CPU计算不会从虚拟线程获益
        // 因为虚拟线程不增加CPU核心数
        
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < Runtime.getRuntime().availableProcessors(); i++) {
                executor.submit(() -> {
                    // CPU密集型计算
                    heavyComputation();
                });
            }
        }
        
        // 这种情况下，使用ForkJoinPool或普通线程池更合适
    }

    /**
     * ==================== 监控与诊断 ====================
     */

    /**
     * 打印虚拟线程信息
     */
    public void printVirtualThreadInfo() {
        System.out.println("当前线程: " + Thread.currentThread());
        System.out.println("是否虚拟线程: " + Thread.currentThread().isVirtual());
        
        // 获取线程信息
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
        long[] threadIds = threadBean.getAllThreadIds();
        ThreadInfo[] threadInfos = threadBean.getThreadInfo(threadIds);
        
        long virtualThreadCount = java.util.Arrays.stream(threadInfos)
            .filter(info -> info != null && isVirtualThread(info))
            .count();
        
        System.out.println("虚拟线程数量: " + virtualThreadCount);
    }

    private boolean isVirtualThread(ThreadInfo info) {
        // 虚拟线程的名称通常以特定前缀开头或为空
        // 实际检测方法可能因JVM实现而异
        String name = info.getThreadName();
        return name == null || name.isEmpty() || name.contains("virtual");
    }

    /**
     * ==================== 辅助方法 ====================
     */

    private String fetchUrl(String url) {
        simulateIO(100);  // 模拟100ms网络延迟
        return "Response of " + url;
    }

    private String fetchUser() {
        simulateIO(50);
        return "User Data";
    }

    private String fetchOrder() {
        simulateIO(50);
        return "Order Data";
    }

    private void handleHttpRequest(int requestId) {
        simulateIO(10);
        System.out.println("处理请求: " + requestId);
    }

    private void processRecord(String record) {
        simulateIO(1);
    }

    private void heavyComputation() {
        // 纯CPU计算
        long sum = 0;
        for (int i = 0; i < 10_000_000; i++) {
            sum += i;
        }
    }

    private void simulateIO(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 主方法：演示虚拟线程
     */
    public static void main(String[] args) throws Exception {
        VirtualThreadOptimization demo = new VirtualThreadOptimization();
        
        System.out.println("========== JDK 21 虚拟线程优化演示 ==========\n");
        
        // 1. 检查JDK版本
        System.out.println("Java版本: " + System.getProperty("java.version"));
        System.out.println("是否支持虚拟线程: " + (Runtime.version().feature() >= 21));
        System.out.println();
        
        // 2. 创建虚拟线程
        System.out.println("1. 创建虚拟线程");
        demo.createVirtualThreadDirectly();
        Thread.sleep(100);
        
        // 3. 批量任务处理
        System.out.println("\n2. 批量任务处理（1000个任务）");
        long start = System.currentTimeMillis();
        demo.batchProcessWithVirtualThreads(1000);
        System.out.println("耗时: " + (System.currentTimeMillis() - start) + "ms");
        
        // 4. 性能对比
        System.out.println("\n3. 性能对比（少量任务）");
        demo.performanceComparison();
        
        System.out.println("\n========== 演示完成 ==========");
    }
}
