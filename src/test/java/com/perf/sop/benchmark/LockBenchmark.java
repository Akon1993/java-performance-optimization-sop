/**
 * JMH基准测试 - 锁性能对比
 * 
 * 【测试内容】
 * 1. synchronized vs ReentrantLock
 * 2. ReadWriteLock 读锁性能
 * 3. StampedLock 乐观读性能
 * 4. AtomicLong vs LongAdder
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.benchmark;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.*;

@BenchmarkMode(Mode.Throughput)           // 测试吞吐量
@OutputTimeUnit(TimeUnit.MILLISECONDS)    // 每毫秒操作数
@State(Scope.Benchmark)                   // 所有线程共享
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@Threads(4)                               // 4个并发线程
public class LockBenchmark {

    private long counter = 0;
    private final Object syncLock = new Object();
    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
    private final StampedLock stampedLock = new StampedLock();
    private final AtomicLong atomicLong = new AtomicLong(0);
    private final LongAdder longAdder = new LongAdder();

    /**
     * synchronized
     */
    @Benchmark
    public long synchronizedIncrement() {
        synchronized (syncLock) {
            return ++counter;
        }
    }

    /**
     * ReentrantLock
     */
    @Benchmark
    public long reentrantLockIncrement() {
        reentrantLock.lock();
        try {
            return ++counter;
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * ReadWriteLock 读锁
     */
    @Benchmark
    public long readWriteLockRead() {
        Lock lock = rwLock.readLock();
        lock.lock();
        try {
            return counter;
        } finally {
            lock.unlock();
        }
    }

    /**
     * StampedLock 乐观读
     */
    @Benchmark
    public long stampedLockOptimisticRead() {
        long stamp = stampedLock.tryOptimisticRead();
        long value = counter;
        if (!stampedLock.validate(stamp)) {
            stamp = stampedLock.readLock();
            try {
                value = counter;
            } finally {
                stampedLock.unlockRead(stamp);
            }
        }
        return value;
    }

    /**
     * StampedLock 悲观读
     */
    @Benchmark
    public long stampedLockRead() {
        long stamp = stampedLock.readLock();
        try {
            return counter;
        } finally {
            stampedLock.unlockRead(stamp);
        }
    }

    /**
     * AtomicLong
     */
    @Benchmark
    public long atomicLongIncrement() {
        return atomicLong.incrementAndGet();
    }

    /**
     * LongAdder
     */
    @Benchmark
    public void longAdderIncrement() {
        longAdder.increment();
    }

    /**
     * 无锁（volatile，非线程安全，仅作参考）
     */
    private volatile long volatileCounter = 0;
    
    @Benchmark
    public long volatileIncrement() {
        return ++volatileCounter;  // 非线程安全
    }

    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(LockBenchmark.class.getSimpleName())
            .output("target/jmh-lock.log")
            .build();
        
        new Runner(options).run();
    }
}
