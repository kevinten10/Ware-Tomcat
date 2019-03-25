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
 * Interface defining a listener for significant events (including "component
 * start" and "component stop" generated by a component that implements the
 * Lifecycle interface. The listener will be fired after the associated state
 * change has taken place.
 * <p>
 * 为重要事件(包括由实现* Lifecycle接口的组件生成的“组件*启动”和“组件停止”)定义侦听器的接口。侦听器将在发生关联状态*更改后被触发。
 *
 * @author Craig R. McClanahan
 */
public interface LifecycleListener {


    /**
     * Acknowledge the occurrence of the specified event.
     * <p>
     * 确认指定事件的发生。
     *
     * @param event LifecycleEvent that has occurred
     */
    public void lifecycleEvent(LifecycleEvent event);


}
