#!/bin/bash
#
# Java 性能优化交互式决策树
# 通过命令行交互引导用户找到性能优化方案
#
# 使用方法：
#   ./perf-decision-tree.sh
#

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
BOLD='\033[1m'
NC='\033[0m' # No Color

# 历史记录
declare -a HISTORY

# 打印带颜色的标题
print_header() {
    clear
    echo ""
    echo -e "${CYAN}========================================${NC}"
    echo -e "${CYAN}   Java 性能优化决策树${NC}"
    echo -e "${CYAN}========================================${NC}"
    echo ""
}

# 打印面包屑
print_breadcrumb() {
    echo -n "路径: "
    for ((i=0; i<${#HISTORY[@]}; i++)); do
        echo -n "${HISTORY[i]}"
        if [ $i -lt $((${#HISTORY[@]}-1)) ]; then
            echo -n " > "
        fi
    done
    echo ""
    echo ""
}

# 打印选项
print_option() {
    local num=$1
    local title=$2
    local desc=$3
    echo -e "${BOLD}${YELLOW}${num})${NC} ${BOLD}${title}${NC}"
    echo -e "   ${desc}"
    echo ""
}

# 打印解决方案
print_solution() {
    local title=$1
    local desc=$2
    local code=$3
    
    echo -e "${GREEN}▶ ${title}${NC}"
    echo -e "  ${desc}"
    if [ -n "$code" ]; then
        echo -e "  ${CYAN}示例:${NC}"
        echo -e "  ${code}" | sed 's/^/    /'
    fi
    echo ""
}

# 等待用户输入
wait_for_input() {
    echo ""
    echo -e "${YELLOW}按回车键继续...${NC}"
    read
}

# 主菜单
show_main_menu() {
    print_header
    HISTORY=("开始")
    print_breadcrumb
    
    echo -e "${BOLD}请选择当前遇到的主要症状：${NC}"
    echo ""
    
    print_option "1" "🖥️  CPU 使用率过高" "CPU 持续高负载，系统负载高"
    print_option "2" "💾  内存使用过高 / OOM" "内存持续增长，频繁 Full GC，甚至 OOM"
    print_option "3" "⏱️  接口响应慢" "接口 P99 延迟高，用户体验差"
    print_option "4" "🗑️  GC 频繁 / 停顿长" "GC 次数多，停顿时间影响业务"
    print_option "5" "🗄️  数据库慢" "SQL 执行慢，连接池耗尽"
    print_option "6" "🧵  线程/并发问题" "线程阻塞，死锁，并发度低"
    print_option "0" "❌  退出" ""
    
    echo ""
    echo -n "请输入选项 [0-6]: "
    read choice
    
    case $choice in
        1) cpu_menu ;;
        2) memory_menu ;;
        3) response_menu ;;
        4) gc_menu ;;
        5) db_menu ;;
        6) thread_menu ;;
        0) exit 0 ;;
        *) 
            echo -e "${RED}无效选项，请重新选择${NC}"
            sleep 1
            show_main_menu
            ;;
    esac
}

# CPU 优化菜单
cpu_menu() {
    print_header
    HISTORY+=("CPU优化")
    print_breadcrumb
    
    echo -e "${BOLD}🖥️  CPU 优化 - 请使用 top 或 htop 观察 CPU 使用特征${NC}"
    echo ""
    
    print_option "1" "用户态 CPU 高 (us 列高)" "业务代码计算密集"
    print_option "2" "系统态 CPU 高 (sy 列高)" "系统调用频繁"
    print_option "3" "IO 等待高 (wa 列高)" "IO 阻塞导致"
    print_option "4" "多核不均衡" "某些核 100%，其他空闲，锁竞争"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) cpu_user_solution ;;
        2) cpu_sys_solution ;;
        3) cpu_io_solution ;;
        4) cpu_lock_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            cpu_menu
            ;;
    esac
}

