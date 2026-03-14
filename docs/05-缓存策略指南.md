# 缓存策略指南

## 目录
1. [缓存设计原则](#1-缓存设计原则)
2. [本地缓存](#2-本地缓存)
3. [分布式缓存](#3-分布式缓存)
4. [缓存问题解决方案](#4-缓存问题解决方案)
5. [多级缓存架构](#5-多级缓存架构)

---

## 1. 缓存设计原则

### 1.1 缓存适用场景

**适合缓存的数据：**
- 读多写少的数据
- 热点数据（高频访问）
- 计算成本高或IO密集的数据
- 配置信息、字典数据
- 用户会话信息

**不适合缓存的数据：**
- 频繁更新的数据
- 对一致性要求极高的数据
- 数据量过大（缓存成本高于计算成本）
- 访问频率低的数据

### 1.2 缓存设计原则

1. **数据一致性**：明确缓存与数据库的一致性策略
2. **过期策略**：设置合理的过期时间
3. **容量规划**：预估缓存容量，避免OOM
4. **降级策略**：缓存失效时的降级方案
5. **监控告警**：缓存命中率和异常监控

---

## 2. 本地缓存

### 2.1 Caffeine（推荐）

**优势：**
- 高性能（基于W-TinyLFU淘汰算法）
- 丰富的配置选项
- 支持异步加载
- 支持刷新机制

**基础配置：**
```java
@Configuration
public class CaffeineConfig {
    
    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
            // 初始容量
            .initialCapacity(100)
            // 最大容量
            .maximumSize(10000)
            // 写入后过期时间
            .expireAfterWrite(10, TimeUnit.MINUTES)
            // 访问后过期时间
            .expireAfterAccess(5, TimeUnit.MINUTES)
            // 刷新时间（异步刷新）
            .refreshAfterWrite(1, TimeUnit.MINUTES)
            // 记录命中率统计
            .recordStats()
            // 淘汰监听器
            .removalListener((key, value, cause) ->
                log.info("缓存被淘汰: key={}, cause={}", key, cause))
            .build();
    }
    
    // 异步加载缓存
    @Bean
    public AsyncLoadingCache<String, User> userCache() {
        return Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .buildAsync(key -> userService.loadUser(key));
    }
}
```

**使用示例：**
```java
@Service
public class UserService {
    
    @Autowired
    private Cache<String, User> userCache;
    
    /**
     * 手动管理缓存
     */
    public User getUser(String userId) {
        User user = userCache.getIfPresent(userId);
        if (user != null) {
            return user;
        }
        
        // 从数据库加载
        user = userMapper.selectById(userId);
        if (user != null) {
            userCache.put(userId, user);
        }
        return user;
    }
    
    /**
     * 使用自动加载
     */
    public User getUserAutoLoad(String userId) {
        return userCache.get(userId, key -> {
            log.info("从数据库加载用户: {}", key);
            return userMapper.selectById(key);
        });
    }
    
    /**
     * 更新时使缓存失效
     */
    public void updateUser(User user) {
        userMapper.update(user);
        userCache.invalidate(user.getId());
    }
}
```

### 2.2 Guava Cache

```java
public class GuavaCacheExample {
    
    private final LoadingCache<String, Object> cache = CacheBuilder.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(10, TimeUnit.MINUTES)
        .recordStats()
        .build(new CacheLoader<String, Object>() {
            @Override
            public Object load(String key) {
                return loadFromDatabase(key);
            }
        });
    
    public Object get(String key) throws ExecutionException {
        return cache.get(key);
    }
    
    public void invalidate(String key) {
        cache.invalidate(key);
    }
    
    public CacheStats getStats() {
        return cache.stats();
    }
}
```

---

## 3. 分布式缓存

### 3.1 Redis配置

**Spring Boot配置：**
```yaml
spring:
  redis:
    host: localhost
    port: 6379
    password: 
    timeout: 2000ms
    lettuce:
      pool:
        max-active: 8
        max-idle: 8
        min-idle: 0
        max-wait: 1000ms
      shutdown-timeout: 100ms
```

**连接池优化：**
```java
@Configuration
public class RedisConfig {
    
    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        RedisStandaloneConfiguration config = 
            new RedisStandaloneConfiguration("localhost", 6379);
        
        ClientOptions clientOptions = ClientOptions.builder()
            .socketOptions(SocketOptions.builder()
                .connectTimeout(Duration.ofMillis(100))
                .keepAlive(true)
                .build())
            .timeoutOptions(TimeoutOptions.builder()
                .timeoutCommands(true)
                .build())
            .build();
        
        LettuceClientConfiguration clientConfig = LettuceClientConfiguration.builder()
            .clientOptions(clientOptions)
            .commandTimeout(Duration.ofMillis(500))
            .build();
        
        return new LettuceConnectionFactory(config, clientConfig);
    }
}
```

### 3.2 RedisTemplate封装

```java
@Component
public class RedisCacheService {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    private static final long DEFAULT_EXPIRE = 600; // 10分钟
    
    public void set(String key, String value) {
        redisTemplate.opsForValue().set(key, value, DEFAULT_EXPIRE, TimeUnit.SECONDS);
    }
    
    public void set(String key, String value, long expire) {
        redisTemplate.opsForValue().set(key, value, expire, TimeUnit.SECONDS);
    }
    
    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }
    
    public <T> void setObject(String key, T obj, long expire) {
        String json = JSON.toJSONString(obj);
        redisTemplate.opsForValue().set(key, json, expire, TimeUnit.SECONDS);
    }
    
    public <T> T getObject(String key, Class<T> clazz) {
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            return null;
        }
        return JSON.parseObject(json, clazz);
    }
    
    public void delete(String key) {
        redisTemplate.delete(key);
    }
    
    public void deletePattern(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (!CollectionUtils.isEmpty(keys)) {
            redisTemplate.delete(keys);
        }
    }
    
    /**
     * 实现SETNX（SET if Not eXists）用于分布式锁
     */
    public boolean setIfAbsent(String key, String value, long expire) {
        Boolean result = redisTemplate.opsForValue()
            .setIfAbsent(key, value, expire, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(result);
    }
}
```

### 3.3 Spring Cache注解

```java
@Configuration
@EnableCaching
public class SpringCacheConfig {
    
    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(
                RedisSerializationContext.SerializationPair
                    .fromSerializer(new GenericJackson2JsonRedisSerializer()))
            .disableCachingNullValues();
        
        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .transactionAware()
            .build();
    }
}

@Service
public class ProductService {
    
    @Cacheable(value = "product", key = "#id")
    public Product getProduct(Long id) {
        return productMapper.selectById(id);
    }
    
    @CachePut(value = "product", key = "#product.id")
    public Product updateProduct(Product product) {
        productMapper.update(product);
        return product;
    }
    
    @CacheEvict(value = "product", key = "#id")
    public void deleteProduct(Long id) {
        productMapper.deleteById(id);
    }
    
    @Caching(cacheable = {
        @Cacheable(value = "product", key = "#categoryId + '_' + #page")
    })
    public List<Product> listProducts(Long categoryId, int page) {
        return productMapper.selectByCategory(categoryId, page);
    }
}
```

---

## 4. 缓存问题解决方案

### 4.1 缓存穿透

**问题：** 查询不存在的数据，每次都打到数据库

**解决方案：**
```java
@Service
public class CachePenetrationSolution {
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    /**
     * 方案1：缓存空对象
     */
    public User getUserWithNullCache(String userId) {
        String key = "user:" + userId;
        String value = redisTemplate.opsForValue().get(key);
        
        if (value != null) {
            // 空值标记
            if ("NULL".equals(value)) {
                return null;
            }
            return JSON.parseObject(value, User.class);
        }
        
        User user = userMapper.selectById(userId);
        if (user != null) {
            redisTemplate.opsForValue().set(key, JSON.toJSONString(user), 600, TimeUnit.SECONDS);
        } else {
            // 缓存空值，设置较短的过期时间
            redisTemplate.opsForValue().set(key, "NULL", 60, TimeUnit.SECONDS);
        }
        return user;
    }
    
    /**
     * 方案2：布隆过滤器
     */
    @Autowired
    private RBloomFilter<String> userBloomFilter;
    
    public User getUserWithBloomFilter(String userId) {
        // 先查布隆过滤器
        if (!userBloomFilter.contains(userId)) {
            return null;  // 一定不存在
        }
        
        // 可能存在，查缓存和数据库
        return getUserFromCache(userId);
    }
    
    public void initBloomFilter() {
        // 初始化时加载所有用户ID
        List<String> userIds = userMapper.selectAllIds();
        userIds.forEach(userBloomFilter::add);
    }
}
```

### 4.2 缓存击穿

**问题：** 热点key过期，大量请求同时打到数据库

**解决方案：**
```java
@Service
public class CacheBreakdownSolution {
    
    @Autowired
    private RedissonClient redissonClient;
    
    /**
     * 方案1：互斥锁
     */
    public User getUserWithMutex(String userId) {
        String key = "user:" + userId;
        User user = getFromCache(key);
        
        if (user != null) {
            return user;
        }
        
        // 获取分布式锁
        RLock lock = redissonClient.getLock("lock:user:" + userId);
        try {
            boolean locked = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (!locked) {
                // 获取锁失败，直接返回或重试
                return getFromCache(key);  // 可能其他线程已加载
            }
            
            // 双重检查
            user = getFromCache(key);
            if (user != null) {
                return user;
            }
            
            // 查数据库并缓存
            user = userMapper.selectById(userId);
            if (user != null) {
                setToCache(key, user, 600);
            }
            return user;
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
    
    /**
     * 方案2：逻辑过期（永不过期）
     */
    public User getUserWithLogicalExpire(String userId) {
        String key = "user:" + userId;
        String json = redisTemplate.opsForValue().get(key);
        
        if (json == null) {
            return null;
        }
        
        RedisData redisData = JSON.parseObject(json, RedisData.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        
        // 未过期，直接返回
        if (expireTime.isAfter(LocalDateTime.now())) {
            return JSON.parseObject(redisData.getData(), User.class);
        }
        
        // 已过期，尝试获取锁重建缓存
        if (tryLock("lock:user:" + userId)) {
            // 开启独立线程重建缓存
            CACHE_REBUILD_EXECUTOR.submit(() -> {
                try {
                    User user = userMapper.selectById(userId);
                    saveWithLogicalExpire(key, user, 10);
                } finally {
                    unlock("lock:user:" + userId);
                }
            });
        }
        
        // 返回过期数据
        return JSON.parseObject(redisData.getData(), User.class);
    }
    
    @Data
    public static class RedisData {
        private LocalDateTime expireTime;
        private String data;
    }
}
```

### 4.3 缓存雪崩

**问题：** 大量key同时过期，数据库压力激增

**解决方案：**
```java
@Service
public class CacheAvalancheSolution {
    
    /**
     * 方案1：过期时间加随机值
     */
    public void setWithRandomExpire(String key, Object value, long baseExpire) {
        // 基础过期时间 + 随机0-5分钟
        long expire = baseExpire + RandomUtil.randomInt(0, 300);
        redisTemplate.opsForValue().set(key, JSON.toJSONString(value), expire, TimeUnit.SECONDS);
    }
    
    /**
     * 方案2：多级缓存
     */
    @Autowired
    private Cache<String, Object> localCache;
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    public Object getWithMultiLevel(String key) {
        // L1: 本地缓存
        Object value = localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // L2: Redis
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            value = JSON.parse(json);
            localCache.put(key, value);
            return value;
        }
        
        // L3: 数据库
        value = loadFromDatabase(key);
        if (value != null) {
            redisTemplate.opsForValue().set(key, JSON.toJSONString(value), 600, TimeUnit.SECONDS);
            localCache.put(key, value);
        }
        return value;
    }
    
    /**
     * 方案3：熔断降级
     */
    @CircuitBreaker(name = "cache", fallbackMethod = "fallback")
    public Object getWithCircuitBreaker(String key) {
        return getWithMultiLevel(key);
    }
    
    public Object fallback(String key, Exception ex) {
        // 降级：只查本地缓存或返回默认值
        return localCache.getIfPresent(key);
    }
}
```

---

## 5. 多级缓存架构

### 5.1 架构设计

```
┌─────────────────────────────────────────────────────────┐
│                       客户端请求                          │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│  L1: Caffeine本地缓存（进程内）                           │
│  - 访问速度：纳秒级                                       │
│  - 容量：1000-10000                                       │
│  - 过期：分钟级                                           │
└─────────────────────────────────────────────────────────┘
                          │ miss
                          ▼
┌─────────────────────────────────────────────────────────┐
│  L2: Redis分布式缓存（进程间）                            │
│  - 访问速度：毫秒级                                       │
│  - 容量：百万级                                           │
│  - 过期：小时级                                           │
└─────────────────────────────────────────────────────────┘
                          │ miss
                          ▼
┌─────────────────────────────────────────────────────────┐
│  L3: 数据库                                               │
│  - 访问速度：几十毫秒                                     │
│  - 容量：无限制                                           │
│  - 持久化存储                                             │
└─────────────────────────────────────────────────────────┘
```

### 5.2 多级缓存实现

```java
@Component
public class MultiLevelCache {
    
    @Autowired
    private Cache<String, Object> localCache;  // Caffeine
    
    @Autowired
    private StringRedisTemplate redisTemplate;
    
    @Autowired
    private RedissonClient redissonClient;
    
    private static final String CACHE_NULL = "NULL";
    
    /**
     * 读取数据
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> clazz, CacheLoader<T> loader) {
        // L1: 本地缓存
        T value = (T) localCache.getIfPresent(key);
        if (value != null) {
            return value;
        }
        
        // L2: Redis
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            if (CACHE_NULL.equals(json)) {
                return null;
            }
            value = JSON.parseObject(json, clazz);
            localCache.put(key, value);
            return value;
        }
        
        // L3: 数据库（加分布式锁防止击穿）
        RLock lock = redissonClient.getLock("lock:" + key);
        try {
            boolean locked = lock.tryLock(100, 10, TimeUnit.SECONDS);
            if (locked) {
                // 双重检查
                json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    return JSON.parseObject(json, clazz);
                }
                
                // 加载数据
                value = loader.load();
                if (value != null) {
                    set(key, value, 600);
                } else {
                    // 缓存空值
                    redisTemplate.opsForValue().set(key, CACHE_NULL, 60, TimeUnit.SECONDS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
        
        return value;
    }
    
    /**
     * 写入数据
     */
    public void set(String key, Object value, long expireSeconds) {
        // 更新Redis
        redisTemplate.opsForValue().set(
            key, JSON.toJSONString(value), expireSeconds, TimeUnit.SECONDS);
        
        // 更新本地缓存
        localCache.put(key, value);
    }
    
    /**
     * 删除数据（保证一致性）
     */
    public void delete(String key) {
        // 先删除Redis
        redisTemplate.delete(key);
        
        // 再删除本地缓存
        localCache.invalidate(key);
        
        // 发送消息通知其他节点清除本地缓存
        redisTemplate.convertAndSend("cache:invalidation", key);
    }
    
    /**
     * 监听缓存失效消息
     */
    @RedisMessageListener(channel = "cache:invalidation")
    public void onInvalidation(String key) {
        localCache.invalidate(key);
    }
    
    @FunctionalInterface
    public interface CacheLoader<T> {
        T load();
    }
}
```

### 5.3 缓存一致性保障

```java
@Service
public class CacheConsistencyService {
    
    @Autowired
    private MultiLevelCache multiLevelCache;
    
    /**
     * Cache-Aside模式（旁路缓存）
     */
    public void updateWithCacheAside(Product product) {
        // 1. 更新数据库
        productMapper.update(product);
        
        // 2. 删除缓存
        multiLevelCache.delete("product:" + product.getId());
    }
    
    /**
     * Read-Through模式（读穿透）
     */
    public Product getWithReadThrough(Long productId) {
        return multiLevelCache.get(
            "product:" + productId, 
            Product.class,
            () -> productMapper.selectById(productId)
        );
    }
    
    /**
     * Write-Through模式（写穿透）
     */
    public void updateWithWriteThrough(Product product) {
        // 1. 更新数据库
        productMapper.update(product);
        
        // 2. 更新缓存
        multiLevelCache.set("product:" + product.getId(), product, 600);
    }
    
    /**
     * 延迟双删（最终一致性）
     */
    public void updateWithDoubleDelete(Product product) {
        String key = "product:" + product.getId();
        
        // 1. 删除缓存
        multiLevelCache.delete(key);
        
        // 2. 更新数据库
        productMapper.update(product);
        
        // 3. 延迟删除缓存（确保读请求已结束）
        CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)
            .execute(() -> multiLevelCache.delete(key));
    }
}
```

---

## 6. 缓存检查清单

### 6.1 设计检查
- [ ] 是否选择了合适的缓存类型（本地/分布式）？
- [ ] 过期时间设置是否合理？
- [ ] 是否考虑了缓存容量限制？
- [ ] 一致性策略是否明确？

### 6.2 问题防护
- [ ] 是否处理了缓存穿透？
- [ ] 是否处理了缓存击穿？
- [ ] 是否处理了缓存雪崩？
- [ ] 是否有降级方案？

### 6.3 监控检查
- [ ] 是否监控缓存命中率？
- [ ] 是否监控缓存异常？
- [ ] 是否监控缓存大小？

---

## 7. 相关代码示例

- [LocalCacheOptimization.java](../src/main/java/com/perf/sop/cache/local/LocalCacheOptimization.java)
- [DistributedCacheOptimization.java](../src/main/java/com/perf/sop/cache/distributed/DistributedCacheOptimization.java)
