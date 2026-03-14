/**
 * JMH基准测试 - 集合类性能对比
 * 
 * 【测试内容】
 * 1. ArrayList vs LinkedList 遍历性能
 * 2. HashMap vs ConcurrentHashMap 读写性能
 * 3. 不同遍历方式性能对比
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.results.format.ResultFormatType;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class CollectionBenchmark {

    private static final int SIZE = 10000;
    
    private List<Integer> arrayList;
    private List<Integer> linkedList;
    private Map<String, Object> hashMap;
    private Map<String, Object> concurrentHashMap;
    private String[] keys;

    @Setup
    public void setup() {
        // 准备测试数据
        arrayList = new ArrayList<>(SIZE);
        linkedList = new LinkedList<>();
        hashMap = new HashMap<>(SIZE);
        concurrentHashMap = new ConcurrentHashMap<>(SIZE);
        keys = new String[SIZE];
        
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(i);
            linkedList.add(i);
            
            String key = "key" + i;
            keys[i] = key;
            hashMap.put(key, i);
            concurrentHashMap.put(key, i);
        }
    }

    /**
     * ArrayList索引遍历（最优）
     */
    @Benchmark
    public int arrayListIndexedIteration() {
        int sum = 0;
        for (int i = 0; i < arrayList.size(); i++) {
            sum += arrayList.get(i);
        }
        return sum;
    }

    /**
     * ArrayList增强for循环
     */
    @Benchmark
    public int arrayListEnhancedIteration() {
        int sum = 0;
        for (Integer value : arrayList) {
            sum += value;
        }
        return sum;
    }

    /**
     * ArrayList迭代器遍历
     */
    @Benchmark
    public int arrayListIteratorIteration() {
        int sum = 0;
        Iterator<Integer> it = arrayList.iterator();
        while (it.hasNext()) {
            sum += it.next();
        }
        return sum;
    }

    /**
     * ArrayList Stream遍历
     */
    @Benchmark
    public int arrayListStreamIteration() {
        return arrayList.stream().mapToInt(Integer::intValue).sum();
    }

    /**
     * LinkedList索引遍历（性能极差）
     */
    @Benchmark
    public int linkedListIndexedIteration() {
        int sum = 0;
        for (int i = 0; i < linkedList.size(); i++) {
            sum += linkedList.get(i);
        }
        return sum;
    }

    /**
     * LinkedList增强for循环（推荐）
     */
    @Benchmark
    public int linkedListEnhancedIteration() {
        int sum = 0;
        for (Integer value : linkedList) {
            sum += value;
        }
        return sum;
    }

    /**
     * HashMap遍历
     */
    @Benchmark
    public int hashMapIteration() {
        int sum = 0;
        for (Map.Entry<String, Object> entry : hashMap.entrySet()) {
            sum += (Integer) entry.getValue();
        }
        return sum;
    }

    /**
     * HashMap get操作
     */
    @Benchmark
    public Object hashMapGet() {
        return hashMap.get(keys[SIZE / 2]);
    }

    /**
     * ConcurrentHashMap get操作
     */
    @Benchmark
    public Object concurrentHashMapGet() {
        return concurrentHashMap.get(keys[SIZE / 2]);
    }

    /**
     * ConcurrentHashMap put操作
     */
    @Benchmark
    public Object concurrentHashMapPut() {
        String key = "new" + System.nanoTime();
        return concurrentHashMap.put(key, 1);
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(CollectionBenchmark.class.getSimpleName())
            .output("target/jmh-collection.log")
            .result("target/jmh-collection.json")
            .resultFormat(ResultFormatType.JSON)
            .build();
        
        new Runner(options).run();
    }
}
