# 数据库性能优化指南

## 目录
1. [连接池优化](#1-连接池优化)
2. [SQL优化](#2-sql优化)
3. [索引优化](#3-索引优化)
4. [分页优化](#4-分页优化)
5. [批量操作优化](#5-批量操作优化)
6. [读写分离](#6-读写分离)

---

## 1. 连接池优化

### 1.1 连接池对比

| 连接池 | 性能 | 功能 | 监控 | 推荐度 |
|--------|------|------|------|--------|
| HikariCP | ★★★★★ | ★★★★☆ | ★★★☆☆ | ⭐⭐⭐⭐⭐ |
| Druid | ★★★★☆ | ★★★★★ | ★★★★★ | ⭐⭐⭐⭐ |
| c3p0 | ★★☆☆☆ | ★★★☆☆ | ★★☆☆☆ | ⭐⭐ |
| DBCP2 | ★★★☆☆ | ★★★☆☆ | ★★★☆☆ | ⭐⭐⭐ |

### 1.2 HikariCP推荐配置

```yaml
# 核心参数计算公式：
# connections = ((core_count * 2) + effective_spindle_count)
# 示例：4核CPU，单磁盘服务器 = 4 * 2 + 1 = 9

spring.datasource.hikari:
  # 连接池大小
  maximum-pool-size: 10          # 最大连接数
  minimum-idle: 5                # 最小空闲连接数
  
  # 超时配置
  connection-timeout: 30000      # 获取连接等待超时：30秒
  idle-timeout: 600000           # 空闲连接超时：10分钟
  max-lifetime: 1800000          # 连接最大生命周期：30分钟
  
  # 性能优化
  data-source-properties:
    cachePrepStmts: true         # 启用预处理语句缓存
    prepStmtCacheSize: 250       # 预处理语句缓存大小
    prepStmtCacheSqlLimit: 2048  # 单条SQL缓存长度限制
    useServerPrepStmts: true     # 使用服务端预处理
    useLocalSessionState: true   # 使用本地会话状态
    rewriteBatchedStatements: true # 批量操作重写
    cacheResultSetMetadata: true # 缓存结果集元数据
    cacheServerConfiguration: true # 缓存服务端配置
    elideSetAutoCommits: true    # 优化autoCommit设置
    maintainTimeStats: false     # 关闭时间统计（提升性能）
  
  # 监控配置
  pool-name: HikariPool-Main
  register-mbeans: true          # 注册JMX MBean
  leak-detection-threshold: 60000 # 连接泄漏检测阈值：60秒
```

### 1.3 连接池监控指标

```java
@Component
public class ConnectionPoolMetrics {
    
    @Autowired
    private HikariDataSource dataSource;
    
    @Scheduled(fixedRate = 60000)
    public void reportMetrics() {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        
        int active = poolMXBean.getActiveConnections();
        int idle = poolMXBean.getIdleConnections();
        int total = poolMXBean.getTotalConnections();
        int waiting = poolMXBean.getThreadsAwaitingConnection();
        
        log.info("连接池状态 - 活跃: {}, 空闲: {}, 总连接: {}, 等待: {}",
                active, idle, total, waiting);
        
        // 告警检查
        if (waiting > 0) {
            log.warn("⚠️ 有线程在等待连接，可能需要增加连接池大小");
        }
        
        if (active >= total * 0.8) {
            log.warn("⚠️ 连接池使用率超过80%");
        }
    }
}
```

---

## 2. SQL优化

### 2.1 SELECT优化

```sql
-- ❌ 避免SELECT *
SELECT * FROM users WHERE status = 'ACTIVE';

-- ✅ 只查询需要的列
SELECT id, username, email FROM users WHERE status = 'ACTIVE';

-- ❌ 避免在WHERE中使用函数
SELECT * FROM users WHERE DATE(created_at) = '2024-01-01';

-- ✅ 改写为范围查询
SELECT * FROM users WHERE created_at >= '2024-01-01' 
                      AND created_at < '2024-01-02';
```

### 2.2 执行计划分析

```sql
-- 使用EXPLAIN分析SQL
EXPLAIN SELECT * FROM users WHERE email = 'test@example.com';

-- 输出字段说明
-- type: 访问类型（system > const > eq_ref > ref > range > index > ALL）
-- key: 实际使用的索引
-- rows: 预估扫描行数
-- Extra: 额外信息
```

### 2.3 JOIN优化

```sql
-- ✅ 小表驱动大表
-- 假设users有1000行，orders有1000000行
SELECT o.* 
FROM users u
INNER JOIN orders o ON u.id = o.user_id
WHERE u.status = 'ACTIVE';

-- ✅ 确保JOIN条件有索引
CREATE INDEX idx_orders_user_id ON orders(user_id);

-- ❌ 避免大IN查询（数量>1000时性能差）
SELECT * FROM orders WHERE user_id IN (?, ?, ?, ...);  -- 大量ID

-- ✅ 使用临时表替代
CREATE TEMPORARY TABLE tmp_user_ids (user_id BIGINT PRIMARY KEY);
INSERT INTO tmp_user_ids VALUES (...);
SELECT o.* FROM orders o 
INNER JOIN tmp_user_ids t ON o.user_id = t.user_id;
```

---

## 3. 索引优化

### 3.1 索引设计原则

**适合建索引的列：**
- WHERE、JOIN、ORDER BY、GROUP BY中的列
- 区分度高的列（cardinality高）
- 外键列

**不适合建索引的列：**
- 数据量小的表（<1000行）
- 频繁更新的列
- 区分度低的列（如性别）
- 大字段（TEXT、BLOB）

### 3.2 联合索引

```sql
-- 最左前缀原则
CREATE INDEX idx_name_age ON users(name, age);

-- ✅ 可以使用索引
SELECT * FROM users WHERE name = '张三';
SELECT * FROM users WHERE name = '张三' AND age = 20;

-- ❌ 无法使用索引（缺少最左列）
SELECT * FROM users WHERE age = 20;
```

### 3.3 索引失效场景

```sql
-- 1. 在索引列上使用函数
SELECT * FROM users WHERE MD5(email) = ?;  -- 索引失效

-- 2. 隐式类型转换
SELECT * FROM users WHERE mobile = 13800138000;  -- mobile是VARCHAR

-- 3. LIKE以通配符开头
SELECT * FROM users WHERE name LIKE '%三%';  -- 索引失效

-- 4. OR条件未全部使用索引
SELECT * FROM users WHERE name = ? OR email = ?;  -- email无索引则失效

-- 5. 使用NOT、<>、IS NOT NULL
SELECT * FROM users WHERE status <> 'DELETED';
```

---

## 4. 分页优化

### 4.1 深分页问题

```sql
-- ❌ 深分页性能差（LIMIT 1000000, 10）
-- 需要扫描1000010行，返回最后10行
SELECT * FROM users ORDER BY id LIMIT 1000000, 10;

-- 解决方案1：使用覆盖索引+子查询
SELECT u.* FROM users u
INNER JOIN (
    SELECT id FROM users ORDER BY id LIMIT 1000000, 10
) tmp ON u.id = tmp.id;

-- 解决方案2：使用游标/书签分页（推荐）
-- 上一页最后一条数据的id = 1234567
SELECT * FROM users 
WHERE id > 1234567 
ORDER BY id 
LIMIT 10;
```

### 4.2 分页优化实践

```java
// ✅ 游标分页实现
public class CursorPage<T> {
    private List<T> data;
    private String nextCursor;  // 下一页游标
    private boolean hasMore;
}

public CursorPage<User> getUsers(String cursor, int pageSize) {
    // 解码cursor获取lastId
    Long lastId = decodeCursor(cursor);
    
    // 使用游标查询
    List<User> users = userMapper.selectByCursor(lastId, pageSize + 1);
    
    boolean hasMore = users.size() > pageSize;
    if (hasMore) {
        users = users.subList(0, pageSize);
    }
    
    // 生成下一页cursor
    String nextCursor = hasMore ? 
        encodeCursor(users.get(users.size() - 1).getId()) : null;
    
    return new CursorPage<>(users, nextCursor, hasMore);
}
```

---

## 5. 批量操作优化

### 5.1 批量插入

```java
// ❌ 逐条插入（性能极差）
for (User user : users) {
    userMapper.insert(user);  // 每次网络往返 + 事务日志
}

// ✅ 批量插入
@Insert("<script>" +
    "INSERT INTO users (name, email) VALUES " +
    "<foreach collection='list' item='item' separator=','>" +
    "(#{item.name}, #{item.email})" +
    "</foreach>" +
    "</script>")
void batchInsert(@Param("list") List<User> users);

// ✅ JDBC批量操作
String sql = "INSERT INTO users (name, email) VALUES (?, ?)";
try (PreparedStatement stmt = conn.prepareStatement(sql)) {
    for (int i = 0; i < users.size(); i++) {
        stmt.setString(1, users.get(i).getName());
        stmt.setString(2, users.get(i).getEmail());
        stmt.addBatch();
        
        if (i % 1000 == 0) {
            stmt.executeBatch();  // 每1000条执行一次
        }
    }
    stmt.executeBatch();
}
```

### 5.2 批量更新

```sql
-- ✅ 使用CASE WHEN批量更新
UPDATE users 
SET status = CASE id
    WHEN 1 THEN 'ACTIVE'
    WHEN 2 THEN 'INACTIVE'
    WHEN 3 THEN 'PENDING'
END
WHERE id IN (1, 2, 3);

-- ✅ 使用INSERT ... ON DUPLICATE KEY UPDATE
INSERT INTO users (id, name, status) 
VALUES (1, '张三', 'ACTIVE'),
       (2, '李四', 'INACTIVE')
ON DUPLICATE KEY UPDATE 
    name = VALUES(name),
    status = VALUES(status);
```

---

## 6. 读写分离

### 6.1 数据源配置

```java
@Configuration
public class DataSourceConfig {
    
    @Bean
    @Primary
    public DataSource routingDataSource() {
        DynamicRoutingDataSource routingDataSource = new DynamicRoutingDataSource();
        
        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDataSource());
        targetDataSources.put("slave1", slave1DataSource());
        targetDataSources.put("slave2", slave2DataSource());
        
        routingDataSource.setTargetDataSources(targetDataSources);
        routingDataSource.setDefaultTargetDataSource(masterDataSource());
        
        return routingDataSource;
    }
}

// 动态数据源切换
public class DataSourceContextHolder {
    private static final ThreadLocal<String> contextHolder = new ThreadLocal<>();
    
    public static void setDataSource(String dataSource) {
        contextHolder.set(dataSource);
    }
    
    public static String getDataSource() {
        return contextHolder.get();
    }
    
    public static void clear() {
        contextHolder.remove();
    }
}
```

### 6.2 读写分离注解

```java
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ReadOnly {
}

@Aspect
@Component
public class ReadOnlyAspect {
    
    @Around("@annotation(readOnly)")
    public Object around(ProceedingJoinPoint point, ReadOnly readOnly) throws Throwable {
        try {
            // 随机选择从库
            int slaveIndex = ThreadLocalRandom.current().nextInt(2) + 1;
            DataSourceContextHolder.setDataSource("slave" + slaveIndex);
            return point.proceed();
        } finally {
            DataSourceContextHolder.clear();
        }
    }
}

// 使用示例
@Service
public class UserService {
    
    @ReadOnly
    public User getUser(Long id) {
        return userMapper.selectById(id);  // 走从库
    }
    
    public void updateUser(User user) {
        userMapper.update(user);  // 走主库
    }
}
```

---

## 7. 相关代码示例

- [ConnectionPoolOptimization.java](../src/main/java/com/perf/sop/database/connection/ConnectionPoolOptimization.java)
- [SqlOptimization.java](../src/main/java/com/perf/sop/database/sql/SqlOptimization.java)
