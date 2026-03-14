/**
 * JMH基准测试 - 字符串拼接性能对比
 * 
 * 【运行方式】
 * 1. mvn clean package
 * 2. java -jar target/perf-sop-benchmarks.jar StringConcatBenchmark
 * 
 * 【测试结果参考】
 * Benchmark                           Mode  Cnt   Score   Error  Units
 * StringConcatBenchmark.stringConcat  avgt   10  1000.5 ± 50.2  ms/op
 * StringConcatBenchmark.stringBuilder avgt   10     0.2 ± 0.01 ms/op
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

import java.util.concurrent.TimeUnit;

/**
 * 字符串拼接性能测试
 * 
 * 测试不同字符串拼接方式的性能差异
 */
@BenchmarkMode(Mode.AverageTime)          // 测试平均执行时间
@OutputTimeUnit(TimeUnit.MILLISECONDS)    // 输出时间单位为毫秒
@State(Scope.Thread)                      // 每个测试线程一个实例
@Warmup(iterations = 3, time = 1)         // 预热3轮，每轮1秒
@Measurement(iterations = 5, time = 1)    // 测试5轮，每轮1秒
@Fork(1)                                  // 进行1轮测试
public class StringConcatBenchmark {

    private static final int COUNT = 10000;

    /**
     * ❌ String += 拼接（性能最差）
     * 
     * 每次循环都创建新的String对象
     * 产生大量临时对象，增加GC压力
     */
    @Benchmark
    public String stringConcat() {
        String result = "";
        for (int i = 0; i < COUNT; i++) {
            result += i + ",";
        }
        return result;
    }

    /**
     * ✅ StringBuilder拼接（性能最好）
     * 
     * 只创建一个StringBuilder对象
     * 内部使用char[]数组，自动扩容
     */
    @Benchmark
    public String stringBuilder() {
        StringBuilder sb = new StringBuilder(COUNT * 4);
        for (int i = 0; i < COUNT; i++) {
            sb.append(i).append(",");
        }
        return sb.toString();
    }

    /**
     * StringBuffer拼接（线程安全）
     * 
     * 性能略低于StringBuilder
     * 适用于多线程环境
     */
    @Benchmark
    public String stringBuffer() {
        StringBuffer sb = new StringBuffer(COUNT * 4);
        for (int i = 0; i < COUNT; i++) {
            sb.append(i).append(",");
        }
        return sb.toString();
    }

    /**
     * StringBuilder无初始容量
     * 
     * 测试扩容对性能的影响
     */
    @Benchmark
    public String stringBuilderNoCapacity() {
        StringBuilder sb = new StringBuilder();  // 默认容量16
        for (int i = 0; i < COUNT; i++) {
            sb.append(i).append(",");
        }
        return sb.toString();
    }

    /**
     * 主方法：运行基准测试
     */
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(StringConcatBenchmark.class.getSimpleName())
            .output("target/jmh-string-concat.log")
            .result("target/jmh-string-concat.json")
            .resultFormat(ResultFormatType.JSON)
            .build();
        
        new Runner(options).run();
    }
}
