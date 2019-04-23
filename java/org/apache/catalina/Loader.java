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
package org.apache.catalina;

import java.beans.PropertyChangeListener;

/**
 * A <b>Loader</b> represents a Java ClassLoader implementation that can
 * be used by a Container to load class files (within a repository associated
 * with the Loader) that are designed to be reloaded upon request, as well as
 * a mechanism to detect whether changes have occurred in the underlying
 * repository.
 * <p>
 * In order for a <code>Loader</code> implementation to successfully operate
 * with a <code>Context</code> implementation that implements reloading, it
 * must obey the following constraints:
 * <ul>
 * <li>Must implement <code>Lifecycle</code> so that the Context can indicate
 * that a new class loader is required.
 * <li>The <code>start()</code> method must unconditionally create a new
 * <code>ClassLoader</code> implementation.
 * <li>The <code>stop()</code> method must throw away its reference to the
 * <code>ClassLoader</code> previously utilized, so that the class loader,
 * all classes loaded by it, and all objects of those classes, can be
 * garbage collected.
 * <li>Must allow a call to <code>stop()</code> to be followed by a call to
 * <code>start()</code> on the same <code>Loader</code> instance.
 * <li>Based on a policy chosen by the implementation, must call the
 * <code>Context.reload()</code> method on the owning <code>Context</code>
 * when a change to one or more of the class files loaded by this class
 * loader is detected.
 * </ul>
 * <p>
 * 加载器表示一个Java类加载器实现，容器可以使用这个Java类加载器来加载类文件(在与加载器关联的存储库中)，
 * 这些类文件设计为在请求时重新加载，以及一种检测底层存储库中是否发生了更改的机制。
 * 为了使加载器实现成功地操作实现重载的上下文实现，它必须遵守以下约束:
 * 必须实现生命周期，以便上下文可以指示需要一个新的类加载器。
 * 方法必须无条件地创建一个新的类加载器实现。
 * stop()方法必须丢弃它对先前使用的类加载器的引用，以便类加载器、由它加载的所有类以及这些类的所有对象都可以被垃圾收集。
 * 必须允许在同一个加载器实例上先调用stop()，然后调用start()。
 * 根据实现选择的策略，当检测到对这个类加载器加载的一个或多个类文件的更改时，必须在所属上下文中调用Context.reload()方法。
 *
 * @author Craig R. McClanahan
 */
public interface Loader {


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     * <p>
     * 执行周期性任务，如重新加载等。此方法将在此容器的类加载上下文中调用。意外的投掷物将被捕获并记录下来。
     */
    public void backgroundProcess();


    /**
     * @return the Java class loader to be used by this Container.
     */
    public ClassLoader getClassLoader();


    /**
     * @return the Context with which this Loader has been associated.
     */
    public Context getContext();


    /**
     * Set the Context with which this Loader has been associated.
     *
     * @param context The associated Context
     */
    public void setContext(Context context);


    /**
     * @return the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     */
    public boolean getDelegate();


    /**
     * Set the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     *
     * @param delegate The new flag
     */
    public void setDelegate(boolean delegate);


    /**
     * @return the reloadable flag for this Loader.
     */
    public boolean getReloadable();


    /**
     * Set the reloadable flag for this Loader.
     *
     * @param reloadable The new reloadable flag
     */
    public void setReloadable(boolean reloadable);


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener);


    /**
     * Has the internal repository associated with this Loader been modified,
     * such that the loaded classes should be reloaded?
     *
     * @return <code>true</code> when the repository has been modified,
     * <code>false</code> otherwise
     */
    public boolean modified();


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener);
}
