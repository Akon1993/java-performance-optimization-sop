/**
 * 本地缓存优化 - Caffeine最佳实践
 * 
 * 【SOP核心要点】
 * 1. 选择合适的缓存容量和过期策略
 * 2. 使用LoadingCache自动加载数据
 * 3. 监控缓存命中率
 * 4. 合理处理缓存刷新
 * 
 * 【Caffeine vs Guava Cache】
 * - Caffeine基于W-TinyLFU算法，命中率更高
 * - Caffeine性能更好（读写性能都优于Guava）
 * - Caffeine支持异步加载
 * 
 * 【适用场景】
 * - 读多写少的数据
 * - 单机部署的应用
 * - 对访问延迟要求极高（纳秒级）
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.cache.local;

import com.github.benmanes.caffeine.cache.*;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.google.common.base.Stopwatch;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
// import java.util.concurrent.ExecutionException;  // Caffeine 3.x may not throw this
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LocalCacheOptimization {

    /**
     * ==================== 基础缓存配置 ====================
     */

    /**
     * ✅ 基础手动加载缓存
     * 
     * 适用场景：需要手动控制缓存写入
     */
    public Cache<String, Object> createManualCache() {
        return Caffeine.newBuilder()
            // 初始容量（减少扩容开销）
            .initialCapacity(100)
            // 最大容量（基于条数）
            .maximumSize(10000)
            // 写入后过期时间
            .expireAfterWrite(10, TimeUnit.MINUTES)
            // 访问后过期时间（与expireAfterWrite互斥）
            // .expireAfterAccess(5, TimeUnit.MINUTES)
            // 启用统计
            .recordStats()
            // 淘汰监听器
            .removalListener((key, value, cause) ->
                System.out.printf("缓存被淘汰: key=%s, cause=%s%n", key, cause))
            .build();
    }

    /**
     * ✅ 自动加载缓存（推荐）
     * 
     * 优势：
     * 1. 自动处理缓存未命中
     * 2. 支持批量加载
     * 3. 线程安全
     */
    public LoadingCache<String, User> createLoadingCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .recordStats()
            .build(key -> loadUserFromDatabase(key));
    }

    /**
     * ✅ 异步加载缓存
     * 
     * 适用场景：数据加载耗时较长
     */
    public AsyncLoadingCache<String, User> createAsyncLoadingCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .buildAsync((key, executor) -> 
                CompletableFuture.supplyAsync(
                    () -> loadUserFromDatabase(key), executor));
    }

    /**
     * ==================== 高级配置 ====================
     */

    /**
     * ✅ 基于权重的缓存（不同对象占用内存不同）
     * 
     * 适用场景：缓存对象大小差异较大
     */
    public Cache<String, byte[]> createWeightedCache() {
        return Caffeine.newBuilder()
            // 最大权重（近似总字节数）
            .maximumWeight(10 * 1024 * 1024)  // 10MB
            // 计算每条缓存的权重
            .weigher((String key, byte[] value) -> value.length)
            .build();
    }

    /**
     * ✅ 基于时间的自定义过期策略
     * 
     * 适用场景：不同数据有不同的过期需求
     */
    public Cache<String, Object> createCustomExpireCache() {
        return Caffeine.newBuilder()
            .expireAfter(new Expiry<String, Object>() {
                @Override
                public long expireAfterCreate(String key, Object value, long currentTime) {
                    // 根据key或value动态设置过期时间
                    if (key.startsWith("hot:")) {
                        return TimeUnit.MINUTES.toNanos(5);  // 热点数据5分钟
                    }
                    return TimeUnit.MINUTES.toNanos(30);      // 普通数据30分钟
                }
                
                @Override
                public long expireAfterUpdate(String key, Object value, 
                        long currentTime, long currentDuration) {
                    return currentDuration;  // 更新不改变过期时间
                }
                
                @Override
                public long expireAfterRead(String key, Object value, 
                        long currentTime, long currentDuration) {
                    // 读取后延长过期时间（滑动过期）
                    return TimeUnit.MINUTES.toNanos(10);
                }
            })
            .build();
    }

    /**
     * ✅ 自动刷新缓存
     * 
     * 适用场景：数据变更不频繁，但需要保持较新
     * 注意：refreshAfterWrite会在访问时异步刷新，不会阻塞读取
     */
    public LoadingCache<String, Object> createRefreshCache() {
        return Caffeine.newBuilder()
            .maximumSize(10000)
            .expireAfterWrite(1, TimeUnit.HOURS)      // 硬过期时间
            .refreshAfterWrite(1, TimeUnit.MINUTES)   // 软刷新时间
            .build(key -> loadFromDatabase(key));
    }

    /**
     * ==================== 使用示例 ====================
     */

    /**
     * ✅ 缓存使用最佳实践
     */
    public void cacheUsageExample() {
        LoadingCache<String, User> cache = createLoadingCache();
        
        // 1. 获取数据（自动加载）
        User user = cache.get("user:12345");
        System.out.println("用户: " + user);
        
        // 2. 批量获取 (Caffeine 3.x API)
        Map<String, User> users = new HashMap<>();
        for (String key : Arrays.asList("user:1", "user:2", "user:3")) {
            users.put(key, cache.get(key));
        }
        System.out.println("批量获取: " + users);
        
        // 3. 手动放入缓存
        User newUser = new User("99999", "张三");
        cache.put("user:99999", newUser);
        
        // 4. 条件性放入（如果不存在）
        cache.asMap().putIfAbsent("user:99999", newUser);
        
        // 5. 使缓存失效
        cache.invalidate("user:12345");      // 单条
        cache.invalidateAll(Arrays.asList("user:1", "user:2"));  // 批量
        cache.invalidateAll();                // 全部
        
        // 6. 获取统计信息
        CacheStats stats = cache.stats();
        System.out.println("命中率: " + stats.hitRate());
        System.out.println("加载次数: " + stats.loadCount());
        System.out.println("平均加载时间: " + stats.averageLoadPenalty() + "ms");
    }

    /**
     * ✅ 防止缓存穿透（Cache-Aside模式）
     */
    public User getUserWithNullHandling(LoadingCache<String, User> cache, String userId) {
        String key = "user:" + userId;
        
        try {
            User user = cache.get(key);
            return user;
        } catch (Exception e) {
            // 缓存加载异常，直接查数据库
            return loadUserFromDatabase(key);
        }
    }

    /**
     * ✅ 缓存预热
     */
    public void warmupCache(LoadingCache<String, User> cache, List<String> hotKeys) {
        System.out.println("开始缓存预热...");
        Stopwatch stopwatch = Stopwatch.createStarted();
        
        // 批量加载热点数据
        for (String key : hotKeys) {
            cache.get(key);
        }
        
        System.out.println("缓存预热完成，耗时: " + stopwatch.elapsed(TimeUnit.MILLISECONDS) + "ms");
    }

    /**
     * ==================== 性能对比 ====================
     */

    /**
     * Caffeine vs ConcurrentHashMap vs Guava Cache性能对比
     */
    public void performanceComparison() {
        final int ITERATIONS = 1000000;
        final int THREAD_COUNT = 4;
        
        // 1. ConcurrentHashMap（无自动加载）
        java.util.concurrent.ConcurrentHashMap<String, User> map = 
            new java.util.concurrent.ConcurrentHashMap<>();
        
        // 2. Caffeine
        LoadingCache<String, User> caffeineCache = Caffeine.newBuilder()
            .maximumSize(10000)
            .build(key -> loadUserFromDatabase(key));
        
        AtomicInteger counter = new AtomicInteger(0);
        
        // 预热
        for (int i = 0; i < 1000; i++) {
            map.put("key" + i, new User(String.valueOf(i), "user" + i));
            caffeineCache.put("key" + i, new User(String.valueOf(i), "user" + i));
        }
        
        // 测试ConcurrentHashMap
        long mapTime = benchmark(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                String key = "key" + (i % 1000);
                User user = map.get(key);
                if (user == null) {
                    map.put(key, loadUserFromDatabase(key));
                }
            }
        });
        
        // 测试Caffeine
        long caffeineTime = benchmark(() -> {
            for (int i = 0; i < ITERATIONS; i++) {
                String key = "key" + (i % 1000);
                caffeineCache.get(key);
            }
        });
        
        System.out.println("性能对比（" + ITERATIONS + "次操作）：");
        System.out.println("ConcurrentHashMap: " + mapTime + "ms");
        System.out.println("Caffeine: " + caffeineTime + "ms");
        System.out.println("Caffeine命中率: " + caffeineCache.stats().hitRate());
    }

    private long benchmark(Runnable task) {
        long start = System.currentTimeMillis();
        task.run();
        return System.currentTimeMillis() - start;
    }

    /**
     * ==================== 模拟数据库操作 ====================
     */

    private User loadUserFromDatabase(String key) {
        // 模拟数据库查询耗时
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        String userId = key.replace("user:", "");
        return new User(userId, "User_" + userId);
    }

    private Map<String, User> loadUsersFromDatabase(Iterable<? extends String> keys) {
        // 模拟批量查询
        System.out.println("批量加载: " + keys);
        Map<String, User> result = new java.util.HashMap<>();
        for (String key : keys) {
            result.put(key, loadUserFromDatabase(key));
        }
        return result;
    }

    private Object loadFromDatabase(String key) {
        System.out.println("加载数据: " + key);
        return new Object();
    }

    /**
     * 用户实体类
     */
    public static class User {
        private String id;
        private String name;
        
        public User(String id, String name) {
            this.id = id;
            this.name = name;
        }
        
        public String getId() { return id; }
        public String getName() { return name; }
        
        @Override
        public String toString() {
            return "User{id='" + id + "', name='" + name + "'}";
        }
    }

    /**
     * 主方法：演示缓存使用
     */
    public static void main(String[] args) throws Exception {
        LocalCacheOptimization demo = new LocalCacheOptimization();
        
        System.out.println("========== 本地缓存优化演示 ==========\n");
        
        // 1. 创建缓存
        LoadingCache<String, User> cache = demo.createLoadingCache();
        
        // 2. 缓存预热
        demo.warmupCache(cache, Arrays.asList("user:1", "user:2", "user:3"));
        
        // 3. 使用缓存
        demo.cacheUsageExample();
        
        // 4. 性能对比
        System.out.println("\n========== 性能对比 ==========");
        demo.performanceComparison();
    }
}
