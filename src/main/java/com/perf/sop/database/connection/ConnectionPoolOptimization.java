/**
 * 数据库连接池优化配置
 * 
 * 【SOP核心要点】
 * 1. 选择合适的连接池（HikariCP推荐）
 * 2. 根据业务特点配置连接池参数
 * 3. 监控连接池状态，及时发现连接泄漏
 * 4. 配置合理的超时时间
 * 5. 使用连接池监控和告警
 * 
 * 【连接池对比】
 * 
 * ┌──────────────┬───────────┬───────────┬────────────┐
 * │ 连接池        │ 性能      │ 功能      │ 推荐度      │
 * ├──────────────┼───────────┼───────────┼────────────┤
 * │ HikariCP     │ ★★★★★    │ ★★★★☆    │ ⭐⭐⭐⭐⭐   │
 * │ Druid        │ ★★★★☆    │ ★★★★★    │ ⭐⭐⭐⭐    │
 * │ c3p0         │ ★★☆☆☆    │ ★★★☆☆    │ ⭐⭐       │
 * │ DBCP2        │ ★★★☆☆    │ ★★★☆☆    │ ⭐⭐⭐     │
 * └──────────────┴───────────┴───────────┴────────────┘
 * 
 * 【HikariCP推荐配置】
 * 
 * 核心参数计算公式：
 * connections = ((core_count * 2) + effective_spindle_count)
 * 
 * 示例：4核CPU，单磁盘服务器
 * - 核心连接数：4 * 2 + 1 = 9
 * - 最大连接数：10-20（根据并发量调整）
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.database.connection;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;

import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.TimeUnit;

public class ConnectionPoolOptimization {

    /**
     * ✅ 创建优化的HikariCP连接池
     * 
     * 生产环境推荐配置
     */
    public static DataSource createOptimizedDataSource() {
        HikariConfig config = new HikariConfig();
        
        // ============ 基础配置 ============
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false&serverTimezone=UTC");
        config.setUsername("username");
        config.setPassword("password");
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");
        
        // ============ 连接池大小配置 ============
        // 核心连接数 = (CPU核数 * 2) + 磁盘数
        // 4核CPU、单磁盘服务器：4 * 2 + 1 = 9
        config.setMaximumPoolSize(10);        // 最大连接数（根据并发量调整）
        config.setMinimumIdle(5);             // 最小空闲连接数
        // 注意：HikariCP不建议设置连接数过多
        // 过多连接会增加数据库负载，降低整体性能
        
        // ============ 连接超时配置 ============
        config.setConnectionTimeout(30000);    // 获取连接等待超时：30秒
        config.setIdleTimeout(600000);         // 空闲连接超时：10分钟
        config.setMaxLifetime(1800000);        // 连接最大生命周期：30分钟
        // 注意：maxLifetime应小于数据库wait_timeout
        
        // ============ 连接测试配置 ============
        config.setConnectionTestQuery("SELECT 1");  // 连接测试SQL（MySQL）
        // 或使用JDBC4驱动自动检测：
        // config.setConnectionTestQuery(null);
        
        // ============ 性能优化配置 ============
        config.addDataSourceProperty("cachePrepStmts", "true");           // 启用预处理语句缓存
        config.addDataSourceProperty("prepStmtCacheSize", "250");         // 预处理语句缓存大小
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");    // 单条SQL缓存长度限制
        config.addDataSourceProperty("useServerPrepStmts", "true");       // 使用服务端预处理
        config.addDataSourceProperty("useLocalSessionState", "true");     // 使用本地会话状态
        config.addDataSourceProperty("rewriteBatchedStatements", "true"); // 批量操作重写
        config.addDataSourceProperty("cacheResultSetMetadata", "true");   // 缓存结果集元数据
        config.addDataSourceProperty("cacheServerConfiguration", "true"); // 缓存服务端配置
        config.addDataSourceProperty("elideSetAutoCommits", "true");      // 优化autoCommit设置
        config.addDataSourceProperty("maintainTimeStats", "false");       // 关闭时间统计（提升性能）
        
        // ============ 监控配置 ============
        config.setPoolName("HikariPool-Main");  // 连接池名称（用于监控）
        config.setRegisterMbeans(true);          // 注册JMX MBean（用于监控）
        config.setMetricRegistry(null);          // 可以接入Micrometer等监控
        config.setHealthCheckRegistry(null);     // 健康检查注册
        
        // ============ 泄漏检测配置 ============
        config.setLeakDetectionThreshold(60000); // 连接泄漏检测阈值：60秒
        // 如果连接被借出超过60秒未归还，记录警告日志
        
        return new HikariDataSource(config);
    }

    /**
     * 创建高并发场景连接池配置
     * 
     * 适用场景：
     * - 高并发读多写少
     * - 微服务架构
     * - 需要快速响应
     */
    public static DataSource createHighConcurrencyDataSource() {
        HikariConfig config = new HikariConfig();
        
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false");
        config.setUsername("username");
        config.setPassword("password");
        
        // 高并发配置
        config.setMaximumPoolSize(20);        // 最大连接数（根据DB能力调整）
        config.setMinimumIdle(10);            // 保持较多空闲连接，减少创建开销
        config.setConnectionTimeout(5000);    // 快速失败：5秒
        config.setIdleTimeout(300000);        // 5分钟
        config.setMaxLifetime(900000);        // 15分钟
        
        // 启用快速验证
        config.setValidationTimeout(3000);    // 验证超时：3秒
        
        // MySQL优化参数
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "500");      // 更大的缓存
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "4096");
        config.addDataSourceProperty("useServerPrepStmts", "true");
        
        return new HikariDataSource(config);
    }

    /**
     * 创建低延迟场景连接池配置
     * 
     * 适用场景：
     * - 金融交易系统
     * - 实时计算
     * - 对延迟敏感的应用
     */
    public static DataSource createLowLatencyDataSource() {
        HikariConfig config = new HikariConfig();
        
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mydb?useSSL=false");
        config.setUsername("username");
        config.setPassword("password");
        
        // 低延迟配置
        config.setMaximumPoolSize(5);         // 较少的连接，避免竞争
        config.setMinimumIdle(5);             // 保持固定连接数
        config.setConnectionTimeout(1000);    // 1秒快速失败
        config.setIdleTimeout(0);             // 永不回收空闲连接
        config.setMaxLifetime(0);             // 连接永不关闭
        
        // 预热连接池
        config.setInitializationFailTimeout(1000);
        
        return new HikariDataSource(config);
    }

    /**
     * ==================== 连接池监控 ====================
     */

    /**
     * 打印连接池状态
     * 
     * 应在监控系统中定期调用，用于：
     * 1. 监控连接池健康状况
     * 2. 及时发现连接泄漏
     * 3. 调整连接池参数
     */
    public static void printPoolStatus(HikariDataSource dataSource) {
        HikariPoolMXBean poolMXBean = dataSource.getHikariPoolMXBean();
        
        System.out.println("========== 连接池状态 ==========");
        System.out.println("连接池名称: " + dataSource.getPoolName());
        System.out.println("活跃连接数: " + poolMXBean.getActiveConnections());
        System.out.println("空闲连接数: " + poolMXBean.getIdleConnections());
        System.out.println("等待线程数: " + poolMXBean.getThreadsAwaitingConnection());
        System.out.println("总连接数: " + poolMXBean.getTotalConnections());
        System.out.println("================================");
        
        // 告警条件
        int active = poolMXBean.getActiveConnections();
        int total = poolMXBean.getTotalConnections();
        int waiting = poolMXBean.getThreadsAwaitingConnection();
        
        if (waiting > 0) {
            System.err.println("⚠️ 警告: 有线程在等待连接，可能需要增加连接池大小");
        }
        
        if (active >= total * 0.8) {
            System.err.println("⚠️ 警告: 连接池使用率超过80%，请关注");
        }
    }

    /**
     * 连接池健康检查
     */
    public static boolean checkPoolHealth(HikariDataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            // 执行健康检查SQL
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT 1")) {
                return rs.next() && rs.getInt(1) == 1;
            }
        } catch (SQLException e) {
            System.err.println("连接池健康检查失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * ==================== 连接使用最佳实践 ====================
     */

    /**
     * ✅ 正确使用连接（try-with-resources）
     */
    public void properConnectionUsage(DataSource dataSource) {
        // ✅ 使用try-with-resources确保连接关闭
        // 即使在异常情况下也会自动关闭连接
        String sql = "SELECT id, name FROM users WHERE id = ?";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            
            stmt.setLong(1, 12345L);
            
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    long id = rs.getLong("id");
                    String name = rs.getString("name");
                    // 处理结果
                }
            }
        } catch (SQLException e) {
            // 日志记录异常
            e.printStackTrace();
        }
        // 连接、语句和结果集自动关闭
    }

    /**
     * ❌ 反例：连接泄漏
     */
    public void badConnectionLeak(DataSource dataSource) {
        try {
            Connection conn = dataSource.getConnection();  // ❌ 未在finally中关闭
            PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                // 处理结果
            }
            // 如果这里发生异常，连接永远不会关闭！
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * ✅ 批量操作优化
     */
    public void batchInsertOptimization(Connection conn) throws SQLException {
        String sql = "INSERT INTO orders (user_id, amount, status) VALUES (?, ?, ?)";
        
        // 关闭自动提交
        conn.setAutoCommit(false);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int batchSize = 1000;  // 每批1000条
            int count = 0;
            
            for (int i = 0; i < 10000; i++) {
                stmt.setLong(1, i);
                stmt.setBigDecimal(2, new java.math.BigDecimal("99.99"));
                stmt.setString(3, "PAID");
                stmt.addBatch();
                
                if (++count % batchSize == 0) {
                    stmt.executeBatch();     // 执行批量插入
                    conn.commit();           // 提交事务
                    stmt.clearBatch();       // 清空批处理
                }
            }
            
            // 处理剩余记录
            stmt.executeBatch();
            conn.commit();
            
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    /**
     * ✅ 读写分离配置示例
     */
    public static class ReadWriteSplittingConfig {
        
        /**
         * 主库（写操作）
         */
        public static DataSource createMasterDataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://master:3306/mydb");
            config.setMaximumPoolSize(10);  // 写库连接数较少
            // ... 其他配置
            return new HikariDataSource(config);
        }
        
        /**
         * 从库（读操作）
         */
        public static DataSource createSlaveDataSource() {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl("jdbc:mysql://slave:3306/mydb");
            config.setMaximumPoolSize(30);  // 读库连接数较多
            config.setReadOnly(true);        // 设置为只读
            // ... 其他配置
            return new HikariDataSource(config);
        }
    }

    /**
     * 主方法：演示连接池使用
     */
    public static void main(String[] args) throws Exception {
        // 创建连接池
        HikariDataSource dataSource = (HikariDataSource) createOptimizedDataSource();
        
        try {
            // 打印初始状态
            printPoolStatus(dataSource);
            
            // 执行一些操作
            ConnectionPoolOptimization optimizer = new ConnectionPoolOptimization();
            optimizer.properConnectionUsage(dataSource);
            
            // 再次打印状态
            Thread.sleep(1000);
            printPoolStatus(dataSource);
            
            // 健康检查
            boolean healthy = checkPoolHealth(dataSource);
            System.out.println("连接池健康: " + healthy);
            
        } finally {
            // 关闭连接池
            dataSource.close();
        }
    }
}