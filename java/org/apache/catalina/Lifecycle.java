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


/**
 * Common interface for component life cycle methods.  Catalina components
 * may implement this interface (as well as the appropriate interface(s) for
 * the functionality they support) in order to provide a consistent mechanism
 * to start and stop the component.
 * <br>
 * The valid state transitions for components that support {@link Lifecycle}
 * are:
 * <p>
 * 我们知道Tomcat的架构设计是清晰的、模块化的，其拥有很多组件，假如我们要启动Tomcat，可以一个一个启动组件，
 * 但这样启动有很多缺点，不仅麻烦，而且容易漏了组件启动，还会对后面动态组件扩展带来麻烦。
 * 难不成真要我们一个一个启动吗？其实未必，Tomcat的设计者提供了一个解决方案：用Lifecycle管理启动、停止、关闭。
 * <p>
 * 从第一节的架构图可以看到各个核心组件有包含与被包含的关系，例如Server<-Service<-Container和Connector，
 * 最大的是Server，往下一层层包含。其实Tomcat就是以容器的方式来组织整个系统架构的，
 * 反映到数据结构就是树，树的根节点没有父节点，其它节点有且仅有一个父节点，每个父节点有零个或多个子节点，
 * 根容器与其他容器也有这样的特点。鉴于如此，可以通过父容器负责启动它的子容器，这样只要启动根容器，即可把其他所有容器都启动，达到统一启动、停止、关闭的效果。
 * <p>
 * Lifecycle作为统一的接口，把所有的启动、停止、关闭、生命周期相关方法都组织到一起，就可以很方便地管理Tomcat各个容器组件的生命周期。
 * <p>
 * 组件生命周期方法的公共接口。Catalina组件*可以实现这个接口(以及它们支持的功能的适当接口)，
 * 以提供一个一致的机制*来启动和停止组件。* <br> *支持{@link Lifecycle} *的组件的有效状态转换为:
 *
 * <pre>
 *            start()
 *  -----------------------------
 *  |                           |
 *  | init()                    |
 * NEW -»-- INITIALIZING        |
 * | |           |              |     ------------------«-----------------------
 * | |           |auto          |     |                                        |
 * | |          \|/    start() \|/   \|/     auto          auto         stop() |
 * | |      INITIALIZED --»-- STARTING_PREP --»- STARTING --»- STARTED --»---  |
 * | |         |                                                            |  |
 * | |destroy()|                                                            |  |
 * | --»-----«--    ------------------------«--------------------------------  ^
 * |     |          |                                                          |
 * |     |         \|/          auto                 auto              start() |
 * |     |     STOPPING_PREP ----»---- STOPPING ------»----- STOPPED -----»-----
 * |    \|/                               ^                     |  ^
 * |     |               stop()           |                     |  |
 * |     |       --------------------------                     |  |
 * |     |       |                                              |  |
 * |     |       |    destroy()                       destroy() |  |
 * |     |    FAILED ----»------ DESTROYING ---«-----------------  |
 * |     |                        ^     |                          |
 * |     |     destroy()          |     |auto                      |
 * |     --------»-----------------    \|/                         |
 * |                                 DESTROYED                     |
 * |                                                               |
 * |                            stop()                             |
 * ----»-----------------------------»------------------------------
 *
 * Any state can transition to FAILED.
 *
 * Calling start() while a component is in states STARTING_PREP, STARTING or
 * STARTED has no effect.
 *
 * Calling start() while a component is in state NEW will cause init() to be
 * called immediately after the start() method is entered.
 *
 * Calling stop() while a component is in states STOPPING_PREP, STOPPING or
 * STOPPED has no effect.
 *
 * Calling stop() while a component is in state NEW transitions the component
 * to STOPPED. This is typically encountered when a component fails to start and
 * does not start all its sub-components. When the component is stopped, it will
 * try to stop all sub-components - even those it didn't start.
 *
 * Attempting any other transition will throw {@link LifecycleException}.
 *
 * </pre>
 * The {@link LifecycleEvent}s fired during state changes are defined in the
 * methods that trigger the changed. No {@link LifecycleEvent}s are fired if the
 * attempted transition is not valid.
 *
 * @author Craig R. McClanahan
 */
public interface Lifecycle {


    // ----------------------------------------------------- Manifest Constants


    /**
     * The LifecycleEvent type for the "component before init" event.
     */
    public static final String BEFORE_INIT_EVENT = "before_init";


    /**
     * The LifecycleEvent type for the "component after init" event.
     */
    public static final String AFTER_INIT_EVENT = "after_init";


    /**
     * The LifecycleEvent type for the "component start" event.
     */
    public static final String START_EVENT = "start";


    /**
     * The LifecycleEvent type for the "component before start" event.
     */
    public static final String BEFORE_START_EVENT = "before_start";


    /**
     * The LifecycleEvent type for the "component after start" event.
     */
    public static final String AFTER_START_EVENT = "after_start";


    /**
     * The LifecycleEvent type for the "component stop" event.
     */
    public static final String STOP_EVENT = "stop";


    /**
     * The LifecycleEvent type for the "component before stop" event.
     */
    public static final String BEFORE_STOP_EVENT = "before_stop";


    /**
     * The LifecycleEvent type for the "component after stop" event.
     */
    public static final String AFTER_STOP_EVENT = "after_stop";


