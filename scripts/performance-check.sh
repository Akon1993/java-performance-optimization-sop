#!/bin/bash

# Java性能优化检查脚本
# 
# 使用方法：
# 1. cd performance-optimization-sop
# 2. chmod +x scripts/performance-check.sh
# 3. ./scripts/performance-check.sh [项目路径]
#
# 功能：
# - 检查常见的性能问题代码模式
# - 统计潜在的优化点
# - 生成检查报告

set -e

PROJECT_DIR="${1:-.}"
REPORT_FILE="performance-check-report.md"
OUTPUT_FILE="$PROJECT_DIR/$REPORT_FILE"

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 统计变量
TOTAL_ISSUES=0
CRITICAL_ISSUES=0
WARNING_ISSUES=0
SUGGESTION_ISSUES=0

# 初始化报告
echo "# 性能优化检查报告" > "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "生成时间: $(date '+%Y-%m-%d %H:%M:%S')" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# 检查函数
check_pattern() {
    local title="$1"
    local pattern="$2"
    local severity="$3"  # CRITICAL, WARNING, SUGGESTION
    local description="$4"
    local suggestion="$5"
    
    echo "检查: $title..."
    
    # 查找匹配的文件和行
    matches=$(find "$PROJECT_DIR/src" -name "*.java" -exec grep -Hn "$pattern" {} + 2>/dev/null || true)
    
    if [ -n "$matches" ]; then
        count=$(echo "$matches" | wc -l)
        TOTAL_ISSUES=$((TOTAL_ISSUES + count))
        
        case "$severity" in
            CRITICAL)
                CRITICAL_ISSUES=$((CRITICAL_ISSUES + count))
                severity_icon="🔴"
                ;;
            WARNING)
                WARNING_ISSUES=$((WARNING_ISSUES + count))
                severity_icon="🟡"
                ;;
            SUGGESTION)
                SUGGESTION_ISSUES=$((SUGGESTION_ISSUES + count))
                severity_icon="🔵"
                ;;
        esac
        
        {
            echo "## $severity_icon $title ($count 处)"
            echo ""
            echo "**严重程度**: $severity"
            echo ""
            echo "**问题描述**: $description"
            echo ""
            echo "**优化建议**: $suggestion"
            echo ""
            echo "**问题位置**:"
            echo '```'
            echo "$matches" | head -20
            echo '```'
            if [ "$count" -gt 20 ]; then
                echo "... 还有 $((count - 20)) 处未显示"
            fi
            echo ""
        } >> "$OUTPUT_FILE"
        
        return 0
    fi
    
    return 1
}

# 检查目录是否存在
if [ ! -d "$PROJECT_DIR/src" ]; then
    echo -e "${RED}错误: 找不到项目源代码目录 $PROJECT_DIR/src${NC}"
    echo "使用方法: $0 [项目路径]"
    exit 1
fi

echo "========================================"
echo "  Java性能优化检查工具"
echo "  检查目录: $PROJECT_DIR"
echo "========================================"
echo ""

# 1. 检查String拼接问题
check_pattern \
    "循环中String拼接" \
    "for.*+=.*String\|while.*+=.*String" \
    CRITICAL \
    "在循环中使用 += 拼接String会创建大量临时对象，导致性能问题" \
    "使用 StringBuilder 或 StringBuffer 替代"

# 2. 检查集合容量问题
check_pattern \
    "ArrayList未设置初始容量" \
    "new ArrayList<>()" \
    WARNING \
    "未设置初始容量的ArrayList在扩容时会产生数组拷贝开销" \
    "预估数据量，使用 new ArrayList<>(capacity)"

check_pattern \
    "HashMap未设置初始容量" \
    "new HashMap<>()" \
    WARNING \
    "未设置初始容量的HashMap在扩容时需要重新哈希，性能开销大" \
    "根据公式 (expectedSize / 0.75) + 1 设置初始容量"

# 3. 检查LinkedList索引访问
check_pattern \
    "LinkedList索引访问" \
    "LinkedList.*\.get(" \
    CRITICAL \
    "LinkedList.get(index)时间复杂度为O(n)，在循环中使用会导致O(n²)复杂度" \
    "使用增强for循环或Iterator遍历"

# 4. 检查SimpleDateFormat线程安全
check_pattern \
    "SimpleDateFormat未正确同步" \
    "new SimpleDateFormat" \
    CRITICAL \
    "SimpleDateFormat不是线程安全的，多线程使用会导致数据混乱" \
    "使用ThreadLocal包装或使用DateTimeFormatter（线程安全）"

# 5. 检查自动装箱问题
check_pattern \
    "循环中使用包装类" \
    "for.*Integer\|for.*Long" \
    WARNING \
    "在循环中使用包装类会导致频繁的自动装箱/拆箱，增加GC压力" \
    "使用基本类型int/long替代"

