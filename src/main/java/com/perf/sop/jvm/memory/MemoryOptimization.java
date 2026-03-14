/**
 * JVM内存模型与优化
 * 
 * 【SOP核心要点】
 * 1. 理解JVM内存结构（堆、栈、元空间、Code Cache等）
 * 2. 合理分配各代内存比例
 * 3. 避免内存溢出和内存泄漏
 * 4. 监控内存使用，及时调整
 * 5. 使用逃逸分析和栈上分配优化
 * 
 * 【JVM内存结构】
 * 
 * ┌──────────────────────────────────────────────────────┐
 * │ 堆内存（Heap）                                         │
 * │  ┌─────────────┬─────────────┬──────────────────┐    │
 * │  │  Eden区     │ Survivor 0  │ Survivor 1       │    │
 * │  │  （新生代）  │   （From）   │    （To）         │    │
 * │  └─────────────┴─────────────┴──────────────────┘    │
 * │  ┌────────────────────────────────────────────────┐  │
 * │  │ 老年代（Old Generation）                        │  │
 * │  └────────────────────────────────────────────────┘  │
 * └──────────────────────────────────────────────────────┘
 * 
 * 非堆内存（Non-Heap）：
 * - 元空间（Metaspace）：类元数据
 * - Code Cache：JIT编译后的本地代码
 * - 直接内存（Direct Memory）：堆外内存
 * - 线程栈（Thread Stack）：每个线程的私有内存
 * 
 * 【内存分配参数】
 * 
 * 1. 堆内存
 *    -Xms4g -Xmx4g           # 初始和最大堆内存
 *    -Xmn1g                  # 新生代大小
 *    -XX:NewRatio=2          # 老年代:新生代 = 2:1
 *    -XX:SurvivorRatio=8     # Eden:S0:S1 = 8:1:1
 * 
 * 2. 元空间
 *    -XX:MetaspaceSize=128m  # 初始元空间大小
 *    -XX:MaxMetaspaceSize=512m # 最大元空间大小
 * 
 * 3. 线程栈
 *    -Xss512k                # 每个线程栈大小
 * 
 * 4. 直接内存
 *    -XX:MaxDirectMemorySize=1g
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.jvm.memory;

import java.lang.management.*;
import java.nio.ByteBuffer;
import java.util.*;

public class MemoryOptimization {

    /**
     * ==================== 内存信息监控 ====================
     */

    /**
     * 获取详细的内存使用信息
     */
    public static void printMemoryDetails() {
        System.out.println("========== JVM内存详情 ==========\n");
        
        // 堆内存
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        System.out.println("【堆内存】");
        System.out.println("  初始: " + formatBytes(heapUsage.getInit()));
        System.out.println("  已使用: " + formatBytes(heapUsage.getUsed()));
        System.out.println("  已提交: " + formatBytes(heapUsage.getCommitted()));
        System.out.println("  最大: " + formatBytes(heapUsage.getMax()));
        
        // 非堆内存
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();
        System.out.println("\n【非堆内存】");
        System.out.println("  初始: " + formatBytes(nonHeapUsage.getInit()));
        System.out.println("  已使用: " + formatBytes(nonHeapUsage.getUsed()));
        System.out.println("  已提交: " + formatBytes(nonHeapUsage.getCommitted()));
        System.out.println("  最大: " + formatBytes(nonHeapUsage.getMax()));
        
        // 各内存池详情
        System.out.println("\n【内存池详情】");
        List<MemoryPoolMXBean> memoryPools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : memoryPools) {
            MemoryUsage usage = pool.getUsage();
            System.out.println("  " + pool.getName() + ":");
            System.out.println("    已使用: " + formatBytes(usage.getUsed()) + 
                             " / " + formatBytes(usage.getMax()));
        }
        
        // 缓冲区池（直接内存）
        System.out.println("\n【直接内存】");
        BufferPoolMXBean directBufferPool = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)
            .stream()
            .filter(pool -> pool.getName().equals("direct"))
            .findFirst()
            .orElse(null);
        
        if (directBufferPool != null) {
            System.out.println("  数量: " + directBufferPool.getCount());
            System.out.println("  内存使用: " + formatBytes(directBufferPool.getMemoryUsed()));
            System.out.println("  总容量: " + formatBytes(directBufferPool.getTotalCapacity()));
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "undefined";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    /**
     * ==================== 对象内存计算 ====================
     */

    /**
     * 估算对象内存占用（简化版）
     * 
     * 对象内存布局（64位JVM，开启压缩指针）：
     * - 对象头（Header）：12 bytes
     * - 对齐填充（Padding）：8字节对齐
     * 
     * 示例：
     * - Object: 12 (header) + 0 (fields) + 4 (padding) = 16 bytes
     * - Integer: 12 (header) + 4 (value) + 0 (padding) = 16 bytes
     * - String: 12 (header) + 4 (hash) + 4 (char[] ref) + 4 (padding) = 24 bytes（不包含char[]）
     */
    public void objectMemoryLayout() {
        System.out.println("\n【对象内存布局（64位JVM）】");
        System.out.println("Object: 16 bytes");
        System.out.println("Integer: 16 bytes");
        System.out.println("Long: 24 bytes");
        System.out.println("String（空）: 40 bytes（对象头+字段+char[]引用+char[]数组）");
        System.out.println("ArrayList（空）: 24 bytes");
        
        // 使用Instrumentation精确测量（需要-javaagent）
        // 或者使用jol（Java Object Layout）库
    }

    /**
     * ==================== 内存分配优化 ====================
     */

    /**
     * ✅ TLAB（Thread Local Allocation Buffer）优化
     * 
     * 原理：
     * - 每个线程在Eden区分配私有缓冲区
     * - 小对象直接在TLAB分配，避免锁竞争
     * - 默认开启，一般不需要调整
     * 
     * JVM参数：
     * -XX:+UseTLAB           # 启用TLAB（默认开启）
     * -XX:TLABSize=512k      # 设置TLAB大小
     * -XX:+ResizeTLAB        # 允许TLAB自动调整大小
     */
    public void tlabOptimization() {
        System.out.println("\n【TLAB优化】");
        System.out.println("TLAB默认开启，无需特殊配置");
        System.out.println("只有在多线程大量创建小对象时，才需要调整TLAB大小");
    }

    /**
     * ✅ 逃逸分析与栈上分配
     * 
     * 原理：
     * - JIT编译器分析对象作用域
     * - 如果对象只在方法内使用（不逃逸），则在栈上分配
     * - 栈上分配的对象随方法结束自动销毁，无需GC
     * 
     * JVM参数：
     * -XX:+DoEscapeAnalysis       # 开启逃逸分析（JDK 8+默认开启）
     * -XX:+EliminateAllocations   # 开启标量替换（默认开启）
     * 
     * 效果：
     * - 减少堆内存分配
     * - 减少GC压力
     * - 提升性能
     */
    public void escapeAnalysisDemo() {
        System.out.println("\n【逃逸分析与栈上分配】");
        System.out.println("开启参数：-XX:+DoEscapeAnalysis");
        
        long start = System.currentTimeMillis();
        
        // 这些Point对象只在方法内使用，会被栈上分配
        for (int i = 0; i < 10000000; i++) {
            Point p = new Point(i, i);  // 可能不会分配在堆上！
            int sum = p.getX() + p.getY();
        }
        
        long end = System.currentTimeMillis();
        System.out.println("执行时间: " + (end - start) + " ms");
        System.out.println("如果逃逸分析生效，几乎没有GC");
    }

    private static class Point {
        private final int x, y;
        
        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
        
        public int getX() { return x; }
        public int getY() { return y; }
    }

    /**
     * ==================== 内存泄漏检测工具 ====================
     */

    /**
     * 内存泄漏检测示例
     * 
     * 常见模式：
     * 1. 静态集合持有对象
     * 2. 未移除的监听器
     * 3. 未关闭的连接
     * 4. ThreadLocal未清理
     * 5. 缓存无限增长
     */
    private static final Map<String, Object> STATIC_CACHE = new HashMap<>();
    private static final ThreadLocal<Object> THREAD_LOCAL = new ThreadLocal<>();
    
    public void memoryLeakPatterns() {
        System.out.println("\n【常见内存泄漏模式】");
        
        // 模式1：静态集合
        System.out.println("1. 静态集合：使用WeakHashMap或SoftReference");
        
        // 模式2：监听器
        System.out.println("2. 监听器：确保在对象销毁时移除监听器");
        
        // 模式3：ThreadLocal
        System.out.println("3. ThreadLocal：使用完后调用remove()");
        try {
            THREAD_LOCAL.set(new byte[1024 * 1024]);
            // 业务逻辑
        } finally {
            THREAD_LOCAL.remove();  // ✅ 必须清理
        }
        
        // 模式4：缓存
        System.out.println("4. 缓存：使用Guava Cache或Caffeine设置过期策略");
    }

    /**
     * ==================== 大页内存（Large Pages）====================
     */

    /**
     * 大页内存配置
     * 
     * 优势：
     * - 减少TLB（Translation Lookaside Buffer）缺失
     * - 提升内存访问性能
     * - 适合大内存应用
     * 
     * 操作系统配置（Linux）：
     * # 查看大页配置
     * cat /proc/meminfo | grep Huge
     * 
     * # 设置大页数量
     * echo 1024 > /proc/sys/vm/nr_hugepages
     * 
     * JVM参数：
     * -XX:+UseLargePages       # 启用大页内存
     * -XX:LargePageSizeInBytes=2m  # 设置大页大小
     * 
     * 注意：需要root权限配置系统大页
     */
    public void largePagesConfiguration() {
        System.out.println("\n【大页内存配置】");
        System.out.println("OS命令: echo 1024 > /proc/sys/vm/nr_hugepages");
        System.out.println("JVM参数: -XX:+UseLargePages");
        System.out.println("适用: 大内存（>8GB）应用，追求极致性能");
    }

    /**
     * ==================== 内存溢出处理 ====================
     */

    /**
     * 配置OOM时的自动处理
     * 
     * JVM参数：
     * -XX:+HeapDumpOnOutOfMemoryError     # OOM时生成堆转储
     * -XX:HeapDumpPath=/path/to/dumps     # 堆转储文件路径
     * -XX:OnOutOfMemoryError="cmd args"   # OOM时执行的命令
     * -XX:+ExitOnOutOfMemoryError         # OOM时退出JVM
     * -XX:+CrashOnOutOfMemoryError        # OOM时生成crash日志
     * 
     * 分析工具：
     * - Eclipse MAT
     * - VisualVM
     * - jhat
     * - Arthas heapdump
     */
    public static void printOOMConfiguration() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n【OOM处理配置模板】\n\n");
        
        sb.append("# 生成堆转储并退出\n");
        sb.append("-XX:+HeapDumpOnOutOfMemoryError \\\n");
        sb.append("-XX:HeapDumpPath=/var/log/app/heapdump.hprof \\\n");
        sb.append("-XX:+ExitOnOutOfMemoryError \\\n");
        sb.append("-XX:OnOutOfMemoryError=\"sh /scripts/cleanup.sh\"\n\n");
        
        sb.append("# 分析堆转储\n");
        sb.append("jhat heapdump.hprof\n");
        sb.append("# 或\n");
        sb.append("eclipse MAT\n");
        
        System.out.println(sb.toString());
    }

    /**
     * ==================== 内存诊断命令 ====================
     */

    /**
     * 打印内存诊断命令参考
     */
    public static void printMemoryCommands() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n【内存诊断命令参考】\n\n");
        
        sb.append("# 1. 查看JVM内存使用\n");
        sb.append("jcmd <pid> VM.native_memory summary\n");
        sb.append("jmap -heap <pid>\n\n");
        
        sb.append("# 2. 生成堆转储\n");
        sb.append("jmap -dump:format=b,file=heap.hprof <pid>\n");
        sb.append("jcmd <pid> GC.heap_dump heap.hprof\n\n");
        
        sb.append("# 3. 查看堆中对象统计\n");
        sb.append("jmap -histo <pid> | head -20\n\n");
        
        sb.append("# 4. 查看GC统计\n");
        sb.append("jstat -gc <pid> 1000\n\n");
        
        sb.append("# 5. 查看类加载统计\n");
        sb.append("jcmd <pid> VM.classloader_stats\n\n");
        
        sb.append("# 6. 使用Arthas诊断\n");
        sb.append("java -jar arthas-boot.jar\n");
        sb.append("dashboard\n");
        sb.append("heapdump\n");
        
        System.out.println(sb.toString());
    }

    /**
     * 主方法
     */
    public static void main(String[] args) {
        // 打印内存详情
        printMemoryDetails();
        
        System.out.println("\n");
        
        // 打印OOM配置
        printOOMConfiguration();
        
        // 打印诊断命令
        printMemoryCommands();
        
        // 运行逃逸分析演示
        MemoryOptimization optimizer = new MemoryOptimization();
        optimizer.escapeAnalysisDemo();
    }
}