# CPU 用户态高解决方案
cpu_user_solution() {
    print_header
    HISTORY+=("用户态CPU高")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}📈 用户态 CPU 高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 生成火焰图定位热点" \
        "使用 async-profiler 生成 CPU 火焰图，找出热点方法" \
        "./profiler.sh -d 30 -f flame.html <pid>"
    
    print_solution \
        "2. 优化热点算法" \
        "检查是否有 O(n²) 算法可优化为 O(n log n) 或 O(n)" \
        "// 示例：使用 HashMap 替代 List 查找\\n- list.contains(key)  // O(n)\\n+ map.containsKey(key) // O(1)"
    
    print_solution \
        "3. 引入缓存" \
        "对热点数据使用 Caffeine 缓存" \
        "LoadingCache<String, Object> cache = Caffeine.newBuilder()\\n    .maximumSize(1000)\\n    .build(this::expensiveCompute);"
    
    print_solution \
        "4. 并行计算" \
        "使用并行流或 CompletableFuture 利用多核" \
        "list.parallelStream()\\n    .map(this::process)\\n    .collect(Collectors.toList());"
    
    echo -e "${YELLOW}📚 相关文档：docs/01-代码层面优化.md${NC}"
    
    wait_for_input
    cpu_menu
}

# CPU 锁竞争解决方案
cpu_lock_solution() {
    print_header
    HISTORY+=("锁竞争")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔒 锁竞争 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 缩小锁粒度" \
        "只锁定必要的代码块，减少临界区" \
        "synchronized (lock) {\\n    // 只锁定需要保护的部分\\n    counter++;\\n}"
    
    print_solution \
        "2. 使用读写锁" \
        "读多写少场景使用 ReentrantReadWriteLock" \
        "ReadWriteLock rwLock = new ReentrantReadWriteLock();\\nrwLock.readLock().lock(); // 支持并发读"
    
    print_solution \
        "3. 无锁数据结构" \
        "使用 ConcurrentHashMap, LongAdder" \
        "LongAdder counter = new LongAdder();\\ncounter.increment(); // 无锁自增"
    
    print_solution \
        "4. 分段锁" \
        "将大锁拆分为多个小锁，降低竞争" \
        "SegmentLock lock = new SegmentLock(16);\\nlock.lock(key.hashCode());"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    cpu_menu
}

# CPU 系统态高解决方案
cpu_sys_solution() {
    print_header
    HISTORY+=("系统态CPU高")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}⚙️ 系统态 CPU 高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 减少系统调用" \
        "批量操作替代单个操作" \
        "// 批量读写替代循环单个读写"
    
    print_solution \
        "2. 使用零拷贝" \
        "FileChannel.transferTo 替代传统 IO" \
        "fileChannel.transferTo(0, size, socketChannel);"
    
    print_solution \
        "3. 减少线程切换" \
        "调整线程池大小，避免过多线程" \
        "线程数 = CPU核数 + 1（CPU密集）或 2*CPU核数（IO密集）"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    cpu_menu
}

# CPU IO等待解决方案
cpu_io_solution() {
    print_header
    HISTORY+=("IO等待")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}💽 IO 等待高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 异步 IO" \
        "使用 NIO 或异步客户端避免阻塞" \
        "CompletableFuture.supplyAsync(() -> fetchData());"
    
    print_solution \
        "2. 批量读写" \
        "减少 IO 次数，提高吞吐量" \
        "jdbcTemplate.batchUpdate(sql, batchArgs);"
    
    print_solution \
        "3. 本地缓存" \
        "热点数据内存缓存，减少 IO" \
        "@Cacheable(\"hotData\")"
    
    echo -e "${YELLOW}📚 相关文档：docs/05-缓存策略指南.md${NC}"
    
    wait_for_input
    cpu_menu
}

