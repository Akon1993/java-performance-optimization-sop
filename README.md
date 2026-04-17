# Java性能优化SOP项目

一个完整的Java性能优化标准操作流程（SOP）代码库，包含各类性能优化场景的最佳实践。

## 📋 项目概述

本项目旨在为Java开发者提供一套系统化的性能优化解决方案，包含：
- **代码示例**：每个优化点都配有可运行的代码示例
- **详细注释**：代码中包含详尽的原理说明和注意事项
- **基准测试**：使用JMH进行性能对比测试
- **配套文档**：完整的Markdown文档说明
- **检查工具**：自动化代码检查脚本

## 🎯 适用场景

- **新项目启动**：作为性能优化的代码规范参考
- **存量优化**：作为性能问题的排查和优化指南
- **团队培训**：作为性能优化知识的培训材料
- **面试准备**：作为Java性能调优知识点的复习资料

## 📁 项目结构

```
performance-optimization-sop/
├── README.md                           # 项目说明文档
├── docs/                               # 配套文档
│   ├── 00-performance-sop-workflow.md  # 性能优化SOP完整流程
│   ├── 01-code-optimization.md         # 代码层面优化指南
│   ├── 02-jvm-tuning.md                # JVM调优完整指南
│   ├── 03-database-optimization.md     # 数据库优化指南
│   ├── 04-concurrency-optimization.md  # 并发优化指南
│   └── 05-cache-strategy.md            # 缓存策略指南
├── scripts/                            # 工具脚本
│   └── performance-check.sh            # 性能检查脚本
├── pom.xml                             # Maven构建文件
├── .gitignore                          # Git忽略文件
└── src/
    ├── main/java/com/perf/sop/
    │   ├── code/                       # 代码层面优化
    │   │   ├── collection/             # 集合类优化
    │   │   ├── string/                 # 字符串处理优化
    │   │   └── loop/                   # 循环优化
    │   ├── jvm/                        # JVM调优
    │   │   ├── gc/                     # 垃圾回收优化
    │   │   └── memory/                 # 内存管理优化
    │   ├── database/                   # 数据库优化
    │   │   ├── connection/             # 连接池配置
    │   │   └── sql/                    # SQL优化
    │   ├── concurrency/                # 并发优化
    │   │   ├── threadpool/             # 线程池配置
    │   │   ├── lock/                   # 锁优化
    │   │   └── virtualthread/          # 虚拟线程（JDK 21+）
    │   └── cache/                      # 缓存策略
    │       ├── local/                  # 本地缓存
    │       └── distributed/            # 分布式缓存
    └── test/java/com/perf/sop/
        └── benchmark/                  # JMH基准测试
            ├── StringConcatBenchmark.java
            ├── CollectionBenchmark.java
            └── LockBenchmark.java
```

## 🚀 快速开始

### 环境要求
- **JDK**: 21或更高版本（LTS）- 本项目充分利用JDK 21特性
- **Maven**: 3.9或更高版本
- **IDE**: IntelliJ IDEA 2023.2+ / Eclipse 2023-09+ / VSCode

### 构建项目

```bash
# 克隆项目
git clone <repository-url>
cd performance-optimization-sop

# 编译项目
mvn clean compile

# 运行所有测试
mvn test

# 运行基准测试
mvn clean package exec:java -Pbenchmark
```

### 运行性能检查脚本

```bash
# 给脚本添加执行权限
chmod +x scripts/performance-check.sh

# 运行检查
./scripts/performance-check.sh

# 检查特定目录
./scripts/performance-check.sh /path/to/your/project
```

### 使用IDE打开

1. **IntelliJ IDEA**:
   - File → Open → 选择pom.xml文件
   - IDEA会自动导入Maven项目

2. **Eclipse**:
   - File → Import → Maven → Existing Maven Projects
   - 选择项目根目录