    /**
     * The LifecycleEvent type for the "component after destroy" event.
     */
    public static final String AFTER_DESTROY_EVENT = "after_destroy";


    /**
     * The LifecycleEvent type for the "component before destroy" event.
     */
    public static final String BEFORE_DESTROY_EVENT = "before_destroy";


    /**
     * The LifecycleEvent type for the "periodic" event.
     */
    public static final String PERIODIC_EVENT = "periodic";


    /**
     * The LifecycleEvent type for the "configure_start" event. Used by those
     * components that use a separate component to perform configuration and
     * need to signal when configuration should be performed - usually after
     * {@link #BEFORE_START_EVENT} and before {@link #START_EVENT}.
     */
    public static final String CONFIGURE_START_EVENT = "configure_start";


    /**
     * The LifecycleEvent type for the "configure_stop" event. Used by those
     * components that use a separate component to perform configuration and
     * need to signal when de-configuration should be performed - usually after
     * {@link #STOP_EVENT} and before {@link #AFTER_STOP_EVENT}.
     */
    public static final String CONFIGURE_STOP_EVENT = "configure_stop";


    // --------------------------------------------------------- Public Methods


    /**
     * Add a LifecycleEvent listener to this component.
     *
     * @param listener The listener to add
     */
    public void addLifecycleListener(LifecycleListener listener);


    /**
     * Get the life cycle listeners associated with this life cycle.
     *
     * @return An array containing the life cycle listeners associated with this
     * life cycle. If this component has no listeners registered, a
     * zero-length array is returned.
     */
    public LifecycleListener[] findLifecycleListeners();


    /**
     * Remove a LifecycleEvent listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removeLifecycleListener(LifecycleListener listener);


    /**
     * Prepare the component for starting. This method should perform any
     * initialization required post object creation. The following
     * {@link LifecycleEvent}s will be fired in the following order:
     * <ol>
     * <li>INIT_EVENT: On the successful completion of component
     * initialization.</li>
     * </ol>
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    public void init() throws LifecycleException;

    /**
     * Prepare for the beginning of active use of the public methods other than
     * property getters/setters and life cycle methods of this component. This
     * method should be called before any of the public methods other than
     * property getters/setters and life cycle methods of this component are
     * utilized. The following {@link LifecycleEvent}s will be fired in the
     * following order:
     * <ol>
     * <li>BEFORE_START_EVENT: At the beginning of the method. It is as this
     * point the state transitions to
     * {@link LifecycleState#STARTING_PREP}.</li>
     * <li>START_EVENT: During the method once it is safe to call start() for
     * any child components. It is at this point that the
     * state transitions to {@link LifecycleState#STARTING}
     * and that the public methods other than property
     * getters/setters and life cycle methods may be
     * used.</li>
     * <li>AFTER_START_EVENT: At the end of the method, immediately before it
     * returns. It is at this point that the state
     * transitions to {@link LifecycleState#STARTED}.
     * </li>
     * </ol>
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    public void start() throws LifecycleException;


    /**
     * Gracefully terminate the active use of the public methods other than
     * property getters/setters and life cycle methods of this component. Once
     * the STOP_EVENT is fired, the public methods other than property
     * getters/setters and life cycle methods should not be used. The following
     * {@link LifecycleEvent}s will be fired in the following order:
     * <ol>
     * <li>BEFORE_STOP_EVENT: At the beginning of the method. It is at this
     * point that the state transitions to
     * {@link LifecycleState#STOPPING_PREP}.</li>
     * <li>STOP_EVENT: During the method once it is safe to call stop() for
     * any child components. It is at this point that the
     * state transitions to {@link LifecycleState#STOPPING}
     * and that the public methods other than property
     * getters/setters and life cycle methods may no longer be
     * used.</li>
     * <li>AFTER_STOP_EVENT: At the end of the method, immediately before it
     * returns. It is at this point that the state
     * transitions to {@link LifecycleState#STOPPED}.
     * </li>
     * </ol>
     * <p>
     * Note that if transitioning from {@link LifecycleState#FAILED} then the
     * three events above will be fired but the component will transition
     * directly from {@link LifecycleState#FAILED} to
     * {@link LifecycleState#STOPPING}, bypassing
     * {@link LifecycleState#STOPPING_PREP}
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that needs to be reported
     */
    public void stop() throws LifecycleException;

    /**
     * Prepare to discard the object. The following {@link LifecycleEvent}s will
     * be fired in the following order:
     * <ol>
     * <li>DESTROY_EVENT: On the successful completion of component
     * destruction.</li>
     * </ol>
     *
     * @throws LifecycleException if this component detects a fatal error
     *                            that prevents this component from being used
     */
    public void destroy() throws LifecycleException;


    /**
     * Obtain the current state of the source component.
     *
     * @return The current state of the source component.
     */
    public LifecycleState getState();


    /**
     * Obtain a textual representation of the current component state. Useful
     * for JMX. The format of this string may vary between point releases and
     * should not be relied upon to determine component state. To determine
     * component state, use {@link #getState()}.
     *
     * @return The name of the current component state.
     */
    public String getStateName();


    /**
     * Marker interface used to indicate that the instance should only be used
     * once. Calling {@link #stop()} on an instance that supports this interface
     * will automatically call {@link #destroy()} after {@link #stop()}
     * completes.
     */
    public interface SingleUse {
    }
}
