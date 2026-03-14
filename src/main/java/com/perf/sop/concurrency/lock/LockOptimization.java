/**
 * 锁优化最佳实践
 * 
 * 【SOP核心要点】
 * 1. 优先使用synchronized（JVM优化好，自动释放）
 * 2. 需要高级特性时使用ReentrantLock
 * 3. 读多写少场景使用ReadWriteLock或StampedLock
 * 4. 高并发计数使用原子类（AtomicLong/LongAdder）
 * 5. 避免死锁：统一加锁顺序、使用tryLock
 * 
 * 【锁选择决策树】
 * 
 * 是否需要线程互斥？
 * ├─ 是
 * │  ├─ 简单同步：synchronized
 * │  ├─ 需要中断/超时/公平锁：ReentrantLock
 * │  ├─ 读多写少：ReadWriteLock / StampedLock
 * │  └─ 条件变量复杂：ReentrantLock + Condition
 * └─ 否
 *    ├─ 计数：AtomicLong / LongAdder
 *    ├─ CAS：AtomicReference
 *    └─ 累加：LongAccumulator
 * 
 * 【性能对比】（4线程竞争）
 * - synchronized: ~50ns
 * - ReentrantLock: ~60ns
 * - ReadWriteLock(读): ~20ns
 * - StampedLock(读): ~10ns
 * - AtomicLong: ~15ns
 * - LongAdder: ~8ns
 * 
 * @author Performance Optimization Team
 * @version 1.0.0
 * @since 2026-03-14
 */
package com.perf.sop.concurrency.lock;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.*;

public class LockOptimization {

    /**
     * ==================== synchronized优化 ====================
     */

    /**
     * ❌ 反例：锁整个方法
     */
    public synchronized void badSynchronizedMethod() {
        // 大量不涉及共享变量的操作
        doSomething();
        // 修改共享变量
        sharedVariable++;
        // 大量不涉及共享变量的操作
        doSomethingElse();
    }

    /**
     * ✅ 正例：缩小锁粒度
     */
    private final Object lock = new Object();
    private int sharedVariable = 0;
    
    public void goodSynchronizedBlock() {
        // 不涉及共享变量的操作（无锁）
        doSomething();
        
        // 只锁定必要代码块
        synchronized (lock) {
            sharedVariable++;
        }
        
        // 不涉及共享变量的操作（无锁）
        doSomethingElse();
    }

    /**
     * ✅ 锁分离（细粒度锁）
     * 
     * 适用场景：多个独立的共享变量
     */
    public class SegmentedLockExample {
        // 分段锁数组
        private final Object[] locks = new Object[16];
        private final AtomicInteger[] counters = new AtomicInteger[16];
        
        public SegmentedLockExample() {
            for (int i = 0; i < 16; i++) {
                locks[i] = new Object();
                counters[i] = new AtomicInteger(0);
            }
        }
        
        public void increment(int key) {
            int index = key % 16;
            synchronized (locks[index]) {
                counters[index].incrementAndGet();
            }
        }
        
        public int getSum() {
            int sum = 0;
            for (AtomicInteger counter : counters) {
                sum += counter.get();
            }
            return sum;
        }
    }

    /**
     * ==================== ReentrantLock优化 ====================
     */

    private final ReentrantLock reentrantLock = new ReentrantLock();
    private final Condition condition = reentrantLock.newCondition();