# 6. 检查数据库连接未关闭
check_pattern \
    "可能的连接泄漏" \
    "getConnection()" \
    WARNING \
    "数据库连接需要确保在使用后关闭，否则会导致连接泄漏" \
    "使用try-with-resources确保连接关闭"

# 7. 检查synchronized使用
check_pattern \
    "synchronized方法" \
    "public synchronized" \
    SUGGESTION \
    "synchronized方法会锁定整个方法，可能影响并发性能" \
    "考虑缩小锁粒度，使用synchronized代码块"

# 8. 检查System.out.println
check_pattern \
    "生产环境System.out.println" \
    "System.out.println\|System.err.println" \
    WARNING \
    "生产环境应避免使用System.out.println，会影响性能且无法灵活控制" \
    "使用SLF4J等日志框架，并合理设置日志级别"

# 9. 检查Thread.sleep异常处理
check_pattern \
    "Thread.sleep未恢复中断状态" \
    "Thread.sleep.*catch" \
    WARNING \
    "捕获InterruptedException后应恢复中断状态或终止线程" \
    "在catch块中调用Thread.currentThread().interrupt()"

# 10. 检查魔法数字
check_pattern \
    "魔法数字" \
    "= 1000\|= 60\|= 10000" \
    SUGGESTION \
    "代码中使用裸数字难以理解和维护" \
    "使用有意义的常量替代"

# 11. 检查异常打印
check_pattern \
    "e.printStackTrace()" \
    "printStackTrace()" \
    WARNING \
    "使用printStackTrace()打印异常不利于日志管理和监控" \
    "使用日志框架记录异常"

# 12. 检查可能的大对象创建
check_pattern \
    "大数组创建" \
    "new byte\\[1000000\\]\|new byte\\[1024.*1024" \
    SUGGESTION \
    "创建大数组会直接进入老年代，增加GC压力" \
    "考虑使用对象池或分批处理"

# 13. 检查TODO和FIXME
check_pattern \
    "待办事项" \
    "TODO\|FIXME\|XXX" \
    SUGGESTION \
    "代码中存在待办事项需要处理" \
    "在发布前完成或移除相关TODO"

echo ""
echo "========================================"
echo "检查完成！"
echo "========================================"
echo ""

# 统计汇总
{
    echo "# 统计汇总"
    echo ""
    echo "| 类型 | 数量 |"
    echo "|------|------|"
    echo "| 🔴 严重问题 | $CRITICAL_ISSUES |"
    echo "| 🟡 警告 | $WARNING_ISSUES |"
    echo "| 🔵 建议 | $SUGGESTION_ISSUES |"
    echo "| **总计** | **$TOTAL_ISSUES** |"
    echo ""
    echo "---"
    echo ""
    echo "# 检查清单"
    echo ""
    echo "## 代码层面优化"
    echo "- [ ] 集合类选择是否合理？"
    echo "- [ ] 字符串拼接是否使用了StringBuilder？"
    echo "- [ ] 循环内部是否有不必要的对象创建？"
    echo "- [ ] 是否避免了自动装箱/拆箱？"
    echo ""
    echo "## JVM优化"
    echo "- [ ] JVM参数配置是否合理？"
    echo "- [ ] GC算法是否适合应用场景？"
    echo "- [ ] 是否配置了OOM处理？"
    echo ""
    echo "## 数据库优化"
    echo "- [ ] 连接池配置是否合理？"
    echo "- [ ] SQL是否有索引支持？"
    echo "- [ ] 是否使用了批量操作？"
    echo ""
    echo "## 并发优化"
    echo "- [ ] 锁的粒度是否足够小？"
    echo "- [ ] 是否使用了合适的并发容器？"
    echo "- [ ] 线程池参数是否配置正确？"
    echo ""
    echo "## 缓存优化"
    echo "- [ ] 是否处理了缓存穿透？"
    echo "- [ ] 是否处理了缓存击穿？"
    echo "- [ ] 是否处理了缓存雪崩？"
    echo ""
} >> "$OUTPUT_FILE"

# 输出结果
echo -e "检查结果摘要："
echo ""
echo -e "  🔴 严重问题: $CRITICAL_ISSUES"
echo -e "  🟡 警告: $WARNING_ISSUES"
echo -e "  🔵 建议: $SUGGESTION_ISSUES"
echo ""
echo -e "  ${YELLOW}总计: $TOTAL_ISSUES 处需要关注${NC}"
echo ""
echo "详细报告已生成: $OUTPUT_FILE"

# 根据问题数量返回状态码
if [ "$CRITICAL_ISSUES" -gt 0 ]; then
    exit 2
elif [ "$WARNING_ISSUES" -gt 0 ]; then
    exit 1
else
    echo -e "${GREEN}代码检查通过！${NC}"
    exit 0
fi
