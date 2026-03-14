/**
 * 循环性能优化最佳实践
 * 
 * 【SOP核心原则】
 * 1. 避免在循环中创建对象
 * 2. 减少循环内部的方法调用
 * 3. 使用增强for循环或Iterator代替索引循环（除ArrayList外）
 * 4. 循环展开（Loop Unrolling）在特定场景使用
 * 5. 使用Java 8 Stream API进行声明式处理
 * 
 * 【性能优化策略】
 * 
 * 1. 对象创建优化
 *    - 将对象创建移到循环外部
 *    - 使用对象池复用对象
 *    - 优先使用基本类型避免装箱
 * 
 * 2. 循环条件优化
 *    - 将循环边界计算移到外部
 *    - 倒序循环可能更快（与零比较）
 *    - 避免在循环条件中调用方法
 * 
 * 3. 算法优化
 *    - 选择合适的数据结构
 *    - 减少嵌套循环的时间复杂度
 *    - 使用空间换时间
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.code.loop;

import java.util.*;

public class LoopOptimization {

    /**
     * ==================== 循环条件优化 ====================
     */

    /**
     * ❌ 反例：在循环条件中调用方法
     * 
     * 性能问题：
     * - list.size()每次循环都被调用
     * - JIT编译器可能无法优化（如果size()不是final）
     * - 虽然JIT可能会优化，但最好显式处理
     */
    public void badLoopCondition(List<String> list) {
        for (int i = 0; i < list.size(); i++) {  // ❌ 每次调用size()
            System.out.println(list.get(i));
        }
    }

    /**
     * ✅ 正例：将循环边界提取到外部
     */
    public void goodLoopCondition(List<String> list) {
        // 方式1：提取size到局部变量
        int size = list.size();
        for (int i = 0; i < size; i++) {
            System.out.println(list.get(i));
        }
        
        // 方式2：倒序循环（某些CPU架构上与零比较更快）
        for (int i = list.size() - 1; i >= 0; i--) {
            System.out.println(list.get(i));
        }
    }

    /**
     * ==================== 循环内部对象创建优化 ====================
     */

    /**
     * ❌ 反例：在循环中创建临时对象
     * 
     * 问题：
     * - 循环10000次，创建10000个StringBuilder
     * - 大量临时对象增加GC压力
     * - 时间复杂度O(n²)因为StringBuilder.append()内部扩容
     */
    public String badObjectCreationInLoop(List<Integer> numbers) {
        String result = "";
        for (Integer num : numbers) {
            // ❌ 每次循环都创建StringBuilder（编译器自动插入）
            result += num.toString() + ",";
        }
        return result;
    }

    /**
     * ✅ 正例：将对象创建移到循环外部
     */
    public String goodObjectCreation(List<Integer> numbers) {
        // ✅ 只创建一个StringBuilder
        StringBuilder sb = new StringBuilder(numbers.size() * 4);
        
        for (Integer num : numbers) {
            sb.append(num).append(",");
        }
        
        if (sb.length() > 0) {
            sb.setLength(sb.length() - 1);
        }
        
        return sb.toString();
    }

    /**
     * ❌ 反例：在循环中解析日期
     */
    public List<Long> badDateParsingInLoop(List<String> dateStrings) {
        List<Long> timestamps = new ArrayList<>();
        
        for (String dateStr : dateStrings) {
            // ❌ 每次循环都创建SimpleDateFormat（expensive！）
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
            try {
                timestamps.add(sdf.parse(dateStr).getTime());
            } catch (java.text.ParseException e) {
                e.printStackTrace();
            }
        }
        
        return timestamps;
    }

    /**
     * ✅ 正例：复用SimpleDateFormat（注意线程安全）
     */
    private static final ThreadLocal<java.text.SimpleDateFormat> DATE_FORMAT = 
        ThreadLocal.withInitial(() -> new java.text.SimpleDateFormat("yyyy-MM-dd"));
    
    public List<Long> goodDateParsing(List<String> dateStrings) {
        List<Long> timestamps = new ArrayList<>();
        
        // ✅ 复用同一个DateFormat实例
        java.text.SimpleDateFormat sdf = DATE_FORMAT.get();
        
        for (String dateStr : dateStrings) {
            try {
                timestamps.add(sdf.parse(dateStr).getTime());
            } catch (java.text.ParseException e) {
                e.printStackTrace();
            }
        }
        
        return timestamps;
    }

    /**
     * ✅ 更好的方案：Java 8 DateTimeFormatter（线程安全）
     */
    private static final java.time.format.DateTimeFormatter FORMATTER = 
        java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd");
    
    public List<Long> bestDateParsing(List<String> dateStrings) {
        List<Long> timestamps = new ArrayList<>();
        
        for (String dateStr : dateStrings) {
            // ✅ DateTimeFormatter是线程安全的，可以静态共享
            java.time.LocalDate date = java.time.LocalDate.parse(dateStr, FORMATTER);
            timestamps.add(date.atStartOfDay(java.time.ZoneId.systemDefault())
                           .toInstant().toEpochMilli());
        }
        
        return timestamps;
    }

    /**
     * ==================== 基本类型 vs 包装类 ====================
     */

    /**
     * ❌ 反例：在循环中使用包装类
     * 
     * 性能问题：
     * - 自动装箱/拆箱有性能开销
     * - Integer对象占用更多内存（16字节 vs 4字节）
     * - 大量对象增加GC压力
     */
    public int badBoxingInLoop() {
        Integer sum = 0;  // ❌ 使用包装类
        for (Integer i = 0; i < 1000000; i++) {
            sum += i;  // 自动装箱/拆箱
        }
        return sum;
    }

    /**
     * ✅ 正例：使用基本类型
     */
    public int goodPrimitiveLoop() {
        int sum = 0;  // ✅ 基本类型
        for (int i = 0; i < 1000000; i++) {
            sum += i;  // 无装箱开销
        }
        return sum;
    }

    /**
     * ==================== 循环展开（Loop Unrolling）====================
     */

    /**
     * 循环展开优化
     * 
     * 原理：
     * - 减少循环迭代次数
     * - 减少循环控制开销（比较、跳转）
     * - 增加指令级并行性（ILP）
     * 
     * 适用场景：
     * - 循环次数确定且较大
     * - 循环体简单
     * - 性能关键代码（如图形处理、科学计算）
     * 
     * 注意：
     * - 现代JIT编译器会自动进行循环展开
     * - 手动展开可能降低代码可读性
     * - 一般不建议在业务代码中手动展开
     */
    public int loopUnrollingExample(int[] array) {
        int sum = 0;
        int i = 0;
        
        // ✅ 每次处理4个元素
        for (; i + 3 < array.length; i += 4) {
            sum += array[i];
            sum += array[i + 1];
            sum += array[i + 2];
            sum += array[i + 3];
        }
        
        // 处理剩余元素
        for (; i < array.length; i++) {
            sum += array[i];
        }
        
        return sum;
    }

    /**
     * ==================== 嵌套循环优化 ====================
     */

    /**
     * ❌ 反例：嵌套循环中重复计算
     */
    public int badNestedLoop(int[][] matrix) {
        int sum = 0;
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[i].length; j++) {
                // ❌ 每次循环都访问matrix[i].length
                sum += matrix[i][j];
            }
        }
        return sum;
    }

    /**
     * ✅ 正例：提取循环变量
     */
    public int goodNestedLoop(int[][] matrix) {
        int sum = 0;
        for (int i = 0; i < matrix.length; i++) {
            int[] row = matrix[i];  // ✅ 缓存行引用
            int rowLength = row.length;  // ✅ 缓存行长度
            
            for (int j = 0; j < rowLength; j++) {
                sum += row[j];
            }
        }
        return sum;
    }

    /**
     * ==================== 提前退出优化 ====================
     */

    /**
     * ✅ 正例：找到结果后立即退出
     */
    public boolean earlyExitExample(List<String> list, String target) {
        // ✅ 找到后立即return，避免不必要的遍历
        for (String item : list) {
            if (item.equals(target)) {
                return true;  // 找到即退出
            }
        }
        return false;
        
        // ❌ 不要这样写
        // boolean found = false;
        // for (String item : list) {
        //     if (item.equals(target)) {
        //         found = true;
        //     }
        // }
        // return found;
    }

    /**
     * ==================== Java 8 Stream优化 ====================
     */

    /**
     * Stream API使用指南
     * 
     * 性能考量：
     * 1. 简单操作：传统循环可能更快（无函数调用开销）
     * 2. 复杂操作：Stream代码更清晰，JIT优化后性能接近
     * 3. 并行流：大数据量（>10,000）时才有优势
     * 4. 避免装箱：使用IntStream, LongStream, DoubleStream
     */
    public void streamOptimization() {
        List<Integer> numbers = java.util.Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        
        // ❌ 装箱版本（较慢）
        int sum1 = numbers.stream()
            .map(n -> n * n)
            .reduce(0, Integer::sum);
        
        // ✅ 原始类型版本（推荐）
        int sum2 = numbers.stream()
            .mapToInt(n -> n * n)  // 转为IntStream，避免装箱
            .sum();
        
        // ✅ 并行流（大数据量时使用）
        List<Integer> largeList = new ArrayList<>();
        // ... 添加100000+元素
        int parallelSum = largeList.parallelStream()  // 并行处理
            .mapToInt(n -> n * n)
            .sum();
    }

    /**
     * ==================== 批量操作替代循环 ====================
     */

    /**
     * ✅ 使用System.arraycopy()复制数组
     * 
     * 性能：使用本地代码（C语言实现），比Java循环快10倍以上
     */
    public void arrayCopyOptimization() {
        int[] source = new int[10000];
        int[] dest = new int[10000];
        
        // ❌ 手动复制
        for (int i = 0; i < source.length; i++) {
            dest[i] = source[i];
        }
        
        // ✅ 使用System.arraycopy()
        System.arraycopy(source, 0, dest, 0, source.length);
        
        // ✅ Java 8+ 使用Arrays.copyOf()
        int[] copy = Arrays.copyOf(source, source.length);
    }

    /**
     * ✅ 使用Collections批量操作
     */
    public void collectionBatchOperation() {
        List<String> list = new ArrayList<>();
        
        // ❌ 逐个添加
        for (int i = 0; i < 1000; i++) {
            list.add("item_" + i);
        }
        
        // ✅ Java 8+ Stream批量生成
        List<String> streamList = java.util.stream.IntStream.range(0, 1000)
            .mapToObj(i -> "item_" + i)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * ==================== 尾递归优化 ====================
     */

    /**
     * 尾递归示例（Java不支持自动尾递归优化，但可以手动改写为循环）
     */
    public long factorial(int n) {
        // ✅ 迭代版本（推荐，不会栈溢出）
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
        
        // ❌ 递归版本（可能栈溢出）
        // if (n <= 1) return 1;
        // return n * factorial(n - 1);
    }

    /**
     * ==================== 循环性能测试工具 ====================
 */

    /**
     * 简单的循环性能测试框架
     * 
     * 使用方法：
     * 1. 编译运行此类
     * 2. 查看控制台输出的执行时间
     * 3. 对比不同实现的性能差异
     */
    public static void main(String[] args) {
        LoopOptimization optimizer = new LoopOptimization();
        
        // 准备测试数据
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < 1000000; i++) {
            numbers.add(i);
        }
        
        // 预热JVM
        for (int i = 0; i < 10; i++) {
            optimizer.goodObjectCreation(numbers);
        }
        
        // 正式测试
        long start = System.nanoTime();
        for (int i = 0; i < 100; i++) {
            optimizer.goodObjectCreation(numbers);
        }
        long end = System.nanoTime();
        
        System.out.printf("平均执行时间: %.2f ms%n", (end - start) / 1000000.0 / 100);
    }
}