# 内存优化菜单
memory_menu() {
    print_header
    HISTORY+=("内存优化")
    print_breadcrumb
    
    echo -e "${BOLD}💾 内存优化 - 请使用 jmap -heap <pid> 查看内存使用状况${NC}"
    echo ""
    
    print_option "1" "持续增长不释放" "内存曲线单调上升，疑似内存泄漏"
    print_option "2" "周期性峰值" "特定操作后内存突增，过后恢复"
    print_option "3" "老年代快速增长" "老年代占用高，频繁 Full GC"
    print_option "4" "堆外内存高" "堆内存正常，RSS 远大于堆大小"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) memory_leak_solution ;;
        2) memory_peak_solution ;;
        3) memory_old_solution ;;
        4) memory_offheap_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            memory_menu
            ;;
    esac
}

# 内存泄漏解决方案
memory_leak_solution() {
    print_header
    HISTORY+=("内存泄漏")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🩸 内存泄漏 - 排查方案${NC}"
    echo ""
    
    print_solution \
        "1. 生成堆转储文件" \
        "使用 jmap 或 JVM 参数自动生成" \
        "jmap -dump:format=b,file=heap.hprof <pid>\\n# 或在 OOM 时自动生成\\n-XX:+HeapDumpOnOutOfMemoryError"
    
    print_solution \
        "2. 检查静态集合" \
        "静态 Map/List 是否只增不减" \
        "// 使用 Guava Cache 替代，设置容量上限\\nCache<String, Object> cache = CacheBuilder.newBuilder()\\n    .maximumSize(1000)\\n    .expireAfterWrite(10, TimeUnit.MINUTES)\\n    .build();"
    
    print_solution \
        "3. 检查 ThreadLocal" \
        "线程池 + ThreadLocal 组合易导致泄漏" \
        "try {\\n    threadLocal.set(value);\\n    // 业务逻辑\\n} finally {\\n    threadLocal.remove(); // 必须清理\\n}"
    
    print_solution \
        "4. 检查未关闭资源" \
        "数据库连接、文件句柄等" \
        "// 使用 try-with-resources\\ntry (Connection conn = dataSource.getConnection()) {\\n    // 使用连接\\n}"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    echo -e "${YELLOW}🔧 分析工具：Eclipse MAT (https://www.eclipse.org/mat/)${NC}"
    
    wait_for_input
    memory_menu
}

# 内存峰值解决方案
memory_peak_solution() {
    print_header
    HISTORY+=("内存峰值")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}📊 内存峰值 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 流式处理" \
        "避免一次性加载大量数据到内存" \
        "stream.forEach(this::process); // 而非 collect(toList())"
    
    print_solution \
        "2. 对象池" \
        "复用大对象，减少分配和 GC 压力" \
        "ByteBufferPool.allocate(size);"
    
    print_solution \
        "3. 分批处理" \
        "将大任务拆分为小批次执行" \
        "Lists.partition(list, 1000).forEach(this::processBatch);"
    
    echo -e "${YELLOW}📚 相关文档：docs/01-代码层面优化.md${NC}"
    
    wait_for_input
    memory_menu
}

# 老年代解决方案
memory_old_solution() {
    print_header
    HISTORY+=("老年代高")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}👴 老年代快速增长 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 缩短对象生命周期" \
        "及时释放引用，帮助 GC" \
        "data = null; // 帮助 GC"
    
    print_solution \
        "2. 使用弱引用" \
        "缓存使用 WeakReference" \
        "WeakReference<Cache> weakCache = new WeakReference<>(cache);"
    
    print_solution \
        "3. 增大新生代" \
        "让对象在新生代被回收" \
        "-Xmn2g 或 -XX:NewRatio=2"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    
    wait_for_input
    memory_menu
}

# 堆外内存解决方案
memory_offheap_solution() {
    print_header
    HISTORY+=("堆外内存")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🎯 堆外内存高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 检查 DirectBuffer" \
        "NIO 使用的直接内存是否正确释放" \
        "// 使用 try-with-resources 确保释放"
    
    print_solution \
        "2. 限制堆外内存" \
        "设置最大堆外内存大小" \
        "-XX:MaxDirectMemorySize=1g"
    
    print_solution \
        "3. 检查 JNI" \
        "native 代码内存分配" \
        "使用 jemalloc 分析 native 内存"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    
    wait_for_input
    memory_menu
}

