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


package org.apache.catalina.loader;

import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class is loaded by {@link WebappClassLoaderBase} to enable it to
 * deregister JDBC drivers forgotten by the web application. There are some
 * classloading hacks involved - see
 * {@link WebappClassLoaderBase#clearReferences()} for details - but the short
 * version is do not just create a new instance of this class with the new
 * keyword.
 * <p>
 * Since this class is loaded by {@link WebappClassLoaderBase}, it cannot refer
 * to any internal Tomcat classes as that will cause the security manager to
 * complain.
 * <p>
 * 该类由WebappClassLoaderBase加载，以使它能够注销被web应用程序遗忘的JDBC驱动程序。
 * 这里涉及到一些类加载技巧——
 * 详细信息请参见WebappClassLoaderBase.clearReferences()——
 * 但是简短的版本是，不要仅仅使用new关键字创建该类的新实例。
 * 因为这个类是由WebappClassLoaderBase加载的，所以它不能引用任何内部Tomcat类，因为这会导致安全管理器发出抱怨。
 */
public class JdbcLeakPrevention {

    public List<String> clearJdbcDriverRegistrations() throws SQLException {
        List<String> driverNames = new ArrayList<>();

        /*
         * DriverManager.getDrivers() has a nasty side-effect of registering
         * drivers that are visible to this class loader but haven't yet been
         * loaded. Therefore, the first call to this method a) gets the list
         * of originally loaded drivers and b) triggers the unwanted
         * side-effect. The second call gets the complete list of drivers
         * ensuring that both original drivers and any loaded as a result of the
         * side-effects are all de-registered.
         *
         * getdrivers()有一个很糟糕的副作用:
         * 注册驱动程序，这些驱动程序对于这个类加载器是可见的，但是还没有加载。
         * 因此，这个方法的第一个调用
         * a)获取原始加载驱动程序的列表，
         * b)触发不必要的副作用。
         * 第二个调用获取完整的驱动程序列表，确保原始驱动程序和由于副作用而加载的所有驱动程序都被注销。
         */
        Set<Driver> originalDrivers = new HashSet<>();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            originalDrivers.add(drivers.nextElement());
        }
        drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            // Only unload the drivers this web app loaded
            if (driver.getClass().getClassLoader() !=
                    this.getClass().getClassLoader()) {
                continue;
            }
            // Only report drivers that were originally registered. Skip any
            // that were registered as a side-effect of this code.
            if (originalDrivers.contains(driver)) {
                driverNames.add(driver.getClass().getCanonicalName());
            }
            DriverManager.deregisterDriver(driver);
        }
        return driverNames;
    }
}