3. **VSCode**:
   - 安装"Extension Pack for Java"插件
   - File → Open Folder → 选择项目目录

## 📚 核心内容

### 1. 代码层面优化 (`com.perf.sop.code`)

| 模块 | 内容 | 关键优化点 |
|------|------|-----------|
| **集合类优化** | ArrayList vs LinkedList、HashMap优化、并发集合选择 | 预估容量、避免LinkedList索引访问 |
| **字符串优化** | StringBuilder vs StringBuffer、字符串分割优化 | 避免循环中使用+=拼接 |
| **循环优化** | 循环条件优化、对象创建优化、Stream API | 避免循环中创建对象、使用基本类型 |

### 2. JVM调优 (`com.perf.sop.jvm`)

| 模块 | 内容 | 关键优化点 |
|------|------|-----------|
| **内存优化** | JVM内存模型、对象内存布局、逃逸分析 | 合理设置堆内存、TLAB优化 |
| **GC优化** | GC算法选择、GC日志分析、内存泄漏检测 | 选择合适的GC算法、避免Full GC |

### 3. 数据库优化 (`com.perf.sop.database`)

| 模块 | 内容 | 关键优化点 |
|------|------|-----------|
| **连接池优化** | HikariCP配置、连接池监控、读写分离 | 合理设置连接池大小、避免连接泄漏 |
| **SQL优化** | 索引优化、分页优化、批量操作 | 避免SELECT *、处理深分页问题 |

### 4. 并发优化 (`com.perf.sop.concurrency`)

| 模块 | 内容 | 关键优化点 |
|------|------|-----------|
| **线程池优化** | 线程池参数计算、监控、优雅关闭 | 根据业务类型设置参数 |
| **锁优化** | synchronized vs ReentrantLock、读写锁、无锁编程 | 缩小锁粒度、使用原子类 |
| **虚拟线程** | JDK 21 Virtual Threads最佳实践 | IO密集型场景、避免synchronized |

### 5. 缓存策略 (`com.perf.sop.cache`)

| 模块 | 内容 | 关键优化点 |
|------|------|-----------|
| **本地缓存** | Caffeine配置、缓存策略、性能优化 | 选择合适的淘汰策略 |
| **分布式缓存** | Redis优化、缓存问题解决方案 | 处理穿透、击穿、雪崩 |

## 📖 配套文档

| 文档 | 说明 | 对应代码包 |
|------|------|-----------|
| [00-性能优化SOP流程.md](docs/00-性能优化SOP流程.md) | **性能优化SOP完整流程** | - |
| [01-代码层面优化.md](docs/01-代码层面优化.md) | 代码层面优化指南 | `com.perf.sop.code` |
| [02-JVM调优指南.md](docs/02-JVM调优指南.md) | JVM调优完整指南 | `com.perf.sop.jvm` |
| [03-数据库优化指南.md](docs/03-数据库优化指南.md) | 数据库优化指南 | `com.perf.sop.database` |
| [04-并发优化指南.md](docs/04-并发优化指南.md) | 并发优化指南 | `com.perf.sop.concurrency` |
| [05-缓存策略指南.md](docs/05-缓存策略指南.md) | 缓存策略指南 | `com.perf.sop.cache` |
| **[09-性能优化决策树.md](docs/09-性能优化决策树.md)** | **按症状快速决策指南** | - |

## 🧪 基准测试

本项目使用JMH（Java Microbenchmark Harness）进行性能测试。

### 运行基准测试

```bash
# 运行所有基准测试
mvn clean package exec:java -Pbenchmark

# 运行特定测试类
java -jar target/perf-sop-benchmarks.jar StringConcatBenchmark
```

### 测试覆盖

| 测试类 | 测试内容 |
|--------|----------|
| `StringConcatBenchmark` | String拼接性能对比 |
| `CollectionBenchmark` | 集合遍历性能对比 |
| `LockBenchmark` | 锁性能对比 |

