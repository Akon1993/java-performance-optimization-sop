# 代码层面性能优化指南

## 目录
1. [集合类优化](#1-集合类优化)
2. [字符串处理优化](#2-字符串处理优化)
3. [循环优化](#3-循环优化)
4. [对象创建优化](#4-对象创建优化)
5. [I/O操作优化](#5-io操作优化)

---

## 1. 集合类优化

### 1.1 集合类选择决策树

```
是否需要线程安全？
├─ 是
│  ├─ 读多写少：CopyOnWriteArrayList / CopyOnWriteArraySet
│  ├─ 高并发：ConcurrentHashMap / ConcurrentSkipListMap
│  └─ 简单同步：Collections.synchronizedXXX()（不推荐）
└─ 否
   ├─ List
   │  ├─ 随机访问多：ArrayList（O(1)）
   │  └─ 插入删除多：LinkedList（O(1)）
   ├─ Set
   │  ├─ 有序：LinkedHashSet
   │  ├─ 排序：TreeSet
   │  └─ 无序：HashSet
   └─ Map
      ├─ 有序：LinkedHashMap
      ├─ 排序：TreeMap
      └─ 无序：HashMap
```

### 1.2 ArrayList vs LinkedList

| 操作 | ArrayList | LinkedList |
|------|-----------|------------|
| get(index) | O(1) ✅ | O(n) ❌ |
| add(E) | O(1) 均摊 | O(1) ✅ |
| add(index, E) | O(n) | O(n) |
| remove(index) | O(n) | O(n) |
| remove(Object) | O(n) | O(n) |
| 内存占用 | 较少 | 较多（节点指针开销）|

**最佳实践：**
```java
// ✅ 预估容量，避免频繁扩容
List<String> list = new ArrayList<>(expectedSize);

// ✅ 批量添加前确保容量
list.ensureCapacity(expectedSize);

// ❌ 不要在LinkedList中使用索引遍历
for (int i = 0; i < linkedList.size(); i++) {  // O(n²) 性能极差！
    linkedList.get(i);
}

// ✅ 使用迭代器遍历LinkedList
for (String item : linkedList) {  // O(n)
    // process item
}
```

### 1.3 HashMap优化

**容量计算：**
```java
// 公式：initialCapacity = expectedSize / loadFactor + 1
int expectedSize = 1000;
float loadFactor = 0.75f;
int initialCapacity = (int) (expectedSize / loadFactor) + 1;

Map<String, Object> map = new HashMap<>(initialCapacity, loadFactor);
```

**Java 8+ 优化方法：**
```java
// ✅ computeIfAbsent: 避免重复计算
map.computeIfAbsent("key", k -> expensiveCompute(k));

// ✅ merge: 原子性合并
map.merge("key", 1, Integer::sum);

// ✅ getOrDefault: 避免null检查
int count = map.getOrDefault("key", 0);
```

### 1.4 并发集合选择

| 集合类 | 适用场景 | 特点 |
|--------|----------|------|
| ConcurrentHashMap | 高并发读写 | 分段锁/CAS，读无锁 |
| CopyOnWriteArrayList | 读多写少 | 写时复制，读无锁 |
| ConcurrentLinkedQueue | 高并发队列 | 无锁队列，高吞吐 |
| BlockingQueue | 生产者消费者 | 支持阻塞操作 |

---

## 2. 字符串处理优化

### 2.1 字符串拼接性能对比

**性能排序（从快到慢）：**
1. StringBuilder.append() - 单线程首选
2. StringBuffer.append() - 多线程安全
3. String.join() - Java 8+ 集合拼接
4. String += - 编译器自动转为StringBuilder，但循环中每次都会创建新对象

**基准测试结果（拼接10000次）：**
- String += : ~1000ms ❌
- StringBuilder: ~0.1ms ✅
- 性能差异: 10000倍以上

### 2.2 最佳实践

```java
// ✅ 单线程使用StringBuilder
StringBuilder sb = new StringBuilder(expectedLength);
for (int i = 0; i < count; i++) {
    sb.append(i).append(",");
}
String result = sb.toString();

// ✅ Java 8+ 集合转字符串
String result = String.join(",", list);

// ✅ 复杂场景使用StringJoiner
StringJoiner joiner = new StringJoiner(",", "[", "]");
list.forEach(joiner::add);
```

### 2.3 字符串分割优化

```java
// ❌ 避免使用split处理大文本（正则编译开销）
String[] parts = text.split(",");

// ✅ 使用Guava Splitter（推荐）
List<String> parts = Splitter.on(',')
    .omitEmptyStrings()
    .trimResults()
    .splitToList(text);

// ✅ 简单场景使用StringTokenizer
StringTokenizer tokenizer = new StringTokenizer(text, ",");
while (tokenizer.hasMoreTokens()) {
    String token = tokenizer.nextToken();
}
```

### 2.4 String.intern() 使用建议

```java
// ✅ 适用场景：状态码、枚举值等有限常量
String status = "ACTIVE".intern();

// ❌ 不适用：动态生成的字符串
String dynamic = ("user_" + System.currentTimeMillis()).intern();  // 不要这样做

// ✅ 替代方案：使用Guava Interner
private static final Interner<String> STRING_INTERNER = 
    Interners.newWeakInterner();
```

---

## 3. 循环优化

### 3.1 循环条件优化

```java
// ❌ 每次循环都调用size()
for (int i = 0; i < list.size(); i++) { }

// ✅ 提取size到局部变量
for (int i = 0, size = list.size(); i < size; i++) { }

// ✅ 倒序循环（某些CPU架构更快）
for (int i = list.size() - 1; i >= 0; i--) { }
```

### 3.2 避免在循环中创建对象

```java
// ❌ 每次循环都创建对象
for (Integer num : numbers) {
    StringBuilder sb = new StringBuilder();  // ❌
    sb.append(num);
}

// ✅ 对象创建移到循环外部
StringBuilder sb = new StringBuilder();
for (Integer num : numbers) {
    sb.append(num);
}
```

### 3.3 Stream API使用指南

```java
// ❌ 装箱版本（较慢）
int sum = list.stream()
    .map(n -> n * n)
    .reduce(0, Integer::sum);

// ✅ 原始类型版本（推荐）
int sum = list.stream()
    .mapToInt(n -> n * n)  // 转为IntStream
    .sum();

// ✅ 并行流（大数据量时使用）
int parallelSum = largeList.parallelStream()
    .mapToInt(n -> n * n)
    .sum();
```

---

## 4. 对象创建优化

### 4.1 基本类型 vs 包装类

```java
// ❌ 使用包装类（有装箱开销）
Integer sum = 0;
for (Integer i = 0; i < 1000000; i++) {
    sum += i;  // 自动装箱/拆箱
}

// ✅ 使用基本类型
int sum = 0;
for (int i = 0; i < 1000000; i++) {
    sum += i;  // 无装箱开销
}
```

### 4.2 对象复用

```java
// ✅ 使用ThreadLocal复用非线程安全对象
private static final ThreadLocal<SimpleDateFormat> DATE_FORMAT = 
    ThreadLocal.withInitial(() -> new SimpleDateFormat("yyyy-MM-dd"));

// ✅ Java 8+ 使用DateTimeFormatter（线程安全）
private static final DateTimeFormatter FORMATTER = 
    DateTimeFormatter.ofPattern("yyyy-MM-dd");
```

### 4.3 静态工厂方法

```java
// ✅ 使用静态工厂代替构造函数
Boolean.valueOf(true);  // 返回缓存对象
Boolean.valueOf("true");  // 同上

// ✅ 使用缓存的不可变对象
List<String> emptyList = Collections.emptyList();
Set<String> emptySet = Collections.emptySet();
```

---

## 5. I/O操作优化

### 5.1 缓冲区使用

```java
// ❌ 无缓冲，每次读取一个字节
InputStream in = new FileInputStream(file);
int b;
while ((b = in.read()) != -1) { }

// ✅ 使用BufferedInputStream
InputStream in = new BufferedInputStream(new FileInputStream(file));
byte[] buffer = new byte[8192];
int len;
while ((len = in.read(buffer)) != -1) { }

// ✅ Java 7+ 使用NIO.2
Path path = Paths.get("file.txt");
byte[] content = Files.readAllBytes(path);
```

### 5.2 批量操作

```java
// ❌ 逐行读取
BufferedReader reader = new BufferedReader(new FileReader(file));
String line;
while ((line = reader.readLine()) != null) {
    process(line);
}

// ✅ 使用内存映射文件（大文件）
try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
    MappedByteBuffer buffer = channel.map(MapMode.READ_ONLY, 0, channel.size());
    // 处理buffer
}
```

---

## 6. 代码审查检查清单

### 6.1 集合使用检查
- [ ] 是否选择了合适的集合类？
- [ ] ArrayList是否预估了容量？
- [ ] HashMap是否预估了容量？
- [ ] 是否在LinkedList中使用索引遍历？
- [ ] 并发场景是否使用了合适的并发集合？

### 6.2 字符串处理检查
- [ ] 循环中是否使用了StringBuilder？
- [ ] 是否避免了在循环中使用+=拼接字符串？
- [ ] 大文本分割是否使用了高效的工具？

### 6.3 循环优化检查
- [ ] 循环条件中是否避免了方法调用？
- [ ] 循环内部是否避免了对象创建？
- [ ] 是否使用了合适的数据结构遍历方式？

### 6.4 对象创建检查
- [ ] 是否优先使用基本类型？
- [ ] 是否避免了自动装箱/拆箱？
- [ ] 是否可以复用对象？

---

## 7. 相关代码示例

- [CollectionOptimization.java](../src/main/java/com/perf/sop/code/collection/CollectionOptimization.java)
- [StringOptimization.java](../src/main/java/com/perf/sop/code/string/StringOptimization.java)
- [LoopOptimization.java](../src/main/java/com/perf/sop/code/loop/LoopOptimization.java)
