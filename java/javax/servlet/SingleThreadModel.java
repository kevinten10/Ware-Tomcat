/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package javax.servlet;

/**
 * Ensures that servlets handle only one request at a time. This interface has
 * no methods.
 * <p>
 * If a servlet implements this interface, you are <i>guaranteed</i> that no two
 * threads will execute concurrently in the servlet's <code>service</code>
 * method. The servlet container can make this guarantee by synchronizing access
 * to a single instance of the servlet, or by maintaining a pool of servlet
 * instances and dispatching each new request to a free servlet.
 * <p>
 * Note that SingleThreadModel does not solve all thread safety issues. For
 * example, session attributes and static variables can still be accessed by
 * multiple requests on multiple threads at the same time, even when
 * SingleThreadModel servlets are used. It is recommended that a developer take
 * other means to resolve those issues instead of implementing this interface,
 * such as avoiding the usage of an instance variable or synchronizing the block
 * of the code accessing those resources. This interface is deprecated in
 * Servlet API version 2.4.
 * <p>
 * 确保servlet一次只处理一个请求。这个接口没有方法。
 * 如果servlet实现了这个接口，就可以保证servlet的服务方法中不会同时执行两个线程。
 * servlet容器可以通过同步对servlet单个实例的访问，
 * 或者通过维护servlet实例池并将每个新请求分派给一个空闲的servlet来实现这一保证。
 * 注意，单线程模型并不能解决所有线程安全问题。例如，即使使用单线程模型servlet，会话属性和静态变量仍然可以由多个线程上的多个请求同时访问。
 * 建议开发人员采用其他方法来解决这些问题，而不是实现这个接口，比如避免使用实例变量或同步访问这些资源的代码块。
 * 这个接口在Servlet API 2.4版本中是不推荐的。
 *
 * @deprecated As of Java Servlet API 2.4, with no direct replacement.
 */
@Deprecated
public interface SingleThreadModel {
    // No methods
}
