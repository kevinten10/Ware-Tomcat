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
package org.apache.catalina.startup;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.ClassLoaderFactory.Repository;
import org.apache.catalina.startup.ClassLoaderFactory.RepositoryType;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * Bootstrap loader for Catalina.  This application constructs a class loader
 * for use in loading the Catalina internal classes (by accumulating all of the
 * JAR files found in the "server" directory under "catalina.home"), and
 * starts the regular execution of the container.  The purpose of this
 * roundabout approach is to keep the Catalina internal classes (and any
 * other classes they depend on, such as an XML parser) out of the system
 * class path and therefore not visible to application level classes.
 * <p>
 * 为卡特琳娜引导加载程序。这个应用程序构造了一个类加载器，用于加载Catalina内部类
 * (通过积累在“Catalina .home”下的“server”目录中找到的所有JAR文件)，并启动容器的常规执行。
 * 这种迂回方法的目的是将Catalina内部类(以及它们所依赖的任何其他类，比如XML解析器)
 * 排除在系统类路径之外，因此对应用程序级别的类不可见。
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public final class Bootstrap {

    private static final Log log = LogFactory.getLog(Bootstrap.class);

    /**
     * Daemon object used by main.
     */
    private static final Object daemonLock = new Object();
    private static volatile Bootstrap daemon = null;

    private static final File catalinaBaseFile;
    private static final File catalinaHomeFile;

    private static final Pattern PATH_PATTERN = Pattern.compile("(\".*?\")|(([^,])*)");

    /**
     * 对比getAbsoluteFile()、getCanonicalFile()
     *
     * getAbsoluteFile获取绝对文件(如果文件路径中包含.和..，不解析)
     *
     * getCanonicalFile获取经典文件(如果文件路径中包含.和..，解析)
     */
    static {
        // Will always be non-null
        // System.getProperty("user.dir")，获取当前目录
        // 由于是在$CATALINA_HOME\bin下运行的Bootstrap，所以userDir为$CATALINA_HOME\bin
        String userDir = System.getProperty("user.dir");

        // Home first
        // Globals是存放全局常量的类
        // Globals.CATALINA_HOME_PROP = "catalina.home"
        // catalina.home在运行Bootstrap时已设置(Tomcat的根目录)
        String home = System.getProperty(Globals.CATALINA_HOME_PROP);
        File homeFile = null;

        // 获取Tomcat的绝对路径
        if (home != null) {
            File f = new File(home);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        if (homeFile == null) {
            // First fall-back. See if current directory is a bin directory
            // in a normal Tomcat install
            File bootstrapJar = new File(userDir, "bootstrap.jar");

            if (bootstrapJar.exists()) {
                File f = new File(userDir, "..");
                try {
                    homeFile = f.getCanonicalFile();
                } catch (IOException ioe) {
                    homeFile = f.getAbsoluteFile();
                }
            }
        }

        if (homeFile == null) {
            // Second fall-back. Use current directory
            File f = new File(userDir);
            try {
                homeFile = f.getCanonicalFile();
            } catch (IOException ioe) {
                homeFile = f.getAbsoluteFile();
            }
        }

        // 设置catalinaHomeFile
        catalinaHomeFile = homeFile;
        System.setProperty(
                Globals.CATALINA_HOME_PROP, catalinaHomeFile.getPath());

        // Then base
        String base = System.getProperty(Globals.CATALINA_BASE_PROP);
        if (base == null) {
            catalinaBaseFile = catalinaHomeFile;
        } else {
            File baseFile = new File(base);
            try {
                baseFile = baseFile.getCanonicalFile();
            } catch (IOException ioe) {
                baseFile = baseFile.getAbsoluteFile();
            }
            catalinaBaseFile = baseFile;
        }
        System.setProperty(
                Globals.CATALINA_BASE_PROP, catalinaBaseFile.getPath());
    }

    // -------------------------------------------------------------- Variables


    /**
     * Daemon reference.
     *
     * @see Catalina
     */
    private Object catalinaDaemon = null;


    ClassLoader commonLoader = null;
    ClassLoader catalinaLoader = null;
    ClassLoader sharedLoader = null;


    // -------------------------------------------------------- Private Methods


    /**
     * 创建commonLoader、catalinaLoader、sharedLoader
     */
    private void initClassLoaders() {
        try {
            // 创建commonLoader
            commonLoader = createClassLoader("common", null);
            if (commonLoader == null) {
                // no config file, default to this loader - we might be in a 'single' env.
                commonLoader = this.getClass().getClassLoader();
            }
            // 创建catalinaLoader、sharedLoader
            catalinaLoader = createClassLoader("server", commonLoader);
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }


    /**
     * 创建类加载器
     */
    private ClassLoader createClassLoader(String name, ClassLoader parent)
            throws Exception {

        // CatalinaProperties解析$CATALINA_HOME\conf\catalina.properties，
        // 并将catalina.properties内的属性存为系统属性
        // catalina.properties内common.loader="${catalina.base}/lib",
        // "${catalina.base}/lib/*.jar","${catalina.home}/lib","${catalina.home}/lib/*.jar"
        // 读取common.loader
        String value = CatalinaProperties.getProperty(name + ".loader");
        if ((value == null) || (value.equals("")))
            return parent;

        // 将${catalina.base},${catalina.home}替换为Tomcat的绝对路径
        value = replace(value);

        List<Repository> repositories = new ArrayList<>();

        String[] repositoryPaths = getPaths(value);

        for (String repository : repositoryPaths) {
            // Check for a JAR URL repository
            try {
                @SuppressWarnings("unused")
                URL url = new URL(repository);
                repositories.add(new Repository(repository, RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            // Local repository
            if (repository.endsWith("*.jar")) {
                repository = repository.substring
                        (0, repository.length() - "*.jar".length());
                repositories.add(new Repository(repository, RepositoryType.GLOB));
            } else if (repository.endsWith(".jar")) {
                repositories.add(new Repository(repository, RepositoryType.JAR));
            } else {
                repositories.add(new Repository(repository, RepositoryType.DIR));
            }
        }

        // ClassLoaderFactory依据repositories的内容创建ClassLoader
        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }


    /**
     * System property replacement in the given string.
     * <p>
     * 在给定字符串中替换系统属性。
     *
     * @param str The original string
     * @return the modified string
     */
    protected String replace(String str) {
        // Implementation is copied from ClassLoaderLogManager.replace(),
        // but added special processing for catalina.home and catalina.base.
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                // 占位符之前进行复制
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                // 占位符替换
                String propName = str.substring(pos_start + 2, pos_end);
                String replacement;
                if (propName.length() == 0) {
                    replacement = null;
                } else if (Globals.CATALINA_HOME_PROP.equals(propName)) {
                    replacement = getCatalinaHome();
                } else if (Globals.CATALINA_BASE_PROP.equals(propName)) {
                    replacement = getCatalinaBase();
                } else {
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }


    /**
     * Initialize daemon.
     *
     * @throws Exception Fatal initialization error
     */
    public void init() throws Exception {

        // 创建commonLoader、catalinaLoader、sharedLoader
        initClassLoaders();

        Thread.currentThread().setContextClassLoader(catalinaLoader);

        // 设置SecurityClassLoad。具体作用还不清楚。。
        SecurityClassLoad.securityClassLoad(catalinaLoader);

        // Load our startup class and call its process() method
        if (log.isDebugEnabled())
            log.debug("Loading startup class");
        // 通过反射实例化Catalina
        Class<?> startupClass = catalinaLoader.loadClass("org.apache.catalina.startup.Catalina");
        Object startupInstance = startupClass.getConstructor().newInstance();

        // Set the shared extensions class loader
        if (log.isDebugEnabled())
            log.debug("Setting startup class properties");
        String methodName = "setParentClassLoader";
        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        Object paramValues[] = new Object[1];
        paramValues[0] = sharedLoader;
        // 通过反射设置Catalina的parentClassLoader
        Method method =
                startupInstance.getClass().getMethod(methodName, paramTypes);
        method.invoke(startupInstance, paramValues);

        // 将实例化的Catalina赋值给catalinaDaemon
        catalinaDaemon = startupInstance;
    }


    /**
     * Load daemon.
     * <p>
     * Bootstrap.load(args)内部通过反射调用Catalina.load(args)，
     * Catalina将利用Digest(Digest详解)解析server.xml，创建相应组件的实例，
     * 之后调用Server.init()，Server初始化时，又会调用其内部的Service的init方法，即调用某一组件的init方法时，将触发其子组件的init方法
     * <p>
     * 执行Bootstrap.load(args)将触发的动作
     * <p>
     * 初始化Boostrap的Catalina对象：通过反射生成Catalina对象，并通过反射调用setParentClassLoader方法设置其父 ClassLoader为sharedLoader。
     * 为什么要用反射，不直接在声明的时候生成对象？使用反射来生成实例的原因是因为在tomcat的发展历史中可以不止Catalina一种启动方式，现在看代码已经没必要了。
     */
    private void load(String[] arguments)
            throws Exception {

        // Call the load() method
        String methodName = "load";
        Object param[];
        Class<?> paramTypes[];
        if (arguments == null || arguments.length == 0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
                catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled())
            log.debug("Calling startup class " + method);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * getServer() for configtest
     */
    private Object getServer() throws Exception {

        String methodName = "getServer";
        Method method =
                catalinaDaemon.getClass().getMethod(methodName);
        return method.invoke(catalinaDaemon);

    }


    // ----------------------------------------------------------- Main Program


    /**
     * Load the Catalina daemon.
     *
     * @param arguments Initialization arguments
     * @throws Exception Fatal initialization error
     */
    public void init(String[] arguments)
            throws Exception {

        init();
        load(arguments);

    }


    /**
     * Start the Catalina daemon.
     *
     * @throws Exception Fatal start error
     */
    public void start()
            throws Exception {
        if (catalinaDaemon == null) init();

        Method method = catalinaDaemon.getClass().getMethod("start", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);

    }


    /**
     * Stop the Catalina Daemon.
     *
     * @throws Exception Fatal stop error
     */
    public void stop()
            throws Exception {

        Method method = catalinaDaemon.getClass().getMethod("stop", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);

    }


    /**
     * Stop the standalone server.
     *
     * @throws Exception Fatal stop error
     */
    public void stopServer()
            throws Exception {

        Method method =
                catalinaDaemon.getClass().getMethod("stopServer", (Class[]) null);
        method.invoke(catalinaDaemon, (Object[]) null);

    }


    /**
     * Stop the standalone server.
     *
     * @param arguments Command line arguments
     * @throws Exception Fatal stop error
     */
    public void stopServer(String[] arguments)
            throws Exception {

        Object param[];
        Class<?> paramTypes[];
        if (arguments == null || arguments.length == 0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
                catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * Set flag.
     * <p>
     * Bootstrap.setAwait(true)内部通过反射，设置Catalina的await属性(默认为false)为true
     * <p>
     * 启动Catalina过程中，当Catalina将Tomcat的所有组件启动之后，会检查await属性，如果为true，
     * 会调用Catalina.await()，而Catalina.await()又会调用其内部的Server的await()
     * <p>
     * Server.await()包含一个while循环，此循环用于监听指定socket端口(默认为8005)的连接，
     * 当某个连接传入的参数为”SHUTDOWN”(默认为”SHUTDOWN”)时，终止此while循环(端口号和终止while循环的参数，在server.xml的Server标签设置)
     * <p>
     * Server.await()用来维持Bootstrap的main方法(main thread)处于运行状态，
     * 而线程池中监听http请求的线程是守护线程(daemon thread)
     * <p>
     * 当Tomcat的指定端口接收到关闭命令时，Server.await()内的while循环终止，
     * 然后Catalina会调用stop()方法，关闭Tomcat的所有组件，最终Bootstrap的main thread终止，Tomcat关闭
     * <p>
     * 2.5 调用Bootstrap.load(args)
     *
     * @param await <code>true</code> if the daemon should block
     * @throws Exception Reflection error
     */
    public void setAwait(boolean await)
            throws Exception {

        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object paramValues[] = new Object[1];
        paramValues[0] = Boolean.valueOf(await);
        Method method =
                catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);

    }

    public boolean getAwait()
            throws Exception {
        Class<?> paramTypes[] = new Class[0];
        Object paramValues[] = new Object[0];
        Method method =
                catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b = (Boolean) method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }


    /**
     * Destroy the Catalina Daemon.
     */
    public void destroy() {

        // FIXME

    }


    /**
     * Main method and entry point when starting Tomcat via the provided
     * scripts.
     * <p>
     * 1. 实例化Boostrap对象，并调用其init方法；
     * 2. 调用load方法
     * 3. 调用start方法；
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {

        synchronized (daemonLock) {
            if (daemon == null) {
                // Don't set daemon until init() has completed
                // 创建bootstrap
                Bootstrap bootstrap = new Bootstrap();
                try {
                    // init
                    bootstrap.init();
                } catch (Throwable t) {
                    handleThrowable(t);
                    t.printStackTrace();
                    return;
                }
                daemon = bootstrap;
            } else {
                // When running as a service the call to stop will be on a new
                // thread so make sure the correct class loader is used to
                // prevent a range of class not found exceptions.
                Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
            }
        }

        // 解析参数
        try {
            String command = "start";
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {
                args[args.length - 1] = "start";
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                // 调用Bootstrap.setAwait(true)
                daemon.setAwait(true);
                // 调用Bootstrap.load(args)
                daemon.load(args);
                //
                daemon.start();
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
                System.exit(0);
            } else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            // Unwrap the Exception for clearer error reporting
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            // 启动失败，直接退出
            handleThrowable(t);
            t.printStackTrace();
            System.exit(1);
        }

    }


    /**
     * Obtain the name of configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     *
     * @return the catalina home
     */
    public static String getCatalinaHome() {
        return catalinaHomeFile.getPath();
    }


    /**
     * Obtain the name of the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHome()} will be used.
     *
     * @return the catalina base
     */
    public static String getCatalinaBase() {
        return catalinaBaseFile.getPath();
    }


    /**
     * Obtain the configured home (binary) directory. Note that home and
     * base may be the same (and are by default).
     *
     * @return the catalina home as a file
     */
    public static File getCatalinaHomeFile() {
        return catalinaHomeFile;
    }


    /**
     * Obtain the configured base (instance) directory. Note that
     * home and base may be the same (and are by default). If this is not set
     * the value returned by {@link #getCatalinaHomeFile()} will be used.
     *
     * @return the catalina base as a file
     */
    public static File getCatalinaBaseFile() {
        return catalinaBaseFile;
    }


    // Copied from ExceptionUtils since that class is not visible during start
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }


    // Protected for unit testing
    protected static String[] getPaths(String value) {

        List<String> result = new ArrayList<>();
        Matcher matcher = PATH_PATTERN.matcher(value);

        while (matcher.find()) {
            String path = value.substring(matcher.start(), matcher.end());

            path = path.trim();
            if (path.length() == 0) {
                continue;
            }

            char first = path.charAt(0);
            char last = path.charAt(path.length() - 1);

            if (first == '"' && last == '"' && path.length() > 1) {
                path = path.substring(1, path.length() - 1);
                path = path.trim();
                if (path.length() == 0) {
                    continue;
                }
            } else if (path.contains("\"")) {
                // Unbalanced quotes
                // Too early to use standard i18n support. The class path hasn't
                // been configured.
                throw new IllegalArgumentException(
                        "The double quote [\"] character only be used to quote paths. It must " +
                                "not appear in a path. This loader path is not valid: [" + value + "]");
            } else {
                // Not quoted - NO-OP
            }

            result.add(path);
        }
        return result.toArray(new String[result.size()]);
    }
}
