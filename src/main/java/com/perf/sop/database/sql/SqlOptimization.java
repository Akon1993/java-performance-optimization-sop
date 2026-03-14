/**
 * SQL语句性能优化
 * 
 * 【SOP核心要点】
 * 1. 使用EXPLAIN分析执行计划
 * 2. 合理使用索引（避免全表扫描）
 * 3. 避免SELECT *，只查询需要的列
 * 4. 使用LIMIT分页，避免深分页问题
 * 5. 批量操作替代循环单条操作
 * 6. 避免在WHERE中使用函数或隐式转换
 * 7. 优化JOIN操作，小表驱动大表
 * 
 * 【索引使用原则】
 * 
 * 1. 适合建索引的列：
 *    - WHERE、JOIN、ORDER BY、GROUP BY中的列
 *    - 区分度高的列（cardinality高）
 *    - 外键列
 * 
 * 2. 不适合建索引的列：
 *    - 数据量小的表（<1000行）
 *    - 频繁更新的列
 *    - 区分度低的列（如性别）
 *    - 大字段（TEXT、BLOB）
 * 
 * 3. 联合索引原则：
 *    - 最左前缀原则
 *    - 将区分度高的列放前面
 *    - 避免冗余索引
 * 
 * 【执行计划分析】
 * 
 * EXPLAIN输出字段说明：
 * - type: 访问类型（system > const > eq_ref > ref > range > index > ALL）
 * - key: 实际使用的索引
 * - rows: 预估扫描行数
 * - Extra: 额外信息
 *   - Using index: 覆盖索引
 *   - Using where: 使用WHERE过滤
 *   - Using filesort: 需要排序（性能差）
 *   - Using temporary: 使用临时表（性能差）
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.database.sql;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class SqlOptimization {

    /**
     * ==================== 查询优化 ====================
     */

    /**
     * ❌ 反例：SELECT * 查询
     * 
     * 问题：
     * 1. 增加网络IO开销
     * 2. 增加内存占用
     * 3. 可能触发回表查询
     * 4. 破坏封装性
     */
    public void badSelectAll(Connection conn) throws SQLException {
        String sql = "SELECT * FROM users WHERE status = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "ACTIVE");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                // 即使只需要id和name，也拉取了所有字段
                long id = rs.getLong("id");
                String name = rs.getString("name");
                // ... 还有很多不需要的字段被读取
            }
        }
    }

    /**
     * ✅ 正例：只查询需要的列
     */
    public void goodSelectSpecificColumns(Connection conn) throws SQLException {
        // ✅ 明确指定需要的列
        String sql = "SELECT id, username, email FROM users WHERE status = ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "ACTIVE");
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                long id = rs.getLong("id");
                String username = rs.getString("username");
                String email = rs.getString("email");
                // 只处理了需要的字段
            }
        }
    }

    /**
     * ==================== 索引优化 ====================
     */

    /**
     * ❌ 反例：索引失效的情况
     */
    public void badIndexUsage(Connection conn) throws SQLException {
        // 假设有索引：CREATE INDEX idx_mobile ON users(mobile);
        
        // ❌ 1. 在WHERE中使用函数
        String sql1 = "SELECT * FROM users WHERE MD5(mobile) = ?";
        // 索引失效！应该在应用层计算MD5
        
        // ❌ 2. 隐式类型转换
        String sql2 = "SELECT * FROM users WHERE mobile = 13800138000";
        // mobile是VARCHAR类型，传入INT导致类型转换，索引失效
        
        // ❌ 3. LIKE以通配符开头
        String sql3 = "SELECT * FROM users WHERE mobile LIKE '%3800%'";
        // 索引失效！无法使用前缀匹配
        
        // ❌ 4. OR条件未全部使用索引
        String sql4 = "SELECT * FROM users WHERE mobile = ? OR email = ?";
        // 如果email没有索引，整个查询可能全表扫描
        
        // ❌ 5. 使用NOT、<>、IS NOT NULL
        String sql5 = "SELECT * FROM users WHERE status <> 'DELETED'";
        // 虽然MySQL 8.0有改进，但仍可能影响性能
    }

    /**
     * ✅ 正例：正确使用索引
     */
    public void goodIndexUsage(Connection conn) throws SQLException {
        // ✅ 1. 避免在索引列上使用函数
        // 应用层计算好值再传入
        String mobileHash = "5f4dcc3b5aa765d61d8327deb882cf99"; // MD5计算结果
        String sql1 = "SELECT * FROM users WHERE mobile_hash = ?";
        
        // ✅ 2. 确保类型匹配
        String sql2 = "SELECT * FROM users WHERE mobile = '13800138000'";
        // 传入字符串，与列类型匹配
        
        // ✅ 3. LIKE使用前缀匹配
        String sql3 = "SELECT * FROM users WHERE mobile LIKE '13800%'";
        // ✅ 可以使用索引前缀匹配
        
        // ✅ 4. 使用UNION ALL替代OR
        String sql4 = "SELECT * FROM users WHERE mobile = ? " +
                     "UNION ALL " +  // 如果确定无重复，用UNION ALL更快
                     "SELECT * FROM users WHERE email = ?";
        
        // ✅ 5. 改写条件
        String sql5 = "SELECT * FROM users WHERE status IN ('ACTIVE', 'PENDING')";
        // 替代 status <> 'DELETED'
    }

    /**
     * ==================== 分页优化 ====================
     */

    /**
     * ❌ 反例：深分页问题
     * 
     * 问题：
     * - LIMIT 1000000, 10 需要先扫描1000010行
     * - 越往后越慢
     * - 时间复杂度O(n)
     */
    public List<User> badDeepPagination(Connection conn, int page, int pageSize) 
            throws SQLException {
        String sql = "SELECT * FROM users ORDER BY created_at DESC LIMIT ?, ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, (page - 1) * pageSize);  // 偏移量
            stmt.setInt(2, pageSize);
            
            ResultSet rs = stmt.executeQuery();
            return extractUsers(rs);
        }
    }

    /**
     * ✅ 方案1：使用覆盖索引+子查询
     */
    public List<User> goodPaginationWithCoveringIndex(Connection conn, 
            long lastId, int pageSize) throws SQLException {
        // 假设有索引：CREATE INDEX idx_created_id ON users(created_at, id);
        
        String sql = "SELECT u.* FROM users u " +
                    "INNER JOIN (" +
                    "  SELECT id FROM users " +
                    "  WHERE created_at <= (SELECT created_at FROM users WHERE id = ?) " +
                    "  AND id < ? " +
                    "  ORDER BY created_at DESC, id DESC " +
                    "  LIMIT ?" +
                    ") tmp ON u.id = tmp.id";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, lastId);
            stmt.setLong(2, lastId);
            stmt.setInt(3, pageSize);
            
            ResultSet rs = stmt.executeQuery();
            return extractUsers(rs);
        }
    }

    /**
     * ✅ 方案2：使用游标/书签分页（推荐）
     */
    public List<User> goodPaginationWithCursor(Connection conn, 
            String cursor, int pageSize) throws SQLException {
        // cursor格式: "created_at:id" 的Base64编码
        String[] parts = decodeCursor(cursor);
        String lastCreatedAt = parts[0];
        long lastId = Long.parseLong(parts[1]);
        
        String sql = "SELECT * FROM users " +
                    "WHERE (created_at < ? OR (created_at = ? AND id < ?)) " +
                    "ORDER BY created_at DESC, id DESC " +
                    "LIMIT ?";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, lastCreatedAt);
            stmt.setString(2, lastCreatedAt);
            stmt.setLong(3, lastId);
            stmt.setInt(4, pageSize);
            
            ResultSet rs = stmt.executeQuery();
            List<User> users = extractUsers(rs);
            
            // 生成下一页cursor
            if (!users.isEmpty()) {
                User lastUser = users.get(users.size() - 1);
                String nextCursor = encodeCursor(lastUser.getCreatedAt(), lastUser.getId());
                // 返回给前端
            }
            
            return users;
        }
    }

    /**
     * ✅ 方案3：使用延迟关联（Deferred Join）
     */
    public List<User> goodPaginationWithDeferredJoin(Connection conn, 
            int offset, int pageSize) throws SQLException {
        // 先查询主键
        String idSql = "SELECT id FROM users ORDER BY created_at DESC LIMIT ?, ?";
        
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(idSql)) {
            stmt.setInt(1, offset);
            stmt.setInt(2, pageSize);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                ids.add(rs.getLong("id"));
            }
        }
        
        if (ids.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 再查询完整数据
        String dataSql = "SELECT * FROM users WHERE id IN (" +
                        String.join(",", java.util.Collections.nCopies(ids.size(), "?")) +
                        ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(dataSql)) {
            for (int i = 0; i < ids.size(); i++) {
                stmt.setLong(i + 1, ids.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            return extractUsers(rs);
        }
    }

    /**
     * ==================== JOIN优化 ====================
     */

    /**
     * ❌ 反例：IN查询（数据量大时性能差）
     */
    public List<Order> badInQuery(Connection conn, List<Long> userIds) 
            throws SQLException {
        // 当userIds数量很大时（>1000），性能急剧下降
        String placeholders = String.join(",", 
            java.util.Collections.nCopies(userIds.size(), "?"));
        String sql = "SELECT * FROM orders WHERE user_id IN (" + placeholders + ")";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (int i = 0; i < userIds.size(); i++) {
                stmt.setLong(i + 1, userIds.get(i));
            }
            ResultSet rs = stmt.executeQuery();
            return extractOrders(rs);
        }
    }

    /**
     * ✅ 正例：使用JOIN替代大IN查询
     */
    public List<Order> goodJoinInsteadOfIn(Connection conn, List<Long> userIds) 
            throws SQLException {
        // 创建临时表或 VALUES 子句
        StringBuilder valuesClause = new StringBuilder();
        for (int i = 0; i < userIds.size(); i++) {
            if (i > 0) valuesClause.append(", ");
            valuesClause.append("(").append(userIds.get(i)).append(")");
        }
        
        String sql = "SELECT o.* FROM orders o " +
                    "INNER JOIN (VALUES " + valuesClause.toString() + ") AS t(user_id) " +
                    "ON o.user_id = t.user_id";
        
        // 或者使用临时表
        // CREATE TEMPORARY TABLE tmp_user_ids (user_id BIGINT PRIMARY KEY);
        // INSERT INTO tmp_user_ids VALUES ...
        // SELECT o.* FROM orders o INNER JOIN tmp_user_ids t ON o.user_id = t.user_id;
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            return extractOrders(rs);
        }
    }

    /**
     * ✅ 小表驱动大表原则
     */
    public void goodJoinOrder(Connection conn) throws SQLException {
        // 假设：users表1000行，orders表1000000行
        // users是小表，orders是大表
        
        // ✅ 小表（users）驱动大表（orders）
        String goodSql = "SELECT o.* FROM users u " +
                        "INNER JOIN orders o ON u.id = o.user_id " +
                        "WHERE u.status = 'ACTIVE'";
        
        // ❌ 避免大表驱动
        // SELECT o.* FROM orders o
        // INNER JOIN users u ON o.user_id = u.id  -- 如果优化器选择错误连接顺序
    }

    /**
     * ==================== 写入优化 ====================
     */

    /**
     * ❌ 反例：循环单条插入
     */
    public void badLoopInsert(Connection conn, List<Order> orders) 
            throws SQLException {
        String sql = "INSERT INTO orders (user_id, amount, status) VALUES (?, ?, ?)";
        
        for (Order order : orders) {
            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setLong(1, order.getUserId());
                stmt.setBigDecimal(2, order.getAmount());
                stmt.setString(3, order.getStatus());
                stmt.executeUpdate();  // ❌ 每次网络往返 + 事务日志
            }
        }
    }

    /**
     * ✅ 正例：批量插入
     */
    public void goodBatchInsert(Connection conn, List<Order> orders) 
            throws SQLException {
        String sql = "INSERT INTO orders (user_id, amount, status) VALUES (?, ?, ?)";
        
        conn.setAutoCommit(false);
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            int batchSize = 1000;
            int count = 0;
            
            for (Order order : orders) {
                stmt.setLong(1, order.getUserId());
                stmt.setBigDecimal(2, order.getAmount());
                stmt.setString(3, order.getStatus());
                stmt.addBatch();
                
                if (++count % batchSize == 0) {
                    stmt.executeBatch();   // ✅ 批量执行
                    conn.commit();          // ✅ 批量提交
                }
            }
            
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
     * ✅ 使用INSERT IGNORE / ON DUPLICATE KEY UPDATE
     */
    public void goodUpsert(Connection conn, List<User> users) 
            throws SQLException {
        // MySQL批量upsert
        String sql = "INSERT INTO users (id, username, email) VALUES (?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE " +
                    "username = VALUES(username), " +
                    "email = VALUES(email)";
        
        // 或者使用REPLACE INTO（注意：会先删除再插入）
        // String sql = "REPLACE INTO users (id, username, email) VALUES (?, ?, ?)";
        
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (User user : users) {
                stmt.setLong(1, user.getId());
                stmt.setString(2, user.getUsername());
                stmt.setString(3, user.getEmail());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }

    // ============= 辅助方法 =============

    private List<User> extractUsers(ResultSet rs) throws SQLException {
        List<User> users = new ArrayList<>();
        while (rs.next()) {
            User user = new User();
            user.setId(rs.getLong("id"));
            user.setUsername(rs.getString("username"));
            user.setEmail(rs.getString("email"));
            user.setCreatedAt(rs.getString("created_at"));
            users.add(user);
        }
        return users;
    }

    private List<Order> extractOrders(ResultSet rs) throws SQLException {
        List<Order> orders = new ArrayList<>();
        while (rs.next()) {
            Order order = new Order();
            order.setId(rs.getLong("id"));
            order.setUserId(rs.getLong("user_id"));
            order.setAmount(rs.getBigDecimal("amount"));
            order.setStatus(rs.getString("status"));
            orders.add(order);
        }
        return orders;
    }

    private String[] decodeCursor(String cursor) {
        // Base64解码逻辑
        return new String[]{"2024-01-01", "12345"};  // 示例
    }

    private String encodeCursor(String createdAt, long id) {
        // Base64编码逻辑
        return "cursor_string";  // 示例
    }

    // 简单实体类
    public static class User {
        private long id;
        private String username;
        private String email;
        private String createdAt;
        
        // Getters and Setters
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getCreatedAt() { return createdAt; }
        public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    }

    public static class Order {
        private long id;
        private long userId;
        private java.math.BigDecimal amount;
        private String status;
        
        // Getters and Setters
        public long getId() { return id; }
        public void setId(long id) { this.id = id; }
        public long getUserId() { return userId; }
        public void setUserId(long userId) { this.userId = userId; }
        public java.math.BigDecimal getAmount() { return amount; }
        public void setAmount(java.math.BigDecimal amount) { this.amount = amount; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
    }
}