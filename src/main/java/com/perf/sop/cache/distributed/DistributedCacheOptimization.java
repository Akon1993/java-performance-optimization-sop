/**
 * 分布式缓存优化 - Redis最佳实践
 * 
 * 【SOP核心要点】
 * 1. 使用连接池管理Redis连接（Lettuce推荐）
 * 2. 合理设置序列化方式
 * 3. 处理缓存穿透、击穿、雪崩问题
 * 4. 实现多级缓存架构
 * 
 * 【缓存问题解决方案】
 * - 穿透：缓存空值 + 布隆过滤器
 * - 击穿：互斥锁 + 逻辑过期
 * - 雪崩：随机过期时间 + 多级缓存
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.cache.distributed;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 模拟Redis操作（实际项目中使用Spring Data Redis）
 */
interface RedisTemplate {
    String get(String key);
    void set(String key, String value, long expireSeconds);
    void setex(String key, long seconds, String value);
    boolean setnx(String key, String value, long expireSeconds);
    boolean delete(String key);
    Set<String> keys(String pattern);
    void publish(String channel, String message);
}

/**
 * 模拟分布式锁
 */
interface DistributedLock {
    boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws InterruptedException;
    void unlock();
    boolean isHeldByCurrentThread();
}

public class DistributedCacheOptimization {

    /**
     * ==================== Redis基础操作 ====================
     */

    private final RedisTemplate redisTemplate;
    private final Cache<String, Object> localCache;

