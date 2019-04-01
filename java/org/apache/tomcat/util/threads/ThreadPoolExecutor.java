/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tomcat.util.threads;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.tomcat.util.res.StringManager;

/**
 * Same as a java.util.concurrent.ThreadPoolExecutor but implements a much more efficient
 * {@link #getSubmittedCount()} method, to be used to properly handle the work queue.
 * If a RejectedExecutionHandler is not specified a default one will be configured
 * and that one will always throw a RejectedExecutionException
 * <p>
 * 与java.util.concurrent相同。但是实现了一个更有效的getSubmittedCount()方法，用于正确处理工作队列。
 * 如果没有指定RejectedExecutionHandler，则将配置一个默认处理程序，该处理程序将始终抛出RejectedExecutionException异常
 */
public class ThreadPoolExecutor extends java.util.concurrent.ThreadPoolExecutor {
    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager
            .getManager("org.apache.tomcat.util.threads.res");

    /**
     * The number of tasks submitted but not yet finished. This includes tasks
     * in the queue and tasks that have been handed to a worker thread but the
     * latter did not start executing the task yet.
     * This number is always greater or equal to {@link #getActiveCount()}.
     */
    private final AtomicInteger submittedCount = new AtomicInteger(0);
    private final AtomicLong lastContextStoppedTime = new AtomicLong(0L);

    /**
     * Most recent time in ms when a thread decided to kill itself to avoid
     * potential memory leaks. Useful to throttle the rate of renewals of
     * threads.
     * <p>
     * 而我们可以看到lastTimeThreadKillItSelf其使用CAS的原因了，compareAndSet，
     * 说明在该处存在高并发对这个变量的操作，当发现超过时间戳之后，在Tomcat中会有n个任务同时可能进行到这里；
     * <p>
     * 因此这里又不能用互斥锁，索性用乐观锁的话，会大大提升效率，因为这里面的流转是非常快的，应该是乐观的，
     * 适当提升提升cpu是可以解决的，无需放弃时间片，因此用独占锁不太合适；
     */
    private final AtomicLong lastTimeThreadKilledItself = new AtomicLong(0L);

