/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.coyote;


/**
 * Action hook. Actions represent the callback mechanism used by
 * coyote servlet containers to request operations on the coyote connectors.
 * Some standard actions are defined in ActionCode, however custom
 * actions are permitted.
 * <p>
 * The param object can be used to pass and return informations related with the
 * action.
 * <p>
 * <p>
 * This interface is typically implemented by ProtocolHandlers, and the param
 * is usually a Request or Response object.
 * <p>
 * 行动钩。动作表示coyote servlet容器用于请求对coyote连接器进行操作的回调机制。
 * 一些标准操作在ActionCode中定义，但是允许自定义操作。param对象可用于传递和返回与操作相关的信息。
 * 该接口通常由协议处理程序实现，参数通常是一个请求或响应对象。
 *
 * @author Remy Maucherat
 */
public interface ActionHook {

    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param      Action parameter
     */
    public void action(ActionCode actionCode, Object param);
}