    public DistributedCacheOptimization(RedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.localCache = Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.MINUTES)
            .build();
    }

    /**
     * ✅ 基础缓存操作
     */
    public String get(String key) {
        return redisTemplate.get(key);
    }

    public void set(String key, String value, long expireSeconds) {
        redisTemplate.set(key, value, expireSeconds);
    }

    /**
     * ==================== 缓存问题解决方案 ====================
     */

    /**
     * ✅ 方案1：缓存空值（解决穿透）
     * 
     * 适用场景：查询不存在的数据
     * 注意：空值设置较短的过期时间
     */
    public String getWithNullCache(String key, DataLoader<String> loader) {
        String value = redisTemplate.get(key);
        
        if (value != null) {
            // 空值标记
            if ("NULL".equals(value)) {
                return null;
            }
            return value;
        }
        
        // 从数据库加载
        value = loader.load(key);
        
        if (value != null) {
            redisTemplate.setex(key, 600, value);  // 正常缓存10分钟
        } else {
            redisTemplate.setex(key, 60, "NULL");  // 空值缓存1分钟
        }
        
        return value;
    }

    /**
     * ✅ 方案2：互斥锁（解决击穿）
     * 
     * 适用场景：热点key过期，大量并发请求
     * 实现：只有一个线程重建缓存，其他线程等待或返回旧值
     */
    public String getWithMutex(String key, DataLoader<String> loader, 
            DistributedLock lock) throws InterruptedException {
        // L1: 本地缓存
        String value = (String) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // L2: Redis
        value = redisTemplate.get(key);
        if (value != null) {
            localCache.put(key, value);
            return value;
        }
        
        // 获取分布式锁
        boolean locked = lock.tryLock(100, 10, TimeUnit.SECONDS);
        
        try {
            if (locked) {
                // 双重检查
                value = redisTemplate.get(key);
                if (value != null) {
                    return value;
                }
                
                // 重建缓存
                value = loader.load(key);
                if (value != null) {
                    redisTemplate.setex(key, 600, value);
                    localCache.put(key, value);
                }
            } else {
                // 获取锁失败，短暂等待后重试
                Thread.sleep(100);
                return getWithMutex(key, loader, lock);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        
        return value;
    }

    /**
     * 逻辑过期数据包装
     */
    public static class RedisData {
        private long expireTime;  // 过期时间戳（毫秒）
        private String data;      // JSON数据
        
        public static RedisData of(String data, long expireSeconds) {
            RedisData rd = new RedisData();
            rd.data = data;
            rd.expireTime = System.currentTimeMillis() + expireSeconds * 1000;
            return rd;
        }
        
        public boolean isExpired() {
            return System.currentTimeMillis() > expireTime;
        }
        
        public String getData() { return data; }
        public long getExpireTime() { return expireTime; }
    }

    /**
     * ✅ 方案3：逻辑过期（解决击穿）
     * 
     * 适用场景：热点数据永不过期
     * 实现：设置逻辑过期时间，过期时异步重建
     */
    private final ExecutorService cacheRebuildExecutor = Executors.newFixedThreadPool(10);
    
    public String getWithLogicalExpire(String key, DataLoader<String> loader, 
            DistributedLock lock) {
        // L1: 本地缓存
        String value = (String) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // L2: Redis（逻辑过期）
        String json = redisTemplate.get(key);
        if (json == null) {
            return null;
        }
        
        // 解析逻辑过期数据
        RedisData redisData = parseRedisData(json);
        
        // 未过期，直接返回
        if (!redisData.isExpired()) {
            localCache.put(key, redisData.getData());
            return redisData.getData();
        }
        
        // 已过期，尝试获取锁重建
        try {
            boolean locked = lock.tryLock(0, 10, TimeUnit.SECONDS);
            if (locked) {
                // 提交异步重建任务
                cacheRebuildExecutor.submit(() -> {
                    try {
                        String newValue = loader.load(key);
                        if (newValue != null) {
                            saveWithLogicalExpire(key, newValue, 600);
                        }
                    } finally {
                        lock.unlock();
                    }
                });
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // 返回过期数据（保证可用性）
        return redisData.getData();
    }

    public void saveWithLogicalExpire(String key, String value, long expireSeconds) {
        RedisData data = RedisData.of(value, expireSeconds);
        redisTemplate.set(key, toJson(data), 0);  // 永不过期
        localCache.put(key, value);
    }

    /**
     * ✅ 方案4：随机过期时间（解决雪崩）
     */
    public void setWithRandomExpire(String key, String value, long baseExpireSeconds) {
        // 基础过期时间 + 随机0-300秒
        long expire = baseExpireSeconds + ThreadLocalRandom.current().nextInt(0, 300);
        redisTemplate.setex(key, expire, value);
    }

    /**
     * ==================== 多级缓存实现 ====================
     */

    /**
     * 多级缓存读取
     * L1: Caffeine本地缓存
     * L2: Redis分布式缓存
     * L3: 数据库
     */
    public <T> T getWithMultiLevel(String key, Class<T> clazz, 
            DataLoader<T> loader, DistributedLock lock) throws InterruptedException {
        // L1: 本地缓存
        T value = (T) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // L2: Redis
        String json = redisTemplate.get(key);
        if (json != null) {
            if ("NULL".equals(json)) {
                return null;
            }
            value = fromJson(json, clazz);
            localCache.put(key, value);
            return value;
        }
        
        // L3: 数据库（加锁防止击穿）
        boolean locked = lock.tryLock(100, 10, TimeUnit.SECONDS);
        
        try {
            if (locked) {
                // 双重检查
                json = redisTemplate.get(key);
                if (json != null) {
                    return fromJson(json, clazz);
                }
                
                value = loader.load(key);
                if (value != null) {
                    redisTemplate.setex(key, 600, toJson(value));
                    localCache.put(key, value);
                } else {
                    redisTemplate.setex(key, 60, "NULL");
                }
            } else {
                Thread.sleep(100);
                return getWithMultiLevel(key, clazz, loader, lock);
            }
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        
        return value;
    }

    /**
     * 删除缓存（保证一致性）
     */
    public void deleteWithConsistency(String key) {
        // 先删除Redis
        redisTemplate.delete(key);
        
        // 再删除本地缓存
        localCache.invalidate(key);
        
        // 发送消息通知其他节点
        redisTemplate.publish("cache:invalidation", key);
    }

    /**
     * ==================== 布隆过滤器（解决穿透） ====================
     */

    /**
     * 简单布隆过滤器实现
     */
    public static class SimpleBloomFilter {
        private final BitSet bitSet;
        private final int bitSize;
        private final int[] seeds;
        
        public SimpleBloomFilter(int expectedInsertions, double fpp) {
            this.bitSize = optimalNumOfBits(expectedInsertions, fpp);
            this.bitSet = new BitSet(bitSize);
            this.seeds = new int[]{3, 5, 7, 11, 13, 31, 37, 61};
        }
        
        public void add(String element) {
            for (int seed : seeds) {
                int hash = hash(element, seed);
                bitSet.set(hash % bitSize);
            }
        }
        
        public boolean mightContain(String element) {
            for (int seed : seeds) {
                int hash = hash(element, seed);
                if (!bitSet.get(hash % bitSize)) {
                    return false;
                }
            }
            return true;
        }
        
        private int hash(String element, int seed) {
            int hash = 0;
            for (int i = 0; i < element.length(); i++) {
                hash = seed * hash + element.charAt(i);
            }
            return hash & Integer.MAX_VALUE;
        }
        
        private int optimalNumOfBits(long n, double p) {
            return (int) (-n * Math.log(p) / (Math.log(2) * Math.log(2)));
        }
    }

    /**
     * 使用布隆过滤器防止穿透
     */
    private SimpleBloomFilter bloomFilter = new SimpleBloomFilter(1000000, 0.01);
    
    public String getWithBloomFilter(String key, DataLoader<String> loader) {
        // 检查布隆过滤器
        if (!bloomFilter.mightContain(key)) {
            return null;  // 一定不存在
        }
        
        // 继续查询缓存和数据库
        return getWithNullCache(key, loader);
    }

    /**
     * ==================== 辅助方法 ====================
     */

    @FunctionalInterface
    public interface DataLoader<T> {
        T load(String key);
    }

    private String toJson(Object obj) {
        // 简化的JSON序列化
        return obj.toString();
    }

    private <T> T fromJson(String json, Class<T> clazz) {
        // 简化的JSON反序列化
        return null;
    }

    private RedisData parseRedisData(String json) {
        // 解析逻辑过期数据
        return new RedisData();
    }

    /**
     * ==================== 模拟实现 ====================
     */

    private static class BitSet {
        private final boolean[] bits;
        
        public BitSet(int size) {
            this.bits = new boolean[size];
        }
        
        public void set(int index) {
            bits[index] = true;
        }
        
        public boolean get(int index) {
            return bits[index];
        }
    }

    /**
     * 主方法：演示分布式缓存使用
     */
    public static void main(String[] args) throws Exception {
        // 创建模拟的RedisTemplate
        RedisTemplate mockRedis = new RedisTemplate() {
            private final ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            
            @Override
            public String get(String key) {
                return store.get(key);
            }
            
            @Override
            public void set(String key, String value, long expireSeconds) {
                store.put(key, value);
            }
            
            @Override
            public void setex(String key, long seconds, String value) {
                store.put(key, value);
            }
            
            @Override
            public boolean setnx(String key, String value, long expireSeconds) {
                return store.putIfAbsent(key, value) == null;
            }
            
            @Override
            public boolean delete(String key) {
                return store.remove(key) != null;
            }
            
            @Override
            public Set<String> keys(String pattern) {
                return store.keySet();
            }
            
            @Override
            public void publish(String channel, String message) {
                System.out.println("发布消息: channel=" + channel + ", message=" + message);
            }
        };

        DistributedCacheOptimization cache = new DistributedCacheOptimization(mockRedis);
        
        System.out.println("========== 分布式缓存优化演示 ==========\n");
        
        // 演示缓存空值
        System.out.println("1. 测试缓存空值（防止穿透）");
        String result = cache.getWithNullCache("user:not_exist", key -> {
            System.out.println("  查询数据库: " + key);
            return null;  // 数据库不存在
        });
        System.out.println("  结果: " + result);
        
        // 第二次查询（应该走缓存）
        result = cache.getWithNullCache("user:not_exist", key -> {
            System.out.println("  不应该执行到这里");
            return null;
        });
        System.out.println("  缓存结果: " + result);
        
        // 演示随机过期时间
        System.out.println("\n2. 测试随机过期时间（防止雪崩）");
        for (int i = 0; i < 5; i++) {
            cache.setWithRandomExpire("key:" + i, "value" + i, 600);
        }
        System.out.println("  已设置5个key，过期时间在600-900秒之间");
        
        System.out.println("\n========== 演示完成 ==========");
    }
}