    /**
     * Delay in ms between 2 threads being renewed. If negative, do not renew threads.
     */
    private long threadRenewalDelay = Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, handler);
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory,
                              RejectedExecutionHandler handler) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, handler);
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue, ThreadFactory threadFactory) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, threadFactory, new RejectHandler());
        prestartAllCoreThreads();
    }

    public ThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit unit, BlockingQueue<Runnable> workQueue) {
        super(corePoolSize, maximumPoolSize, keepAliveTime, unit, workQueue, new RejectHandler());
        prestartAllCoreThreads();
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
    }

    @Override
    protected void afterExecute(Runnable r, Throwable t) {
        submittedCount.decrementAndGet();

        if (t == null) {
            stopCurrentThreadIfNeeded();
        }
    }

    /**
     * If the current thread was started before the last time when a context was
     * stopped, an exception is thrown so that the current thread is stopped.
     * <p>
     * 如果当前线程是在上一次停止上下文之前启动的，则抛出异常，以便当前线程停止。
     * <p>
     * 其次，每一次是lastTimeThreadKillItSelf + theadRenewalDelay 与当前的时间进行比对，
     * 如果超出了theadRenewalDelay 的秒数，说明线程是在间隔进行kill的，通过这样的方式就可以让线程renew的压力小一些；
     */
    protected void stopCurrentThreadIfNeeded() {
        if (currentThreadShouldBeStopped()) {
            long lastTime = lastTimeThreadKilledItself.longValue();
            if (lastTime + threadRenewalDelay < System.currentTimeMillis()) {
                if (lastTimeThreadKilledItself.compareAndSet(lastTime,
                        System.currentTimeMillis() + 1)) {
                    // OK, it's really time to dispose of this thread

                    final String msg = sm.getString(
                            "threadPoolExecutor.threadStoppedToAvoidPotentialLeak",
                            Thread.currentThread().getName());

                    // 抛出异常 打断当前线程
                    throw new StopPooledThreadException(msg);
                }
            }
        }
    }

    /**
     * 但是，在这个时候，如果一股脑的一下子重新renew线程，cpu直接就蹭蹭的上来了，这种情况我们应该要匀的乎的让线程慢慢的都被renew；
     * <p>
     * 而这个theadRenewalDelay的意思就是，每一次搞1个线程，然后下一次搞之前间隔多少时间，这个就是theadRenewalDelay的配置了；
     */
    protected boolean currentThreadShouldBeStopped() {
        if (threadRenewalDelay >= 0
                && Thread.currentThread() instanceof TaskThread) {
            TaskThread currentTaskThread = (TaskThread) Thread.currentThread();
            if (currentTaskThread.getCreationTime() <
                    this.lastContextStoppedTime.longValue()) {
                return true;
            }
        }
        return false;
    }

    public int getSubmittedCount() {
        return submittedCount.get();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(Runnable command) {
        execute(command, 0, TimeUnit.MILLISECONDS);
    }

    /**
     * Executes the given command at some time in the future.  The command
     * may execute in a new thread, in a pooled thread, or in the calling
     * thread, at the discretion of the <tt>Executor</tt> implementation.
     * If no threads are available, it will be added to the work queue.
     * If the work queue is full, the system will wait for the specified
     * time and it throw a RejectedExecutionException if the queue is still
     * full after that.
     * <p>
     * 在将来的某个时候执行给定的命令。命令可以在新线程、池化线程或调用线程中执行，具体由执行器实现决定。
     * 如果没有可用的线程，它将被添加到工作队列中。如果工作队列已满，系统将等待指定的时间，
     * 如果队列在此之后仍然满，则抛出RejectedExecutionException。
     *
     * @param command the runnable task
     * @param timeout A timeout for the completion of the task
     * @param unit    The timeout time unit
     * @throws RejectedExecutionException if this task cannot be
     *                                    accepted for execution - the queue is full
     * @throws NullPointerException       if command or unit is null
     */
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        submittedCount.incrementAndGet();
        try {
            super.execute(command);
        } catch (RejectedExecutionException rx) {
            if (super.getQueue() instanceof TaskQueue) {
                final TaskQueue queue = (TaskQueue) super.getQueue();
                try {
                    if (!queue.force(command, timeout, unit)) {
                        submittedCount.decrementAndGet();
                        throw new RejectedExecutionException(sm.getString("threadPoolExecutor.queueFull"));
                    }
                } catch (InterruptedException x) {
                    submittedCount.decrementAndGet();
                    throw new RejectedExecutionException(x);
                }
            } else {
                submittedCount.decrementAndGet();
                throw rx;
            }

        }
    }

    /**
     * 从头来说，ThreadLocalLeakPreventionListener 配置了，可以监听应用停止，我们前面知道会触发contextStopping方法；
     * <p>
     * 在contextStopping方法中，线程池中的空闲线程会重新干掉，然后再创建，其目的就是为了将ThreadLocal等缓存清空，减轻线程负担
     */
    public void contextStopping() {
        this.lastContextStoppedTime.set(System.currentTimeMillis());

        // save the current pool parameters to restore them later
        int savedCorePoolSize = this.getCorePoolSize();
        TaskQueue taskQueue =
                getQueue() instanceof TaskQueue ? (TaskQueue) getQueue() : null;
        if (taskQueue != null) {
            // note by slaurent : quite oddly threadPoolExecutor.setCorePoolSize
            // checks that queue.remainingCapacity()==0. I did not understand
            // why, but to get the intended effect of waking up idle threads, I
            // temporarily fake this condition.
            taskQueue.setForcedRemainingCapacity(Integer.valueOf(0));
        }

        // setCorePoolSize(0) wakes idle threads
        this.setCorePoolSize(0);

        // TaskQueue.take() takes care of timing out, so that we are sure that
        // all threads of the pool are renewed in a limited time, something like
        // (threadKeepAlive + longest request time)

        if (taskQueue != null) {
            // ok, restore the state of the queue and pool
            taskQueue.setForcedRemainingCapacity(null);
        }
        this.setCorePoolSize(savedCorePoolSize);
    }

    private static class RejectHandler implements RejectedExecutionHandler {
        @Override
        public void rejectedExecution(Runnable r,
                                      java.util.concurrent.ThreadPoolExecutor executor) {
            throw new RejectedExecutionException();
        }

    }


}
