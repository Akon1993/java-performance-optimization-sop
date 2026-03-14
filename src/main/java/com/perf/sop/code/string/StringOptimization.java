/**
 * 字符串处理性能优化最佳实践
 * 
 * 【SOP要点】
 * 1. 频繁字符串拼接必须使用StringBuilder或StringBuffer
 * 2. 避免在循环中使用+操作符拼接字符串
 * 3. 合理使用String.intern()注意内存消耗
 * 4. 字符串分割优先使用StringTokenizer或split的替代方案
 * 
 * 【性能对比】（基于JMH基准测试）
 * - String += : 约1000ms/万次
 * - StringBuilder.append(): 约0.1ms/万次
 * - 性能差异: 10000倍以上
 * 
 * 【注意事项】
 * - StringBuilder: 非线程安全，单线程环境下使用
 * - StringBuffer: 线程安全，多线程环境下使用（性能略低）
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.code.string;

import java.util.StringTokenizer;

public class StringOptimization {

    /**
     * ❌ 反例：在循环中使用+拼接字符串
     * 
     * 性能问题：
     * 1. 每次循环都会创建新的String对象
     * 2. 产生大量临时对象，增加GC压力
     * 3. 时间复杂度O(n²)
     * 
     * 内存分析：
     * - 循环10000次，创建约10000个String对象
     * - 每个String对象约40字节，总计约400KB临时对象
     */
    public String badStringConcat(int count) {
        String result = "";
        for (int i = 0; i < count; i++) {
            result += i + ",";  // ❌ 每次循环都创建新String
        }
        return result;
    }

    /**
     * ✅ 正例：使用StringBuilder拼接字符串
     * 
     * 性能优势：
     * 1. 只创建一个StringBuilder对象
     * 2. 内部使用char[]数组，自动扩容
     * 3. 时间复杂度O(n)
     * 
     * 优化技巧：
     * - 预估容量：new StringBuilder(expectedLength)避免扩容
     * - 预估公式：字符串平均长度 × 数量 + 分隔符长度 × (数量-1)
     */
    public String goodStringConcat(int count) {
        // ✅ 预估容量：假设每个数字平均3位，分隔符1位
        int expectedLength = count * 4;
        StringBuilder sb = new StringBuilder(expectedLength);
        
        for (int i = 0; i < count; i++) {
            sb.append(i).append(",");  // ✅ 复用同一个StringBuilder
        }
        
        // 移除最后一个多余的逗号
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        
        return sb.toString();
    }

    /**
     * ✅ 进阶：使用String.join()（Java 8+）
     * 
     * 适用场景：集合类转字符串
     * 内部实现：使用StringJoiner，性能等同于StringBuilder
     */
    public String modernStringConcat(java.util.List<String> items) {
        // ✅ Java 8 Stream API
        return String.join(",", items);
    }

    /**
     * ❌ 反例：使用String.split()处理大文本
     * 
     * 性能问题：
     * 1. 使用正则表达式，编译开销大
     * 2. 产生大量临时字符串对象
     * 3. 不支持lazy evaluation
     * 
     * 适用：简单分隔且数据量小
     */
    public String[] badSplit(String text) {
        return text.split(",");  // ❌ 使用正则，性能差
    }

    /**
     * ✅ 正例：使用StringTokenizer处理大文本
     * 
     * 性能优势：
     * 1. 不使用正则，直接字符匹配
     * 2. 逐个返回token，内存友好
     * 3. 支持多个分隔符
     * 
     * 注意：StringTokenizer是遗留类，新代码建议使用String.split()或Guava Splitter
     */
    public java.util.List<String> goodSplit(String text) {
        java.util.List<String> result = new java.util.ArrayList<>();
        StringTokenizer tokenizer = new StringTokenizer(text, ",");
        
        while (tokenizer.hasMoreTokens()) {
            result.add(tokenizer.nextToken());
        }
        
        return result;
    }

    /**
     * ✅ 最佳实践：使用Guava Splitter（推荐）
     * 
     * 优势：
     * 1. 线程安全，可复用
     * 2. 支持lazy evaluation（omitEmptyStrings, trimResults）
     * 3. 性能优于String.split()
     */
    public java.util.List<String> bestSplit(String text) {
        // 需要引入Guava依赖
        return com.google.common.base.Splitter.on(',')
                .omitEmptyStrings()     // 跳过空字符串
                .trimResults()          // 去除首尾空格
                .splitToList(text);
    }

    /**
     * ⚠️ 慎用：String.intern()
     * 
     * 使用场景：
     * - 字符串常量池优化，减少内存占用
     * - 大量重复的字符串（如枚举值、状态码）
     * 
     * 注意事项：
     * 1. Java 7+ interned字符串存储在堆中，不在PermGen
     * 2. 频繁intern会导致GC压力增大
     * 3. 不适用于生命周期短的字符串
     * 
     * 替代方案：使用Guava Interner或自定义缓存
     */
    public void stringInternExample() {
        // ✅ 适用场景：状态码常量
        String status = "ACTIVE".intern();
        
        // ❌ 不适用：动态生成的字符串
        String dynamic = ("user_" + System.currentTimeMillis()).intern();  // 不要这样做
    }

    /**
     * ✅ 正例：自定义字符串池（推荐用于大量重复字符串）
     */
    private static final com.google.common.collect.Interner<String> STRING_INTERNER = 
            com.google.common.collect.Interners.newWeakInterner();
    
    public String customIntern(String str) {
        return STRING_INTERNER.intern(str);
    }

    /**
     * 字符串比较优化
     * 
     * 【原则】
     * 1. 常量放前面，避免NPE
     * 2. 使用equalsIgnoreCase()比较忽略大小写
     * 3. 多字符串比较考虑使用switch（Java 7+）
     */
    public boolean safeEquals(String input) {
        // ✅ 常量在前，避免input为null时NPE
        return "EXPECTED".equals(input);
        
        // ❌ 不推荐：input.equals("EXPECTED");
    }

    /**
     * Java 14+ Text Blocks（多行字符串）
     * 
     * 优势：
     * 1. 代码可读性提升
     * 2. 自动处理换行和缩进
     * 3. 避免字符串拼接
     */
    public String textBlockExample() {
        // Java 14+ 特性
        String json = """
            {
                "name": "performance",
                "version": "1.0.0",
                "description": "String optimization examples"
            }
            """;
        return json;
    }

    /**
     * 字符串替换性能对比
     * 
     * 场景：循环中多次替换
     * 
     * 性能排序（从快到慢）：
     * 1. String.replace() (Java 9+ 优化过) - O(n)
     * 2. Apache Commons StringUtils.replace() - O(n)
     * 3. 正则Pattern.matcher().replaceAll() - O(n)但有编译开销
     * 4. String.replaceAll() - O(n)，每次编译Pattern
     */
    public String replaceExample(String text) {
        // ✅ 简单替换使用String.replace()
        return text.replace("old", "new");
        
        // ❌ 避免在循环中使用replaceAll()
        // return text.replaceAll("old", "new");  // 每次都会编译Pattern
    }
}