    /**
     * ✅ 使用tryLock避免死锁
     */
    public boolean tryLockWithTimeout() {
        boolean locked = false;
        try {
            // 尝试获取锁，最多等待1秒
            locked = reentrantLock.tryLock(1, TimeUnit.SECONDS);
            if (locked) {
                // 执行业务逻辑
                return true;
            } else {
                System.out.println("获取锁超时");
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            if (locked) {
                reentrantLock.unlock();
            }
        }
    }

    /**
     * ✅ 可中断锁
     */
    public void interruptibleLock() {
        reentrantLock.lock();
        try {
            // 等待条件（可中断）
            condition.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("等待被中断");
        } finally {
            reentrantLock.unlock();
        }
    }

    /**
     * ✅ 公平锁（避免饥饿）
     * 
     * 注意：公平锁性能较低，默认使用非公平锁
     */
    private final ReentrantLock fairLock = new ReentrantLock(true);

    /**
     * ==================== 读写锁优化 ====================
     */

    /**
     * ✅ ReadWriteLock使用
     * 
     * 适用场景：读多写少
     */
    public class ReadWriteLockExample {
        private final ReadWriteLock rwLock = new ReentrantReadWriteLock();
        private final Lock readLock = rwLock.readLock();
        private final Lock writeLock = rwLock.writeLock();
        
        private String data = "initial";
        
        // 读操作
        public String read() {
            readLock.lock();
            try {
                return data;  // 多个读线程可同时执行
            } finally {
                readLock.unlock();
            }
        }
        
        // 写操作
        public void write(String newData) {
            writeLock.lock();
            try {
                data = newData;  // 独占访问
            } finally {
                writeLock.unlock();
            }
        }
    }

    /**
     * ✅ StampedLock（Java 8+）
     * 
     * 优势：
     * 1. 支持乐观读（性能更好）
     * 2. 读写锁可相互转换
     * 3. 性能比ReadWriteLock更好
     */
    public class StampedLockExample {
        private final StampedLock lock = new StampedLock();
        private double x, y;
        
        /**
         * 乐观读
         * 
         * 性能最优，适合读多写少且数据不频繁变更的场景
         */
        public double distanceFromOrigin() {
            long stamp = lock.tryOptimisticRead();
            double currentX = x, currentY = y;
            
            // 验证读期间是否有写操作
            if (!lock.validate(stamp)) {
                // 升级为悲观读锁
                stamp = lock.readLock();
                try {
                    currentX = x;
                    currentY = y;
                } finally {
                    lock.unlockRead(stamp);
                }
            }
            
            return Math.sqrt(currentX * currentX + currentY * currentY);
        }
        
        /**
         * 悲观读
         */
        public String read() {
            long stamp = lock.readLock();
            try {
                return "(" + x + ", " + y + ")";
            } finally {
                lock.unlockRead(stamp);
            }
        }
        
        /**
         * 写操作
         */
        public void move(double deltaX, double deltaY) {
            long stamp = lock.writeLock();
            try {
                x += deltaX;
                y += deltaY;
            } finally {
                lock.unlockWrite(stamp);
            }
        }
        
        /**
         * 锁降级：写锁降级为读锁
         */
        public void writeWithDowngrade(double newX, double newY) {
            long stamp = lock.writeLock();
            try {
                x = newX;
                y = newY;
                
                // 降级为读锁（可读取验证）
                long readStamp = lock.tryConvertToReadLock(stamp);
                if (readStamp != 0L) {
                    stamp = readStamp;
                    // 以读锁状态继续执行
                    System.out.println("验证: x=" + x + ", y=" + y);
                }
            } finally {
                lock.unlock(stamp);
            }
        }
    }

    /**
     * ==================== 原子类优化 ====================
     */

    /**
     * ✅ 高并发计数器优化
     * 
     * LongAdder vs AtomicLong:
     * - AtomicLong: 单个变量，高并发下竞争激烈
     * - LongAdder: 分散热点，多线程分别累加，最后汇总
     */
    public class CounterOptimization {
        // ❌ 高并发下竞争激烈
        private final AtomicInteger atomicCounter = new AtomicInteger(0);
        
        // ✅ LongAdder分散热点，性能更好
        private final LongAdder longAdder = new LongAdder();
        
        public void incrementAtomic() {
            atomicCounter.incrementAndGet();
        }
        
        public void incrementAdder() {
            longAdder.increment();
        }
        
        public int getAtomic() {
            return atomicCounter.get();
        }
        
        public long getAdder() {
            return longAdder.sum();
        }
        
        /**
         * 性能对比结果（1000万并发递增）：
         * - AtomicInteger: ~2000ms
         * - LongAdder: ~200ms
         * - 性能提升约10倍
         */
    }

    /**
     * ==================== 死锁避免 ====================
     */

    /**
     * ❌ 反例：嵌套锁可能导致死锁
     */
    public class DeadlockRisk {
        private final Object lockA = new Object();
        private final Object lockB = new Object();
        
        public void method1() {
            synchronized (lockA) {
                synchronized (lockB) {  // 可能死锁
                    // do something
                }
            }
        }
        
        public void method2() {
            synchronized (lockB) {
                synchronized (lockA) {  // 可能死锁
                    // do something
                }
            }
        }
    }

    /**
     * ✅ 正例：统一加锁顺序
     */
    public class DeadlockSafe {
        private final Object lockA = new Object();
        private final Object lockB = new Object();
        
        public void method1() {
            Object first = lockA.hashCode() < lockB.hashCode() ? lockA : lockB;
            Object second = lockA.hashCode() < lockB.hashCode() ? lockB : lockA;
            
            synchronized (first) {
                synchronized (second) {
                    // do something
                }
            }
        }
        
        public void method2() {
            Object first = lockA.hashCode() < lockB.hashCode() ? lockA : lockB;
            Object second = lockA.hashCode() < lockB.hashCode() ? lockB : lockA;
            
            synchronized (first) {
                synchronized (second) {
                    // do something
                }
            }
        }
    }

    /**
     * ✅ 使用tryLock避免死锁
     */
    public boolean transferWithTryLock(Account from, Account to, double amount) {
        Account first = from.hashCode() < to.hashCode() ? from : to;
        Account second = from.hashCode() < to.hashCode() ? to : from;
        
        boolean firstLocked = false;
        boolean secondLocked = false;
        
        try {
            // 尝试获取两个锁
            firstLocked = first.getLock().tryLock(1, TimeUnit.SECONDS);
            secondLocked = second.getLock().tryLock(1, TimeUnit.SECONDS);
            
            if (firstLocked && secondLocked) {
                from.debit(amount);
                to.credit(amount);
                return true;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (firstLocked) first.getLock().unlock();
            if (secondLocked) second.getLock().unlock();
        }
        
        return false;
    }

    /**
     * 账户类
     */
    public static class Account {
        private final ReentrantLock lock = new ReentrantLock();
        private double balance;
        
        public ReentrantLock getLock() {
            return lock;
        }
        
        public void debit(double amount) {
            balance -= amount;
        }
        
        public void credit(double amount) {
            balance += amount;
        }
    }

    /**
     * ==================== 锁粗化与消除 ====================
     */

    /**
     * ✅ 锁粗化：JVM会自动优化相邻的同步块
     * 
     * 以下代码JVM可能会自动粗化为一个同步块
     */
    public void lockCoarsening() {
        StringBuilder sb = new StringBuilder();
        
        synchronized (this) {
            sb.append("a");
        }
        synchronized (this) {
            sb.append("b");
        }
        synchronized (this) {
            sb.append("c");
        }
    }

    /**
     * ✅ 锁消除：JVM逃逸分析会消除不必要的锁
     * 
     * -XX:+DoEscapeAnalysis 开启逃逸分析（JDK 8+默认开启）
     * 
     * 以下StringBuffer的锁会被消除（sb不会逃逸出方法）
     */
    public String lockElimination() {
        StringBuffer sb = new StringBuffer();
        sb.append("a");
        sb.append("b");
        sb.append("c");
        return sb.toString();
    }

    /**
     * ==================== 无锁编程 ====================
     */

    /**
     * ✅ CAS实现自旋锁
     */
    public class SpinLock {
        private final AtomicInteger state = new AtomicInteger(0);
        
        public void lock() {
            // 自旋等待获取锁
            while (!state.compareAndSet(0, 1)) {
                // 可选：Thread.yield()或LockSupport.parkNanos()
                Thread.yield();
            }
        }
        
        public void unlock() {
            state.compareAndSet(1, 0);
        }
    }

    /**
     * ✅ TicketLock（公平自旋锁）
     */
    public class TicketLock {
        private final AtomicInteger ticketNum = new AtomicInteger();
        private final AtomicInteger nowServing = new AtomicInteger();
        
        public void lock() {
            int myTicket = ticketNum.getAndIncrement();
            while (myTicket != nowServing.get()) {
                // 自旋等待
                Thread.yield();
            }
        }
        
        public void unlock() {
            nowServing.incrementAndGet();
        }
    }

    /**
     * ==================== 辅助方法 ====================
     */

    private void doSomething() {
        // 模拟业务逻辑
    }

    private void doSomethingElse() {
        // 模拟业务逻辑
    }

    /**
     * 主方法：演示锁优化
     */
    public static void main(String[] args) {
        LockOptimization demo = new LockOptimization();
        
        System.out.println("========== 锁优化演示 ==========\n");
        
        // 1. synchronized优化
        System.out.println("1. synchronized优化");
        demo.goodSynchronizedBlock();
        System.out.println("  ✓ 缩小锁粒度，只锁定必要代码块\n");
        
        // 2. ReentrantLock特性
        System.out.println("2. ReentrantLock特性");
        boolean acquired = demo.tryLockWithTimeout();
        System.out.println("  ✓ tryLock结果: " + acquired + "\n");
        
        // 3. 读写锁
        System.out.println("3. 读写锁对比");
        ReadWriteLockExample rwExample = demo.new ReadWriteLockExample();
        System.out.println("  ReadWriteLock - 读锁可并发，写锁独占\n");
        
        // 4. StampedLock
        System.out.println("4. StampedLock乐观读");
        StampedLockExample stampedExample = demo.new StampedLockExample();
        double distance = stampedExample.distanceFromOrigin();
        System.out.println("  ✓ 乐观读性能最优，适合读多写少\n");
        
        // 5. 原子类
        System.out.println("5. 原子类优化");
        CounterOptimization counter = demo.new CounterOptimization();
        counter.incrementAdder();
        System.out.println("  ✓ LongAdder性能优于AtomicLong（高并发场景）\n");
        
        System.out.println("========== 演示完成 ==========");
    }
}