# 响应时间优化菜单
response_menu() {
    print_header
    HISTORY+=("响应时间")
    print_breadcrumb
    
    echo -e "${BOLD}⏱️ 响应时间优化 - 请使用 Arthas trace 分析耗时分布${NC}"
    echo ""
    
    print_option "1" "数据库耗时高" "SQL 查询占接口耗时主要部分"
    print_option "2" "远程调用耗时高" "HTTP/RPC 调用耗时长"
    print_option "3" "本地计算耗时高" "业务逻辑处理慢"
    print_option "4" "锁等待耗时高" "线程等待锁释放时间长"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) response_db_solution ;;
        2) response_rpc_solution ;;
        3) response_compute_solution ;;
        4) response_lock_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            response_menu
            ;;
    esac
}

# 数据库慢解决方案
response_db_solution() {
    print_header
    HISTORY+=("数据库慢")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🗄️ 数据库慢 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 添加索引" \
        "为 WHERE、JOIN、ORDER BY 字段添加索引" \
        "CREATE INDEX idx_user_status ON users(status, created_at);"
    
    print_solution \
        "2. SQL 优化" \
        "避免 SELECT *，使用覆盖索引" \
        "-- 使用覆盖索引\\nSELECT id, name FROM user WHERE status = 1;\\n-- 确保 (status, id, name) 是联合索引"
    
    print_solution \
        "3. 分页优化" \
        "深分页使用延迟关联" \
        "-- 延迟关联优化深分页\\nSELECT * FROM orders o\\nJOIN (SELECT id FROM orders ORDER BY id LIMIT 100000, 10) tmp\\nON o.id = tmp.id;"
    
    print_solution \
        "4. 引入缓存" \
        "热点数据使用 Redis 缓存" \
        "@Cacheable(value = \"user\", key = \"#id\")\\npublic User getUser(Long id) {\\n    return userMapper.selectById(id);\\n}"
    
    echo -e "${YELLOW}📚 相关文档：docs/03-数据库优化指南.md${NC}"
    
    wait_for_input
    response_menu
}

# RPC 调用慢解决方案
response_rpc_solution() {
    print_header
    HISTORY+=("RPC慢")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🌐 RPC 调用慢 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 连接池复用" \
        "HTTP 连接池配置，避免重复建连" \
        "HttpClient.newBuilder().connectionPool(pool);"
    
    print_solution \
        "2. 异步调用" \
        "CompletableFuture 并行调用多个服务" \
        "CompletableFuture.allOf(future1, future2).join();"
    
    print_solution \
        "3. 熔断降级" \
        "使用 Sentinel 或 Hystrix 防止雪崩" \
        "@SentinelResource(fallback = \"fallbackMethod\")"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    response_menu
}

# 计算慢解决方案
response_compute_solution() {
    print_header
    HISTORY+=("计算慢")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🧮 本地计算慢 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 算法优化" \
        "降低时间复杂度" \
        "// O(n²) -> O(n log n)"
    
    print_solution \
        "2. 缓存结果" \
        "避免重复计算" \
        "@Cacheable(\"calculationResult\")"
    
    print_solution \
        "3. 提前计算" \
        "异步预计算热点数据" \
        "@Scheduled(fixedRate = 60000) public void precompute() {}"
    
    echo -e "${YELLOW}📚 相关文档：docs/01-代码层面优化.md${NC}"
    
    wait_for_input
    response_menu
}

# 锁等待解决方案
response_lock_solution() {
    print_header
    HISTORY+=("锁等待")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}⏳ 锁等待耗时高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 锁粒度细化" \
        "分段锁减少竞争" \
        "ConcurrentHashMap 替代 synchronized Map"
    
    print_solution \
        "2. 乐观锁" \
        "CAS 替代悲观锁" \
        "AtomicInteger.compareAndSet(expected, update);"
    
    print_solution \
        "3. 无锁队列" \
        "使用 Disruptor 高性能队列" \
        "RingBuffer<Event> ringBuffer = disruptor.getRingBuffer();"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    response_menu
}

