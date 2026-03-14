/**
 * 线程池优化配置
 * 
 * 【SOP核心要点】
 * 1. 根据业务场景选择线程池类型
 * 2. 合理设置核心线程数、最大线程数
 * 3. 配置合适的队列和拒绝策略
 * 4. 监控线程池运行状态
 * 5. 优雅关闭线程池
 * 
 * 【线程池参数计算公式】
 * 
 * 1. CPU密集型任务：
 *    核心线程数 = CPU核心数 + 1
 *    原因：充分利用CPU，+1防止线程偶发缺页中断
 * 
 * 2. IO密集型任务：
 *    核心线程数 = CPU核心数 * 2
 *    或
 *    核心线程数 = CPU核心数 / (1 - 阻塞系数)
 *    阻塞系数：0.8 ~ 0.9
 *    
 *    例如：4核CPU，阻塞系数0.9
 *    核心线程数 = 4 / (1 - 0.9) = 40
 * 
 * 3. 混合型任务：
 *    拆分线程池，CPU密集型一个，IO密集型一个
 * 
 * 【线程池类型选择】
 * 
 * ┌────────────────────┬────────────────────────────────────────────┐
 * │ 线程池类型          │ 适用场景                                    │
 * ├────────────────────┼────────────────────────────────────────────┤
 * │ FixedThreadPool    │ 负载稳定的长期任务                          │
 * │ CachedThreadPool   │ 大量短期异步任务                            │
 * │ SingleThreadPool   │ 顺序执行任务                                │
 * │ ScheduledPool      │ 定时任务、周期性任务                        │
 * │ WorkStealingPool   │ 大任务拆分并行处理（Fork/Join）              │
 * │ 自定义ThreadPool   │ 生产环境推荐，可精细控制                     │
 * └────────────────────┴────────────────────────────────────────────┘
 * 
 * 【队列选择】
 * 
 * - ArrayBlockingQueue：有界队列，防止OOM
 * - LinkedBlockingQueue：无界队列（慎用），内存可能耗尽
 * - SynchronousQueue：直接提交，无缓冲
 * - PriorityBlockingQueue：优先级队列
 * 
 * 【拒绝策略】
 * 
 * - AbortPolicy：直接抛出异常（默认）
 * - CallerRunsPolicy：由调用线程执行任务
 * - DiscardPolicy：静默丢弃任务
 * - DiscardOldestPolicy：丢弃队列最老任务
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.concurrency.threadpool;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadPoolOptimization {

    /**
     * CPU核心数
     */
    private static final int CPU_COUNT = Runtime.getRuntime().availableProcessors();

    /**
     * ✅ 创建CPU密集型任务线程池
     * 
     * 适用场景：
     * - 复杂计算（如排序、加密、压缩）
     * - 数据处理（如大数据ETL）
     * - 算法执行
     */
    public static ThreadPoolExecutor createCpuIntensivePool() {
        int corePoolSize = CPU_COUNT + 1;      // CPU核心数 + 1
        int maximumPoolSize = CPU_COUNT + 1;    // 固定大小，不扩容
        long keepAliveTime = 0L;                 // 无空闲线程
        
        // 使用有界队列，防止OOM
        BlockingQueue<Runnable> workQueue = 
            new ArrayBlockingQueue<>(1000);
        
        // 自定义线程工厂，设置有意义的线程名
        ThreadFactory threadFactory = new NamedThreadFactory("cpu-intensive");
        
        // 拒绝策略：调用者运行（降级保护）
        RejectedExecutionHandler handler = 
            new ThreadPoolExecutor.CallerRunsPolicy();
        
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.MILLISECONDS,
            workQueue,
            threadFactory,
            handler
        );
        
        // 允许核心线程超时回收（如果长时间无任务）
        executor.allowCoreThreadTimeOut(false);
        
        return executor;
    }

    /**
     * ✅ 创建IO密集型任务线程池
     * 
     * 适用场景：
     * - HTTP/RPC调用
     * - 数据库操作
     * - 文件读写
     * - 网络通信
     */
    public static ThreadPoolExecutor createIoIntensivePool() {
        // IO密集型：阻塞系数0.8 ~ 0.9
        int corePoolSize = CPU_COUNT * 4;           // 更多的线程
        int maximumPoolSize = CPU_COUNT * 8;        // 最大线程数
        long keepAliveTime = 60L;                   // 空闲线程存活时间
        
        // 使用有界队列
        BlockingQueue<Runnable> workQueue = 
            new ArrayBlockingQueue<>(5000);
        
        ThreadFactory threadFactory = new NamedThreadFactory("io-intensive");
        
        // 拒绝策略：记录日志并抛出异常
        RejectedExecutionHandler handler = new LoggingAbortPolicy();
        
        return new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            workQueue,
            threadFactory,
            handler
        );
    }

    /**
     * ✅ 创建混合型任务线程池（Tomcat风格）
     * 
     * Tomcat默认配置：
     * - 核心线程数：10
     * - 最大线程数：200
     * - 队列：无界队列（LinkedBlockingQueue）
     * - 空闲超时：60秒
     * 
     * 改进：使用有界队列防止OOM
     */
    public static ThreadPoolExecutor createTomcatStylePool() {
        int corePoolSize = 10;
        int maximumPoolSize = 200;
        long keepAliveTime = 60L;
        
        // 使用有界队列替代无界队列
        BlockingQueue<Runnable> workQueue = 
            new LinkedBlockingQueue<>(1000);
        
        return new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            keepAliveTime,
            TimeUnit.SECONDS,
            workQueue,
            new NamedThreadFactory("tomcat-style"),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * ✅ 创建快速响应线程池（低延迟场景）
     * 
     * 适用场景：
     * - 实时计算
     * - 高频交易
     * - 实时推荐
     */
    public static ThreadPoolExecutor createFastResponsePool() {
        // 更多核心线程，减少任务等待
        int corePoolSize = CPU_COUNT * 2;
        int maximumPoolSize = CPU_COUNT * 4;
        
        // 使用SynchronousQueue，直接提交，无缓冲
        // 立即创建新线程处理任务，无队列等待
        BlockingQueue<Runnable> workQueue = 
            new SynchronousQueue<>();
        
        return new ThreadPoolExecutor(
            corePoolSize,
            maximumPoolSize,
            60L,
            TimeUnit.SECONDS,
            workQueue,
            new NamedThreadFactory("fast-response"),
            // 快速失败，调用者处理
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }

    /**
     * ✅ 创建定时任务线程池
     */
    public static ScheduledExecutorService createScheduledPool() {
        int corePoolSize = CPU_COUNT;
        
        return Executors.newScheduledThreadPool(
            corePoolSize,
            new NamedThreadFactory("scheduled")
        );
    }

    /**
     * ✅ 创建Fork/Join线程池（任务拆分）
     */
    public static ForkJoinPool createForkJoinPool() {
        int parallelism = CPU_COUNT;
        
        return new ForkJoinPool(
            parallelism,
            ForkJoinPool.defaultForkJoinWorkerThreadFactory,
            null,  // 未捕获异常处理器
            true   // 异步模式
        );
    }

    /**
     * ==================== 线程池监控 ====================
     */

    /**
     * 线程池监控指标
     */
    public static class ThreadPoolMetrics {
        private final String poolName;
        private final int poolSize;
        private final int activeCount;
        private final int corePoolSize;
        private final int maximumPoolSize;
        private final long completedTaskCount;
        private final long taskCount;
        private final int queueSize;
        private final int queueRemainingCapacity;
        private final long keepAliveTime;

        public ThreadPoolMetrics(ThreadPoolExecutor executor, String name) {
            this.poolName = name;
            this.poolSize = executor.getPoolSize();
            this.activeCount = executor.getActiveCount();
            this.corePoolSize = executor.getCorePoolSize();
            this.maximumPoolSize = executor.getMaximumPoolSize();
            this.completedTaskCount = executor.getCompletedTaskCount();
            this.taskCount = executor.getTaskCount();
            this.queueSize = executor.getQueue().size();
            this.queueRemainingCapacity = executor.getQueue().remainingCapacity();
            this.keepAliveTime = executor.getKeepAliveTime(TimeUnit.SECONDS);
        }

        @Override
        public String toString() {
            return String.format(
                "ThreadPool[%s]: " +
                "poolSize=%d, active=%d, queued=%d/%d, completed=%d, " +
                "core=%d, max=%d",
                poolName,
                poolSize,
                activeCount,
                queueSize,
                queueSize + queueRemainingCapacity,
                completedTaskCount,
                corePoolSize,
                maximumPoolSize
            );
        }

        // Getters
        public String getPoolName() { return poolName; }
        public int getPoolSize() { return poolSize; }
        public int getActiveCount() { return activeCount; }
        public int getQueueSize() { return queueSize; }
        public boolean isHealthy() {
            return queueSize < (queueSize + queueRemainingCapacity) * 0.8;
        }
    }

    /**
     * 打印线程池状态
     */
    public static void printThreadPoolStatus(ThreadPoolExecutor executor, String name) {
        ThreadPoolMetrics metrics = new ThreadPoolMetrics(executor, name);
        System.out.println(metrics);
        
        // 告警检查
        if (!metrics.isHealthy()) {
            System.err.println("⚠️ 警告：线程池队列使用率超过80%");
        }
        
        if (metrics.getActiveCount() >= executor.getMaximumPoolSize()) {
            System.err.println("⚠️ 警告：所有线程都在忙碌");
        }
    }

    /**
     * ==================== 优雅关闭线程池 ====================
     */

    /**
     * ✅ 优雅关闭线程池
     * 
     * 步骤：
     * 1. 调用shutdown()，停止接受新任务
     * 2. 等待现有任务完成（带超时）
     * 3. 如果超时，调用shutdownNow()强制关闭
     * 4. 处理未执行的任务
     */
    public static void gracefulShutdown(ThreadPoolExecutor executor, 
            String poolName, long timeout, TimeUnit unit) {
        System.out.println("开始关闭线程池: " + poolName);
        
        // 第1步：优雅关闭，不再接受新任务
        executor.shutdown();
        
        try {
            // 第2步：等待现有任务完成
            if (!executor.awaitTermination(timeout, unit)) {
                System.err.println("线程池未在指定时间内关闭，强制关闭");
                
                // 第3步：强制关闭
                executor.shutdownNow();
                
                // 再次等待
                if (!executor.awaitTermination(timeout, unit)) {
                    System.err.println("线程池强制关闭失败");
                }
            }
        } catch (InterruptedException e) {
            // 当前线程被中断，强制关闭
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        System.out.println("线程池已关闭: " + poolName);
    }

    /**
     * ==================== 自定义组件 ====================
     */

    /**
     * 命名线程工厂
     */
    public static class NamedThreadFactory implements ThreadFactory {
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;
        private final boolean daemon;

        public NamedThreadFactory(String namePrefix) {
            this(namePrefix, false);
        }

        public NamedThreadFactory(String namePrefix, boolean daemon) {
            this.namePrefix = namePrefix + "-";
            this.daemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
            t.setDaemon(daemon);
            if (t.getPriority() != Thread.NORM_PRIORITY) {
                t.setPriority(Thread.NORM_PRIORITY);
            }
            return t;
        }
    }

    /**
     * 带日志的拒绝策略
     */
    public static class LoggingAbortPolicy extends ThreadPoolExecutor.AbortPolicy {
        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            System.err.println("任务被拒绝: " + r.getClass().getName() + 
                             ", activeCount=" + e.getActiveCount() +
                             ", queueSize=" + e.getQueue().size());
            super.rejectedExecution(r, e);
        }
    }

    /**
     * ==================== 线程池使用示例 ====================
     */

    /**
     * ✅ 正确使用线程池
     */
    public void properThreadPoolUsage() {
        ThreadPoolExecutor executor = createIoIntensivePool();
        
        try {
            // 提交任务
            Future<Integer> future = executor.submit(() -> {
                // 执行任务
                return 42;
            });
            
            // 获取结果（带超时）
            try {
                Integer result = future.get(10, TimeUnit.SECONDS);
                System.out.println("结果: " + result);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                e.printStackTrace();
            }
            
            // 批量提交任务
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    System.out.println("执行任务: " + taskId);
                    // 模拟IO操作
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
        } finally {
            // 优雅关闭
            gracefulShutdown(executor, "io-pool", 60, TimeUnit.SECONDS);
        }
    }

    /**
     * ❌ 反例：错误的线程池使用
     */
    public void badThreadPoolUsage() {
        // ❌ 使用Executors创建的线程池有风险
        
        // 风险1：FixedThreadPool使用无界队列，可能OOM
        ExecutorService badPool1 = Executors.newFixedThreadPool(10);
        
        // 风险2：CachedThreadPool允许无限创建线程，可能耗尽资源
        ExecutorService badPool2 = Executors.newCachedThreadPool();
        
        // 风险3：SingleThreadExecutor使用无界队列
        ExecutorService badPool3 = Executors.newSingleThreadExecutor();
        
        // ❌ 不关闭线程池导致资源泄漏
        // badPool1.shutdown();  // 忘记调用
    }

    /**
     * 主方法：演示线程池使用
     */
    public static void main(String[] args) throws Exception {
        System.out.println("CPU核心数: " + CPU_COUNT);
        
        // 创建线程池
        ThreadPoolExecutor executor = createIoIntensivePool();
        
        try {
            // 打印初始状态
            printThreadPoolStatus(executor, "io-pool");
            
            // 提交一些任务
            for (int i = 0; i < 100; i++) {
                final int taskId = i;
                executor.submit(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            }
            
            // 等待一会
            Thread.sleep(1000);
            
            // 打印运行时状态
            printThreadPoolStatus(executor, "io-pool");
            
        } finally {
            // 优雅关闭
            gracefulShutdown(executor, "io-pool", 30, TimeUnit.SECONDS);
        }
    }
}