## 🔧 性能检查脚本

自动化检查Java代码中的常见性能问题：

### 检查项

- 🔴 **严重问题**：循环中String拼接、LinkedList索引访问、SimpleDateFormat线程安全问题
- 🟡 **警告**：集合未设置初始容量、自动装箱、连接泄漏风险
- 🔵 **建议**：锁粒度优化、魔法数字、日志规范

### 使用示例

```bash
$ ./scripts/performance-check.sh

========================================
  Java性能优化检查工具
  检查目录: .
========================================

检查: 循环中String拼接...
检查: ArrayList未设置初始容量...
...

========================================
检查完成！
========================================

检查结果摘要：

  🔴 严重问题: 3
  🟡 警告: 5
  🔵 建议: 2

  总计: 10 处需要关注

详细报告已生成: ./performance-check-report.md
```

## 🎓 使用建议

### 作为SOP使用

1. **问题发现**：通过监控工具（SkyWalking、Arthas）定位性能瓶颈
2. **根因分析**：根据问题类型查阅对应文档
3. **方案选择**：参考代码示例选择最优解决方案
4. **效果验证**：使用JMH进行优化前后的性能对比

### 代码审查检查清单

**代码层面**
- [ ] 集合类选择是否合理？（随机访问用ArrayList，频繁插入删除用LinkedList）
- [ ] 字符串拼接是否使用了StringBuilder？
- [ ] 循环内部是否有不必要的对象创建？
- [ ] 是否避免了自动装箱/拆箱？

**JVM层面**
- [ ] 堆内存设置是否合理？
- [ ] GC算法是否适合应用场景？
- [ ] 是否配置了OOM处理？

**数据库层面**
- [ ] 连接池配置是否合理？
- [ ] SQL是否有索引支持？
- [ ] 是否使用了批量操作？

**并发层面**
- [ ] 线程池参数是否根据业务场景调整？
- [ ] 锁粒度是否足够小？
- [ ] 是否使用了合适的并发容器？

**缓存层面**
- [ ] 缓存使用是否考虑了穿透/击穿/雪崩问题？
- [ ] 缓存一致性策略是否明确？

## ⚠️ 注意事项

1. **不要过早优化**
   - 先保证代码正确性，再进行性能优化
   - 通过profiling工具确认瓶颈后再优化

2. **基于数据优化**
   - 优化前一定要通过监控工具确认瓶颈
   - 使用JMH等工具进行量化对比

3. **测试验证**
   - 生产环境优化前必须在测试环境充分验证
   - 准备好回滚方案

4. **渐进式优化**
   - 一次只优化一个点
   - 便于回滚和问题定位

## 📊 性能优化决策树

```
性能问题
    │
    ├── CPU高
    │     ├── 火焰图分析
    │     ├── 优化算法
    │     └── 使用缓存
    │
    ├── 内存高
    │     ├── 堆转储分析
    │     ├── 修复内存泄漏
    │     └── 调整堆大小
    │
    ├── 响应慢
    │     ├── 数据库慢？→ SQL优化/索引
    │     ├── 外部调用慢？→ 异步/超时/熔断
    │     └── 计算慢？→ 算法优化/缓存
    │
    └── GC频繁
          ├── 增加堆内存
          ├── 调整新生代比例
          └── 选择合适的GC算法
```

## 📞 技术支持

- **GitHub Issues**: 提交问题和建议
- **Email**: perf-sop@example.com
- **Wiki**: 项目Wiki页面

## 🤝 贡献指南

欢迎提交Issue和PR来完善本项目：

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建Pull Request

## 📄 许可证

本项目采用Apache License 2.0开源许可证。

## 🙏 致谢

感谢所有为本项目做出贡献的开发者。

---

**最后更新时间**: 2026-03-14
**JDK版本**: JDK 21 LTS  
**版本**: v2.0.0  
**维护团队**: Performance Optimization Team