# GC 优化菜单
gc_menu() {
    print_header
    HISTORY+=("GC优化")
    print_breadcrumb
    
    echo -e "${BOLD}🗑️ GC 优化 - 请分析 GC 日志，观察 GC 特征${NC}"
    echo ""
    
    print_option "1" "Young GC 频繁" "每秒多次 Young GC，但停顿短"
    print_option "2" "Full GC 频繁" "频繁 Full GC，停顿时间长"
    print_option "3" "GC 停顿过长" "单次 GC 停顿超过 100ms"
    print_option "4" "GC 后内存不释放" "Full GC 后内存仍然高"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) gc_young_solution ;;
        2) gc_full_solution ;;
        3) gc_pause_solution ;;
        4) gc_release_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            gc_menu
            ;;
    esac
}

# Young GC 解决方案
gc_young_solution() {
    print_header
    HISTORY+=("YoungGC频繁")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}👶 Young GC 频繁 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 增大 Eden 区" \
        "减少 Young GC 频率" \
        "-Xmn2g 或 -XX:NewRatio=2"
    
    print_solution \
        "2. 避免大对象" \
        "大对象直接进入老年代" \
        "// 分批处理，避免一次性创建大数组"
    
    print_solution \
        "3. 对象池" \
        "复用对象减少分配" \
        "ObjectPool<T> pool = new ObjectPool<>(factory);"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    
    wait_for_input
    gc_menu
}

# Full GC 解决方案
gc_full_solution() {
    print_header
    HISTORY+=("FullGC频繁")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}👴 Full GC 频繁 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 检查内存泄漏" \
        "使用 MAT 分析堆转储" \
        "jmap -dump:format=b,file=heap.hprof <pid>"
    
    print_solution \
        "2. 增大老年代" \
        "调整堆内存比例" \
        "-XX:NewRatio=3  # 老年代:新生代 = 3:1"
    
    print_solution \
        "3. 禁用 System.gc" \
        "防止显式触发 Full GC" \
        "-XX:+DisableExplicitGC"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    
    wait_for_input
    gc_menu
}

# GC 停顿解决方案
gc_pause_solution() {
    print_header
    HISTORY+=("GC停顿")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}⏸️ GC 停顿过长 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 选择低延迟 GC" \
        "JDK 17+ 推荐使用 ZGC" \
        "# ZGC 配置（目标停顿 < 1ms）\\n-XX:+UseZGC\\n-XX:+ZGenerational  # JDK 21+ 分代 ZGC"
    
    print_solution \
        "2. G1 调优" \
        "设置目标停顿时间" \
        "# G1 配置（目标停顿 200ms）\\n-XX:+UseG1GC\\n-XX:MaxGCPauseMillis=200\\n-XX:G1HeapRegionSize=16m"
    
    print_solution \
        "3. 增大堆内存" \
        "内存充足可减少 GC 频率" \
        "-Xms8g -Xmx8g  # 初始和最大堆内存一致"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    echo -e "${YELLOW}🌐 GC 日志分析：https://gceasy.io/${NC}"
    
    wait_for_input
    gc_menu
}

# GC 不释放解决方案
gc_release_solution() {
    print_header
    HISTORY+=("GC不释放")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔄 GC 后内存不释放 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 内存泄漏排查" \
        "查找强引用链" \
        "使用 Eclipse MAT 分析 dominator tree"
    
    print_solution \
        "2. 软引用缓存" \
        "允许 GC 回收缓存" \
        "SoftReference<Cache> softCache = new SoftReference<>(cache);"
    
    print_solution \
        "3. 弱引用监听器" \
        "避免监听器累积" \
        "WeakReference<Listener> weakListener = new WeakReference<>(listener);"
    
    echo -e "${YELLOW}📚 相关文档：docs/02-JVM调优指南.md${NC}"
    
    wait_for_input
    gc_menu
}

