/*
 * Copyright (C) 2005, The Beangle Software.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.beangle.bas.engine.jetty;

import jakarta.servlet.SessionTrackingMode;
import org.beangle.bas.engine.Server;
import org.eclipse.jetty.ee10.annotations.AnnotationConfiguration;
import org.eclipse.jetty.ee10.servlet.DefaultServlet;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.webapp.FragmentConfiguration;
import org.eclipse.jetty.ee10.webapp.MetaInfConfiguration;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.ee10.webapp.WebXmlConfiguration;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ErrorHandler;
import org.eclipse.jetty.util.VirtualThreads;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.util.thread.VirtualThreadPool;

import java.io.File;
import java.nio.file.Path;
import java.util.Set;

public class JettyServerBuilder {
  private final Server.Config config;

  public JettyServerBuilder(Server.Config config) {
    this.config = config;
  }

  public org.eclipse.jetty.server.Server build() {
    var server = new org.eclipse.jetty.server.Server(threadPool());
    // 停服交给 Bootstrap 的 shutdown hook，与 tomcat/undertow 一致
    server.setStopAtShutdown(false);
    server.setTempDirectory(tempDir());
    server.addConnector(connector(server));
    server.setHandler(webapp());
    server.setErrorHandler(coreErrorHandler());
    return server;
  }

  /**
   * 对齐 tomcat 的 SwallowErrorValve：Jetty 默认错误页会返回异常消息与完整调用栈，
   * 生产模式关掉栈/原因链（消息仍保留，与 tomcat 一致），dev 模式保留以便排错。
   *
   * <p>core 层处理未进入 context 的错误（400/404 等）；servlet 层见 {@link #servletErrorHandler()}。
   */
  private ErrorHandler coreErrorHandler() {
    var handler = new ErrorHandler();
    handler.setShowStacks(config.devMode);
    handler.setShowCauses(config.devMode);
    return handler;
  }

  /** servlet 层错误页（servlet 抛异常 / sendError），Jetty 默认会整页打印调用栈。 */
  private org.eclipse.jetty.ee10.servlet.ErrorHandler servletErrorHandler() {
    var handler = new org.eclipse.jetty.ee10.servlet.ErrorHandler();
    handler.setShowStacks(config.devMode);
    return handler;
  }

  /** Jetty 的 setTempDirectory 要求目录已存在；initBase 正常会创建，这里对直接构造 Config 的场景兜底。 */
  private File tempDir() {
    var dir = new File(config.base, "temp");
    dir.mkdirs();
    return dir;
  }

  /** 对齐 tomcat 的虚拟线程执行器；不支持虚拟线程的 JVM 退回平台线程池。 */
  private ThreadPool threadPool() {
    return VirtualThreads.areSupported() ? new VirtualThreadPool() : new QueuedThreadPool();
  }

  private ServerConnector connector(org.eclipse.jetty.server.Server server) {
    HttpConfiguration http = new HttpConfiguration();
    http.setSendServerVersion(false);
    http.setSendXPoweredBy(false);

    ServerConnector connector = new ServerConnector(server, new HttpConnectionFactory(http));
    connector.setPort(config.port);
    config.getInt("connector.acceptCount").ifPresent(size -> connector.setAcceptQueueSize(size));
    // Jetty 只有一个连接空闲超时：keep-alive 优先，其次建连/读超时
    var idleTimeout = config.getInt("connector.keepAliveTimeout");
    if (idleTimeout.isEmpty()) idleTimeout = config.getInt("connector.connectionTimeout");
    idleTimeout.ifPresent(timeout -> connector.setIdleTimeout(timeout));
    return connector;
  }

  private WebAppContext webapp() {
    var wac = new WebAppContext();
    // Jetty 的根 context 必须是 "/"，而 Server.Config 把根规范成 ""
    wac.setContextPath(config.contextPath.isEmpty() ? "/" : config.contextPath);
    wac.setBaseResource(ResourceFactory.of(wac).newResource(Path.of(config.docBase)));
    // 应用依赖已由 basctl 放进 -cp，父加载器优先，等价于 tomcat 的 loader.setDelegate(true)
    wac.setParentLoaderPriority(true);
    // 换掉 Jetty 默认的 webapp 类加载器：它会把 WEB-INF/classes、WEB-INF/lib 也当成自己的资源根，
    // 同一个目录于是能从父加载器与 webapp 两侧各枚举一次，classpath*: 配置（如 beangle.xml）被合并两遍。
    // 这里与 tomcat 一致：父加载器取 TCCL（basctl 的 -cp），webapp 侧不提供资源。
    wac.setClassLoader(new EmbeddedClassLoader(Thread.currentThread().getContextClassLoader(), wac));
    // webapp 起不来时直接抛，等价于 tomcat 的 WebappFailFastListener
    wac.setThrowUnavailableOnStartupException(true);
    wac.setTempDirectory(tempDir());
    wac.setErrorHandler(servletErrorHandler());

    var sessions = wac.getSessionHandler();
    sessions.setMaxInactiveInterval(config.defaultSessionTimeout * 60);
    // 只保留 Cookie 会话跟踪，避免 ;jsessionid 出现在 URL/Referer/日志中
    sessions.setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE));

    // 与 tomcat/undertow 对齐：嵌入式引擎不解析 web.xml / web-fragment.xml，也不加载容器的
    // 默认描述符(webdefault-ee10.xml)；应用初始化统一走 SCI(beangle.xml)，完整的 web.xml 语义
    // 交给 server.xml 的 tomcat-server 模式。默认 servlet 因此不再来自描述符，见下方显式注册。
    wac.getConfigurations().remove(WebXmlConfiguration.class, FragmentConfiguration.class, MetaInfConfiguration.class);
    // 屏蔽 tomcat/jasper 的 SCI；Jetty 自身的 SCI（如 websocket）不能屏蔽
    wac.setAttribute(AnnotationConfiguration.SERVLET_CONTAINER_INITIALIZER_EXCLUSION_PATTERN,
      "org\\.apache\\.tomcat\\..*|org\\.apache\\.jasper\\..*");
    if (config.defaultServletSupport) {
      // 等价于 tomcat 的 addDefaults：war 根下的静态文件与 welcome file 由默认 servlet 提供
      wac.addServlet(new ServletHolder("default", new DefaultServlet()), "/");
    }
    return wac;
  }
}
