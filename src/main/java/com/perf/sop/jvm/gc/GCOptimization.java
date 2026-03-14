/**
 * JVM垃圾回收优化与监控
 * 
 * 【SOP核心要点】
 * 1. 根据应用特点选择合适的GC算法
 * 2. 合理设置堆内存大小
 * 3. 监控GC日志，及时发现性能问题
 * 4. 避免Full GC频繁触发
 * 5. 理解各GC算法的适用场景
 * 
 * 【GC选择指南】
 * 
 * ┌─────────────────┬─────────────────────────────────────────────────────┐
 * │ GC算法           │ 适用场景                                             │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ Serial          │ 单核CPU、小内存（<100MB）、客户端应用                  │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ Parallel        │ 多核CPU、批处理应用、追求吞吐量                         │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ CMS             │ 低延迟要求、JDK 8及以下（已废弃）                       │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ G1              │ 大堆内存（>6GB）、平衡吞吐量和延迟、JDK 9+默认          │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ ZGC             │ 超大堆（TB级）、超低延迟（<10ms）、JDK 11+             │
 * ├─────────────────┼─────────────────────────────────────────────────────┤
 * │ Shenandoah      │ 低延迟、与ZGC类似、OpenJDK特性                         │
 * └─────────────────┴─────────────────────────────────────────────────────┘
 * 
 * 【推荐JVM参数配置】
 * 
 * 1. G1 GC（JDK 9+默认，推荐大多数场景）
 *    -XX:+UseG1GC
 *    -Xms4g -Xmx4g
 *    -XX:MaxGCPauseMillis=200
 *    -XX:+PrintGCDetails
 *    -Xlog:gc*:file=gc.log
 * 
 * 2. ZGC（JDK 11+，超低延迟场景）
 *    -XX:+UseZGC
 *    -Xms16g -Xmx16g
 *    -XX:+ZGenerational  (JDK 21+)
 *    -Xlog:gc*:file=gc.log
 * 
 * 3. 生产环境通用配置
 *    -server
 *    -Xms8g -Xmx8g          # 堆内存，建议Xms=Xmx避免动态调整
 *    -XX:MetaspaceSize=256m # 元空间初始大小
 *    -XX:MaxMetaspaceSize=512m
 *    -XX:+UseG1GC
 *    -XX:MaxGCPauseMillis=200
 *    -XX:+HeapDumpOnOutOfMemoryError
 *    -XX:HeapDumpPath=/path/to/dumps
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.jvm.gc;

import java.lang.management.*;
import java.util.*;

public class GCOptimization {

    /**
     * ==================== GC信息监控 ====================
     */

    /**
     * 获取当前JVM的GC信息
     * 
     * 可用于：
     * 1. 应用启动时打印GC配置
     * 2. 监控页面展示GC统计
     * 3. 告警系统检测GC异常
     */
    public static void printGCInfo() {
        System.out.println("========== JVM GC信息 ==========");
        
        // 获取所有垃圾收集器
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            System.out.println("\nGC名称: " + gcBean.getName());
            System.out.println("收集次数: " + gcBean.getCollectionCount());
            System.out.println("收集时间: " + gcBean.getCollectionTime() + " ms");
            
            // 计算平均每次GC耗时
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count > 0) {
                System.out.printf("平均GC耗时: %.2f ms%n", (double) time / count);
            }
        }
        
        // 获取堆内存信息
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        System.out.println("\n堆内存使用: " + formatBytes(heapUsage.getUsed()) + 
                          " / " + formatBytes(heapUsage.getMax()));
        
        // 获取非堆内存（元空间、Code Cache等）
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        System.out.println("非堆内存使用: " + formatBytes(nonHeapUsage.getUsed()) + 
                          " / " + formatBytes(nonHeapUsage.getMax()));
    }

    /**
     * 格式化字节数为可读格式
     */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * ==================== GC日志分析 ====================
     */

    /**
     * GC日志解析工具示例
     * 
     * 实际生产环境建议使用：
     * - GCEasy (在线工具)
     * - GCViewer (桌面工具)
     * - 自定义ELK收集分析
     * 
     * 关键指标：
     * 1. GC频率：Young GC < 1次/秒，Full GC 尽量为0
     * 2. GC耗时：Young GC < 50ms，Full GC < 1s
     * 3. 吞吐量：GC时间占比 < 5%
     * 4. 内存分配速率：观察eden区分配速度
     */
    public void analyzeGCLogs() {
        // 这是一个示例框架，实际应该解析GC日志文件
        System.out.println("GC日志分析指标:");
        System.out.println("1. GC频率 - 单位时间内的GC次数");
        System.out.println("2. GC耗时 - 每次GC的停顿时间");
        System.out.println("3. 吞吐量 - (总时间 - GC时间) / 总时间");
        System.out.println("4. 内存回收效率 - 每次GC回收的内存量");
    }

    /**
     * ==================== 内存泄漏检测 ====================
     */

    /**
     * 模拟内存泄漏场景
     * 
     * 常见内存泄漏原因：
     * 1. 静态集合持有对象引用
     * 2. 未关闭的资源（数据库连接、文件流）
     * 3. 监听器未移除
     * 4. ThreadLocal使用不当
     * 5. 缓存无限增长
     * 
     * 检测工具：
     * - VisualVM
     * - Eclipse MAT
     * - JProfiler
     * - Arthas
     */
    private static final List<Object> LEAKY_CACHE = new ArrayList<>();
    
    public void demonstrateMemoryLeak() {
        System.out.println("演示内存泄漏...");
        
        // ❌ 错误示范：静态集合无限增长
        for (int i = 0; i < 10000; i++) {
            byte[] data = new byte[1024 * 1024]; // 1MB
            LEAKY_CACHE.add(data);  // 永远不会被释放！
        }
        
        System.out.println("缓存大小: " + LEAKY_CACHE.size() + " MB");
    }

    /**
     * ✅ 使用WeakReference避免内存泄漏
     */
    private static final List<java.lang.ref.WeakReference<Object>> WEAK_CACHE = 
        new ArrayList<>();
    
    public void properCacheWithWeakReference() {
        System.out.println("使用WeakReference的正确缓存...");
        
        // ✅ WeakReference在GC时会被回收
        for (int i = 0; i < 1000; i++) {
            byte[] data = new byte[1024 * 1024];
            WEAK_CACHE.add(new java.lang.ref.WeakReference<>(data));
        }
        
        // 主动触发GC
        System.gc();
        
        // 清理已回收的引用
        WEAK_CACHE.removeIf(ref -> ref.get() == null);
        
        System.out.println("缓存大小（GC后）: " + WEAK_CACHE.size());
    }

    /**
     * ==================== 大对象优化 ====================
     */

    /**
     * 大对象直接进入老年代
 * 
     * JVM参数：-XX:PretenureSizeThreshold=1m
     * 超过此大小的对象直接在老年代分配
     * 
     * 适用场景：
     * - 大数组、大集合
     * - 生命周期长的大对象
     * - 避免在Eden和Survivor区之间复制
     */
    public void largeObjectHandling() {
        // 大对象示例：1MB数组
        byte[] largeArray = new byte[1024 * 1024];
        
        // 优化建议：
        // 1. 使用对象池复用大对象
        // 2. 及时置null，帮助GC
        // 3. 考虑使用堆外内存（DirectByteBuffer）
        
        // 使用完后置null
        largeArray = null;
    }

    /**
     * ==================== GC触发优化 ====================
     */

    /**
     * 手动触发GC（不推荐在生产环境使用）
     * 
     * 适用场景：
     * - 测试环境验证GC行为
     * - 批量任务完成后立即回收内存
     * - 内存敏感型应用在低峰期主动回收
     * 
     * ⚠️ 注意：
     * - System.gc()只是建议，JVM不一定会立即执行
     * - 生产环境应避免频繁调用
     */
    public void triggerGC() {
        // 获取GC前的内存状态
        Runtime runtime = Runtime.getRuntime();
        long memoryBefore = runtime.totalMemory() - runtime.freeMemory();
        
        System.out.println("GC前内存使用: " + formatBytes(memoryBefore));
        
        // 触发GC（仅测试用途）
        System.gc();
        
        // 等待GC完成（实际不可控）
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        long memoryAfter = runtime.totalMemory() - runtime.freeMemory();
        System.out.println("GC后内存使用: " + formatBytes(memoryAfter));
        System.out.println("回收内存: " + formatBytes(memoryBefore - memoryAfter));
    }

    /**
     * ==================== 堆外内存管理 ====================
     */

    /**
     * 堆外内存使用示例
     * 
     * 优势：
     * 1. 不受堆大小限制
     * 2. 减少GC压力
     * 3. 适合大内存、长生命周期对象
     * 
     * 劣势：
     * 1. 分配和回收成本较高
     * 2. 内存泄漏难以检测
     * 3. 需要手动管理
     * 
     * JVM参数：
     * -XX:MaxDirectMemorySize=1g  # 限制堆外内存大小
     */
    public void offHeapMemoryExample() {
        // 分配堆外内存
        java.nio.ByteBuffer directBuffer = java.nio.ByteBuffer.allocateDirect(1024 * 1024);
        
        // 使用堆外内存
        directBuffer.putInt(42);
        directBuffer.flip();
        int value = directBuffer.getInt();
        
        System.out.println("读取值: " + value);
        
        // 清理堆外内存（JDK 9+ 需要使用反射）
        // 注意：以下代码仅供演示，生产环境请使用专门的堆外内存管理库
        cleanDirectBuffer(directBuffer);
    }
    
    /**
     * 使用反射清理直接内存缓冲区（JDK 9+兼容方式）
     */
    private void cleanDirectBuffer(java.nio.ByteBuffer buffer) {
        if (buffer == null || !buffer.isDirect()) {
            return;
        }
        
        try {
            // 获取Unsafe类的cleaner方法
            java.lang.reflect.Method cleanerMethod = buffer.getClass().getMethod("cleaner");
            cleanerMethod.setAccessible(true);
            Object cleaner = cleanerMethod.invoke(buffer);
            if (cleaner != null) {
                java.lang.reflect.Method cleanMethod = cleaner.getClass().getMethod("clean");
                cleanMethod.setAccessible(true);
                cleanMethod.invoke(cleaner);
            }
        } catch (Exception e) {
            // 清理失败，依赖GC最终回收
            System.out.println("堆外内存清理失败（将依赖GC回收）: " + e.getMessage());
        }
    }

    /**
     * ==================== GC配置模板 ====================
     */

    /**
     * 打印推荐的JVM参数配置
     */
    public static void printRecommendedVMOptions() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("\n========== JVM GC配置模板 ==========\n\n");
        
        // G1 GC配置
        sb.append("【G1 GC - 通用推荐】\n");
        sb.append("-XX:+UseG1GC\n");
        sb.append("-Xms4g -Xmx4g\n");
        sb.append("-XX:MaxGCPauseMillis=200\n");
        sb.append("-XX:+ParallelRefProcEnabled\n");
        sb.append("-XX:+AlwaysPreTouch\n");
        sb.append("-XX:+DisableExplicitGC\n");
        sb.append("-Xlog:gc*:file=logs/gc.log:time,uptime:filecount=5,filesize=100m\n\n");
        
        // ZGC配置
        sb.append("【ZGC - 超低延迟】\n");
        sb.append("-XX:+UseZGC\n");
        sb.append("-Xms16g -Xmx16g\n");
        sb.append("-XX:+ZGenerational  # JDK 21+\n");
        sb.append("-XX:+DisableExplicitGC\n");
        sb.append("-Xlog:gc*:file=logs/gc.log:time,uptime:filecount=5,filesize=100m\n\n");
        
        // OOM处理
        sb.append("【OOM处理配置】\n");
        sb.append("-XX:+HeapDumpOnOutOfMemoryError\n");
        sb.append("-XX:HeapDumpPath=/var/log/app/heapdump.hprof\n");
        sb.append("-XX:OnOutOfMemoryError=\"sh /scripts/alert.sh\"\n\n");
        
        // 元空间配置
        sb.append("【元空间配置】\n");
        sb.append("-XX:MetaspaceSize=256m\n");
        sb.append("-XX:MaxMetaspaceSize=512m\n");
        sb.append("-XX:+CMSClassUnloadingEnabled  # JDK 8\n\n");
        
        System.out.println(sb.toString());
    }

    /**
     * ==================== GC性能测试 ====================
     */

    /**
     * GC压力测试
     * 
     * 使用方法：
     * 1. 配置不同的GC参数运行
     * 2. 观察GC日志和吞吐量
     * 3. 对比不同GC算法的性能
     */
    public static void gcStressTest() {
        System.out.println("开始GC压力测试...");
        
        final int OBJECT_SIZE = 1024; // 1KB
        final int OBJECT_COUNT = 1000000;
        final int ITERATIONS = 10;
        
        List<byte[]> list = new ArrayList<>();
        long totalTime = 0;
        
        for (int i = 0; i < ITERATIONS; i++) {
            long start = System.currentTimeMillis();
            
            // 创建临时对象
            for (int j = 0; j < OBJECT_COUNT; j++) {
                byte[] obj = new byte[OBJECT_SIZE];
                if (j % 2 == 0) {
                    list.add(obj);
                }
            }
            
            // 清理一半对象
            list.subList(0, list.size() / 2).clear();
            
            long end = System.currentTimeMillis();
            totalTime += (end - start);
            
            System.out.printf("迭代 %d: %d ms, 列表大小: %d%n", 
                            i + 1, (end - start), list.size());
        }
        
        System.out.printf("平均耗时: %.2f ms%n", (double) totalTime / ITERATIONS);
        
        // 打印GC统计
        printGCInfo();
    }

    /**
     * 主方法：运行GC监控和测试
     */
    public static void main(String[] args) {
        // 打印JVM信息
        printGCInfo();
        
        System.out.println("\n");
        
        // 打印推荐配置
        printRecommendedVMOptions();
        
        // 运行压力测试（可选）
        // gcStressTest();
    }
}