# 数据库优化菜单
db_menu() {
    print_header
    HISTORY+=("数据库优化")
    print_breadcrumb
    
    echo -e "${BOLD}🗄️ 数据库优化 - 请检查慢查询日志和连接池监控${NC}"
    echo ""
    
    print_option "1" "慢查询" "特定 SQL 执行时间长"
    print_option "2" "连接问题" "连接等待，连接池耗尽"
    print_option "3" "锁竞争" "行锁等待，死锁"
    print_option "4" "主从延迟" "读写分离场景下数据不一致"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) db_slow_solution ;;
        2) db_conn_solution ;;
        3) db_lock_solution ;;
        4) db_replica_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            db_menu
            ;;
    esac
}

# 慢查询解决方案
db_slow_solution() {
    print_header
    HISTORY+=("慢查询")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🐌 慢查询 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 添加索引" \
        "为 WHERE、JOIN、ORDER BY 字段添加索引" \
        "CREATE INDEX idx_user_status ON users(status, created_at);"
    
    print_solution \
        "2. 执行计划分析" \
        "使用 EXPLAIN 分析 SQL" \
        "EXPLAIN SELECT * FROM users WHERE email = 'test@example.com';"
    
    print_solution \
        "3. 避免大 IN 查询" \
        "数量 > 1000 时改用临时表" \
        "CREATE TEMPORARY TABLE tmp_ids (id BIGINT PRIMARY KEY);\\nINSERT INTO tmp_ids VALUES (...);"
    
    echo -e "${YELLOW}📚 相关文档：docs/03-数据库优化指南.md${NC}"
    
    wait_for_input
    db_menu
}

# 连接问题解决方案
db_conn_solution() {
    print_header
    HISTORY+=("连接问题")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔌 连接问题 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 增大连接池" \
        "HikariCP 配置" \
        "maximum-pool-size: 20\\nminimum-idle: 5"
    
    print_solution \
        "2. 检查连接泄漏" \
        "启用泄漏检测" \
        "leak-detection-threshold: 60000"
    
    print_solution \
        "3. 连接预热" \
        "启动时初始化连接" \
        "dataSource.getConnection().close();"
    
    echo -e "${YELLOW}📚 相关文档：docs/03-数据库优化指南.md${NC}"
    
    wait_for_input
    db_menu
}

# 数据库锁解决方案
db_lock_solution() {
    print_header
    HISTORY+=("数据库锁")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔐 锁竞争 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 减少事务范围" \
        "缩短事务持有时间" \
        "@Transactional(propagation = Propagation.REQUIRED)"
    
    print_solution \
        "2. 调整隔离级别" \
        "读已提交替代可串行化" \
        "@Transactional(isolation = Isolation.READ_COMMITTED)"
    
    print_solution \
        "3. 乐观锁" \
        "使用版本号替代行锁" \
        "UPDATE table SET value = ?, version = version + 1 WHERE id = ? AND version = ?"
    
    echo -e "${YELLOW}📚 相关文档：docs/03-数据库优化指南.md${NC}"
    
    wait_for_input
    db_menu
}

# 主从延迟解决方案
db_replica_solution() {
    print_header
    HISTORY+=("主从延迟")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔄 主从延迟 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 强制走主库" \
        "关键查询读主库" \
        "@Transactional(readOnly = false) // 强制主库"
    
    print_solution \
        "2. 延迟敏感读" \
        "写后立即读使用主库" \
        "// 写操作后的 1 秒内读主库"
    
    print_solution \
        "3. 并行复制" \
        "启用 MySQL 并行复制" \
        "slave_parallel_workers = 8"
    
    echo -e "${YELLOW}📚 相关文档：docs/03-数据库优化指南.md${NC}"
    
    wait_for_input
    db_menu
}

