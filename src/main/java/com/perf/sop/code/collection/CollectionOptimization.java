/**
 * 集合类性能优化最佳实践
 * 
 * 【SOP核心原则】
 * 1. 根据使用场景选择合适的集合类
 * 2. 预估容量，避免频繁扩容
 * 3. 优先使用Java 8+的Stream API处理集合
 * 4. 注意线程安全与性能的权衡
 * 
 * 【集合类选择决策树】
 * 
 * 是否需要线程安全？
 * ├─ 是
 * │  ├─ 读多写少：CopyOnWriteArrayList / CopyOnWriteArraySet
 * │  ├─ 高并发：ConcurrentHashMap / ConcurrentSkipListMap
 * │  └─ 简单同步：Collections.synchronizedXXX()（不推荐）
 * └─ 否
 *    ├─ List
 *    │  ├─ 随机访问多：ArrayList（O(1)）
 *    │  └─ 插入删除多：LinkedList（O(1)）
 *    ├─ Set
 *    │  ├─ 有序：LinkedHashSet
 *    │  ├─ 排序：TreeSet
 *    │  └─ 无序：HashSet
 *    └─ Map
 *       ├─ 有序：LinkedHashMap
 *       ├─ 排序：TreeMap
 *       └─ 无序：HashMap
 * 
 * 【性能对比数据】（基于10万元素操作）
 * - ArrayList.get(i): ~0.1ms
 * - LinkedList.get(i): ~500ms (O(n)遍历)
 * - HashMap.get(): ~0.1ms
 * - ConcurrentHashMap.get(): ~0.2ms
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.code.collection;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class CollectionOptimization {

    /**
     * ==================== ArrayList vs LinkedList ====================
     */

    private List<String> items = new ArrayList<>();
    private boolean condition = true;

    /**
     * ✅ ArrayList适用场景：随机访问频繁
     * 
     * 时间复杂度：
     * - get(index): O(1) - 直接数组下标访问
     * - add(E): O(1) 均摊 - 可能需要扩容
     * - add(index, E): O(n) - 需要移动元素
     * - remove(index): O(n) - 需要移动元素
     */
    public void arrayListBestPractice() {
        // ✅ 预估容量：避免频繁扩容
        // 默认初始容量10，扩容时增长1.5倍
        // 扩容代价：创建新数组 + System.arraycopy()
        int expectedSize = 10000;
        ArrayList<String> list = new ArrayList<>(expectedSize);
        
        // ✅ 批量添加时使用ensureCapacity()
        list.ensureCapacity(expectedSize);
        
        for (int i = 0; i < expectedSize; i++) {
            list.add("item_" + i);
        }
        
        // ✅ 随机访问性能最优
        String item = list.get(5000);  // O(1)
    }

    /**
     * ✅ LinkedList适用场景：频繁插入删除
     * 
     * 时间复杂度：
     * - get(index): O(n) - 需要遍历链表
     * - add(E): O(1) - 直接添加到尾部
     * - add(index, E): O(n) - 需要遍历到指定位置
     * - remove(index): O(n) - 需要遍历到指定位置
     * - remove(Object): O(n) - 需要遍历查找
     * 
     * 内存占用：每个节点额外开销（prev + next + element）约24字节
     */
    public void linkedListBestPractice() {
        // ✅ 双端队列操作
        Deque<String> deque = new LinkedList<>();
        
        deque.addFirst("first");  // O(1)
        deque.addLast("last");    // O(1)
        
        String first = deque.removeFirst();  // O(1)
        String last = deque.removeLast();    // O(1)
        
        // ❌ 不要这样用LinkedList
        // String item = ((List<String>) deque).get(1000);  // O(n)，性能极差
    }

    /**
     * ❌ 反例：在LinkedList中随机访问
     */
    public void badLinkedListUsage() {
        List<Integer> list = new LinkedList<>();
        for (int i = 0; i < 100000; i++) {
            list.add(i);
        }
        
        // ❌ 性能极差！O(n²)时间复杂度
        // LinkedList.get(n)内部使用遍历，不是下标访问
        for (int i = 0; i < list.size(); i++) {
            Integer value = list.get(i);  // 每次都要遍历到第i个
        }
    }

    /**
     * ✅ 正例：使用迭代器遍历LinkedList
     */
    public void goodLinkedListIteration() {
        List<Integer> list = new LinkedList<>();
        // ... 填充数据
        
        // ✅ 使用增强for循环或迭代器（编译后会转为迭代器）
        for (Integer value : list) {
            // O(n)总时间
        }
        
        // ✅ 或者显式使用Iterator
        Iterator<Integer> iterator = list.iterator();
        while (iterator.hasNext()) {
            Integer value = iterator.next();
        }
    }

    /**
     * ==================== HashMap优化 ====================
     */

    /**
     * ✅ HashMap最佳实践
     * 
     * 扩容机制：
     * - 初始容量：16（必须是2的幂）
     * - 负载因子：0.75
     * - 扩容时机：size > capacity * loadFactor
     * - 扩容代价：rehash所有元素，性能开销大
     * 
     * 优化策略：
     * 1. 预估容量，避免扩容
     * 2. 合理设置负载因子
     * 3. 使用合适的hashCode()
     */
    public void hashMapBestPractice() {
        // 公式：initialCapacity = expectedSize / loadFactor + 1
        int expectedSize = 1000;
        float loadFactor = 0.75f;
        int initialCapacity = (int) (expectedSize / loadFactor) + 1;
        
        // ✅ 预估容量创建HashMap
        Map<String, Object> map = new HashMap<>(initialCapacity, loadFactor);
        
        // Java 8+ 使用工厂方法
        Map<String, Object> map2 = new HashMap<>(expectedSize);
    }

    /**
     * Java 8+ Map优化方法
     */
    public void mapJava8Features() {
        Map<String, Integer> map = new HashMap<>();
        
        // ✅ computeIfAbsent: 避免重复计算
        // 替代：if (!map.containsKey(key)) { map.put(key, expensiveCompute()); }
        Integer value = map.computeIfAbsent("key", k -> expensiveCompute(k));
        
        // ✅ merge: 合并值
        map.merge("key", 1, Integer::sum);  // 计数器常用
        
        // ✅ getOrDefault: 避免null检查
        int count = map.getOrDefault("key", 0);
        
        // ✅ forEach: 简洁遍历
        map.forEach((k, v) -> System.out.println(k + "=" + v));
    }
    
    /**
     * ConcurrentHashMap Java 8+ 优化方法
     */
    public void concurrentMapJava8Features() {
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        
        // ✅ computeIfAbsent - 原子性判断+插入
        map.computeIfAbsent("key", k -> 1);
        
        // ✅ compute - 原子性计算
        map.compute("key", (k, v) -> v == null ? 1 : v + 1);
        
        // ✅ merge - 原子性合并
        map.merge("key", 1, Integer::sum);
    }

    private Integer expensiveCompute(String key) {
        // 模拟耗时计算
        return key.length() * 100;
    }

    /**
     * ==================== 并发集合优化 ====================
     */

    /**
     * ❌ 反例：使用Collections.synchronizedList()
     * 
     * 问题：
     * 1. 粗粒度锁，所有方法都synchronized
     * 2. 迭代时仍需手动同步
     * 3. 读操作也会阻塞其他读操作
     */
    public List<String> badConcurrentList() {
        List<String> list = new ArrayList<>();
        return Collections.synchronizedList(list);
    }

    /**
     * ✅ 正例：使用CopyOnWriteArrayList（读多写少场景）
     * 
     * 原理：
     * - 读操作：无锁，直接读取底层数组
     * - 写操作：复制新数组，修改后替换引用
     * 
     * 适用：读操作占90%以上，写操作极少
     * 不适用：写操作频繁（每次写都要复制数组）
     */
    public void copyOnWriteBestPractice() {
        // ✅ 配置项、白名单等读多写少场景
        List<String> configList = new CopyOnWriteArrayList<>();
        
        // 写操作：复制整个数组
        configList.add("new_config");  // O(n)
        
        // 读操作：直接访问，无锁
        String config = configList.get(0);  // O(1)
    }

    /**
     * ✅ 正例：使用ConcurrentHashMap（高并发场景）
     * 
     * 原理：
     * - 分段锁（Java 7）或 CAS + synchronized（Java 8+）
     * - 读操作基本无锁（volatile保证可见性）
     * - 写操作只锁住部分桶（bucket）
     * 
     * 并发度：
     * - Java 7: 默认16个段，最多16线程并发写
     * - Java 8+: 每个桶独立加锁，并发度更高
     */
    public void concurrentHashMapBestPractice() {
        // ✅ 高并发读写场景
        ConcurrentHashMap<String, Integer> map = new ConcurrentHashMap<>();
        
        // 线程安全的原子操作
        map.put("key", 1);
        Integer value = map.get("key");
        
        // ✅ Java 8+ 新增方法
        // computeIfAbsent - 原子性判断+插入
        map.computeIfAbsent("key", k -> 1);
        
        // compute - 原子性计算
        map.compute("key", (k, v) -> v == null ? 1 : v + 1);
        
        // merge - 原子性合并
        map.merge("key", 1, Integer::sum);
    }

    /**
     * ==================== 集合遍历优化 ====================
     */

    /**
     * 遍历方式性能对比
     * 
     * 性能排序（从快到慢）：
     * 1. 普通for循环（ArrayList）- O(n)
     * 2. 增强for循环（Iterator）- O(n)
     * 3. forEach + Lambda（Java 8）- O(n)，略有函数调用开销
     * 4. Stream API - O(n)，并行流在数据量大时有优势
     * 5. 普通for循环（LinkedList）- O(n²) 极差！
     */
    public void iterationComparison() {
        List<String> list = new ArrayList<>();
        // ... 填充数据
        
        // 方式1：普通for循环（仅ArrayList适用）
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
        }
        
        // 方式2：增强for循环（通用，推荐）
        for (String item : list) {
            // 编译后转为Iterator
        }
        
        // 方式3：Iterator（需要remove时）
        Iterator<String> iterator = list.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            if (item.startsWith("del_")) {
                iterator.remove();  // ✅ 安全的删除方式
            }
        }
        
        // 方式4：forEach + Lambda（Java 8+）
        list.forEach(item -> System.out.println(item));
        
        // 方式5：Stream API（函数式操作）
        list.stream()
            .filter(item -> item.length() > 5)
            .map(String::toUpperCase)
            .collect(java.util.stream.Collectors.toList());
    }

    /**
     * ==================== 批量操作优化 ====================
 */

    /**
     * ✅ 批量添加优化
     */
    public void batchOperationOptimization() {
        List<String> source = new ArrayList<>();
        List<String> target = new ArrayList<>();
        
        // ❌ 逐个添加
        for (String item : source) {
            target.add(item);
        }
        
        // ✅ 使用addAll()批量添加
        target.addAll(source);
        
        // ✅ 构造时直接传入集合
        List<String> newList = new ArrayList<>(source);
        
        // ✅ Java 8+ Stream批量转换
        List<String> upperList = source.stream()
            .map(String::toUpperCase)
            .collect(java.util.stream.Collectors.toCollection(() -> 
                new ArrayList<>(source.size())));  // 预估容量
    }

    /**
     * ==================== 空间优化 ====================
 */

    /**
     * 使用原始类型集合避免装箱开销
     * 
     * 问题：ArrayList<Integer>每个元素都是对象，有额外开销
     * 解决方案：使用fastutil, Eclipse Collections, Trove等库
     * 
     * 内存对比（100万个int）：
     * - ArrayList<Integer>: ~20MB
     * - IntArrayList (fastutil): ~4MB
     */
    public void primitiveCollectionExample() {
        // ❌ 有装箱开销
        List<Integer> boxedList = new ArrayList<>();
        boxedList.add(100);  // 自动装箱为Integer
        
        // ✅ 使用fastutil的原始类型集合（需添加依赖）
        // IntList primitiveList = new IntArrayList();
        // primitiveList.add(100);  // 无装箱
    }

    /**
     * ==================== 集合工具类 ====================
 */

    /**
     * Guava不可变集合（Immutable Collections）
     * 
     * 优势：
     * 1. 线程安全（创建后不可变）
     * 2. 内存高效（复用单例empty集合）
     * 3. 防御性编程（防止意外修改）
     */
    public void guavaImmutableCollections() {
        // ✅ 创建不可变列表
        List<String> immutableList = com.google.common.collect.ImmutableList.of(
            "a", "b", "c"
        );
        
        // ✅ 从可变集合创建不可变副本
        List<String> mutableList = new ArrayList<>();
        List<String> copy = com.google.common.collect.ImmutableList.copyOf(mutableList);
        
        // ✅ 不可变Map
        Map<String, Integer> immutableMap = com.google.common.collect.ImmutableMap.of(
            "key1", 1,
            "key2", 2
        );
    }

    /**
     * 集合判空和初始化最佳实践
     */
    public List<String> getItems() {
        // ✅ 返回空集合而不是null
        if (condition) {
            return new ArrayList<>();  // 返回空列表
            // 或：return Collections.emptyList(); // 不可变空列表
        }
        return items;
    }
    
    /**
     * ✅ 使用Objects.requireNonNull()进行防御性编程
     */
    public void processList(List<String> list) {
        Objects.requireNonNull(list, "list must not be null");
        // 处理逻辑
    }
}