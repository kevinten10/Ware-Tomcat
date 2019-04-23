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

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * Intended for use by a {@link Valve} to indicate that the {@link Valve}
 * provides access logging. It is used by the Tomcat internals to identify a
 * Valve that logs access requests so requests that are rejected
 * earlier in the processing chain can still be added to the access log.
 * Implementations of this interface should be robust against the provided
 * {@link Request} and {@link Response} objects being null, having null
 * attributes or any other 'oddness' that may result from attempting to log
 * a request that was almost certainly rejected because it was mal-formed.
 * <p>
 * 用于阀门，指示阀门提供访问日志记录。Tomcat内部使用它来标识一个记录访问请求的阀门，
 * 这样在处理链的早期被拒绝的请求仍然可以添加到访问日志中。这个接口的实现应该是健壮的，
 * 以抵抗所提供的请求和响应对象为空、具有空属性或任何其他“奇怪”的情况，
 * 这些情况可能是由于试图记录一个几乎肯定被拒绝的请求(因为它的格式错误)而导致的。
 */
public interface AccessLog {

    /**
     * Name of request attribute used to override the remote address recorded by
     * the AccessLog.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE =
            "org.apache.catalina.AccessLog.RemoteAddr";

    /**
     * Name of request attribute used to override remote host name recorded by
     * the AccessLog.
     */
    public static final String REMOTE_HOST_ATTRIBUTE =
            "org.apache.catalina.AccessLog.RemoteHost";

    /**
     * Name of request attribute used to override the protocol recorded by the
     * AccessLog.
     */
    public static final String PROTOCOL_ATTRIBUTE =
            "org.apache.catalina.AccessLog.Protocol";

    /**
     * Name of request attribute used to override the server port recorded by
     * the AccessLog.
     */
    public static final String SERVER_PORT_ATTRIBUTE =
            "org.apache.catalina.AccessLog.ServerPort";


    /**
     * Add the request/response to the access log using the specified processing
     * time.
     *
     * @param request  Request (associated with the response) to log
     * @param response Response (associated with the request) to log
     * @param time     Time taken to process the request/response in
     *                 milliseconds (use 0 if not known)
     */
    public void log(Request request, Response response, long time);

    /**
     * Should this valve set request attributes for IP address, hostname,
     * protocol and port used for the request? This are typically used in
     * conjunction with the {@link org.apache.catalina.valves.AccessLogValve}
     * which will otherwise log the original values.
     * <p>
     * The attributes set are:
     * <ul>
     * <li>org.apache.catalina.RemoteAddr</li>
     * <li>org.apache.catalina.RemoteHost</li>
     * <li>org.apache.catalina.Protocol</li>
     * <li>org.apache.catalina.ServerPost</li>
     * </ul>
     *
     * @param requestAttributesEnabled <code>true</code> causes the attributes
     *                                 to be set, <code>false</code> disables
     *                                 the setting of the attributes.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled);

    /**
     * @return <code>true</code> if the attributes will be logged, otherwise
     * <code>false</code>
     * @see #setRequestAttributesEnabled(boolean)
     */
    public boolean getRequestAttributesEnabled();
}