# 线程优化菜单
thread_menu() {
    print_header
    HISTORY+=("线程优化")
    print_breadcrumb
    
    echo -e "${BOLD}🧵 线程/并发优化 - 请使用 jstack 分析线程状态${NC}"
    echo ""
    
    print_option "1" "线程阻塞" "大量 BLOCKED 状态线程"
    print_option "2" "死锁" "线程相互等待，系统卡死"
    print_option "3" "线程池满" "任务队列积压，拒绝执行"
    print_option "4" "上下文切换高" "cs 列高，线程切换频繁"
    print_option "5" "返回上级" ""
    
    echo ""
    echo -n "请输入选项 [1-5]: "
    read choice
    
    case $choice in
        1) thread_block_solution ;;
        2) thread_deadlock_solution ;;
        3) thread_pool_solution ;;
        4) thread_context_solution ;;
        5) 
            unset 'HISTORY[${#HISTORY[@]}-1]'
            show_main_menu
            ;;
        *) 
            echo -e "${RED}无效选项${NC}"
            sleep 1
            thread_menu
            ;;
    esac
}

# 线程阻塞解决方案
thread_block_solution() {
    print_header
    HISTORY+=("线程阻塞")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🚫 线程阻塞 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 分析线程栈" \
        "找出阻塞原因" \
        "jstack <pid> | grep BLOCKED -A 5"
    
    print_solution \
        "2. 锁优化" \
        "使用并发容器" \
        "ConcurrentHashMap 替代 HashTable"
    
    print_solution \
        "3. 超时设置" \
        "避免无限等待" \
        "lock.tryLock(10, TimeUnit.SECONDS)"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    thread_menu
}

# 死锁解决方案
thread_deadlock_solution() {
    print_header
    HISTORY+=("死锁")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}💀 死锁 - 解决方案${NC}"
    echo ""
    
    print_solution \
        "1. 检测死锁" \
        "使用 jstack 检测" \
        "jstack -l <pid> | grep -A 50 \"Found one Java-level deadlock\""
    
    print_solution \
        "2. 统一加锁顺序" \
        "按固定顺序获取锁" \
        "// 按 hashCode 顺序加锁"
    
    print_solution \
        "3. 使用 tryLock" \
        "带超时的锁获取" \
        "if (lock.tryLock(1, TimeUnit.SECONDS)) {\\n    try { ... }\\n    finally { lock.unlock(); }\\n}"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    thread_menu
}

# 线程池满解决方案
thread_pool_solution() {
    print_header
    HISTORY+=("线程池满")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🏊 线程池满 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 增大核心线程数" \
        "提高并发处理能力" \
        "ThreadPoolExecutor executor = new ThreadPoolExecutor(10, 50, 60L, TimeUnit.SECONDS, queue);"
    
    print_solution \
        "2. 使用有界队列" \
        "防止无限堆积" \
        "new LinkedBlockingQueue<>(1000)"
    
    print_solution \
        "3. 自定义拒绝策略" \
        "优雅处理过载" \
        "new ThreadPoolExecutor.CallerRunsPolicy()"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    thread_menu
}

# 上下文切换解决方案
thread_context_solution() {
    print_header
    HISTORY+=("上下文切换")
    print_breadcrumb
    
    echo -e "${BOLD}${GREEN}🔄 上下文切换高 - 优化方案${NC}"
    echo ""
    
    print_solution \
        "1. 减少线程数" \
        "避免过多线程竞争 CPU" \
        "线程数 = CPU核数 * (1 + 等待时间/计算时间)"
    
    print_solution \
        "2. 使用协程" \
        "JDK 21 虚拟线程" \
        "Executors.newVirtualThreadPerTaskExecutor()"
    
    print_solution \
        "3. 合并任务" \
        "减少任务数量" \
        "// 小任务合并批量处理"
    
    echo -e "${YELLOW}📚 相关文档：docs/04-并发优化指南.md${NC}"
    
    wait_for_input
    thread_menu
}

# 主入口
main() {
    # 检查是否在交互式终端
    if [ ! -t 0 ]; then
        echo "请在交互式终端中运行此脚本"
        exit 1
    fi
    
    show_main_menu
}

main "$@"
