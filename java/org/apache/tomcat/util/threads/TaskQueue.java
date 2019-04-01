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

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.tomcat.util.res.StringManager;

/**
 * As task queue specifically designed to run with a thread pool executor. The
 * task queue is optimised to properly utilize threads within a thread pool
 * executor. If you use a normal queue, the executor will spawn threads when
 * there are idle threads and you wont be able to force items onto the queue
 * itself.
 * <p>
 * 作为任务队列，特别设计为使用线程池执行器运行。任务队列经过优化，以正确利用线程池执行器中的线程。
 * 如果使用普通队列，执行器将在有空闲线程时派生线程，并且无法将项强制到队列本身。
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    private static final long serialVersionUID = 1L;
    protected static final StringManager sm = StringManager
            .getManager("org.apache.tomcat.util.threads.res");

    private transient volatile ThreadPoolExecutor parent = null;

    // No need to be volatile. This is written and read in a single thread
    // (when stopping a context and firing the  listeners)
    private Integer forcedRemainingCapacity = null;

    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }

    /**
     * 将项强制放到队列中，如果任务被拒绝，则使用该队列
     */
    public boolean force(Runnable o) {
        if (parent == null || parent.isShutdown())
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
    }

    /**
     * 将项强制放到队列中，如果任务被拒绝，则使用该队列
     */
    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (parent == null || parent.isShutdown())
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        return super.offer(o, timeout, unit); //forces the item onto the queue, to be used if the task is rejected
    }

    @Override
    public boolean offer(Runnable o) {
        //we can't do any checks
        if (parent == null) return super.offer(o);
        //we are maxed out on threads, simply queue the object
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) return super.offer(o);
        //we have idle threads, just add it to the queue
        if (parent.getSubmittedCount() <= (parent.getPoolSize())) return super.offer(o);
        //if we have less threads than maximum force creation of a new thread
        if (parent.getPoolSize() < parent.getMaximumPoolSize()) return false;
        //if we reached here, we need to add it to the queue
        return super.offer(o);
    }

    /**
     * poll方法是从工作线程队列中弹出任务，如果发现没有任务，说明现在这会“Tomcat挺空闲”啊，就有机会“整肃”一下队伍了，
     * 也就是Tomcat线程池中的线程，也就是执行前面说的这个renew操作
     */
    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks.
            // 轮询超时，如果需要避免内存泄漏，它提供了一个停止当前线程的机会。
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        return super.take();
    }

    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity != null) {
            // ThreadPoolExecutor.setCorePoolSize checks that
            // remainingCapacity==0 to allow to interrupt idle threads
            // I don't see why, but this hack allows to conform to this
            // "requirement"
            return forcedRemainingCapacity.intValue();
        }
        return super.remainingCapacity();
    }

    public void setForcedRemainingCapacity(Integer forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

}
