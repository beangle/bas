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

package org.beangle.bas.engine.tomcat;

import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.SessionTrackingMode;
import org.apache.catalina.*;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.core.*;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.startup.Constants;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.http11.Http11NioProtocol;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.scan.StandardJarScanner;
import org.beangle.bas.engine.Server;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class TomcatServerBuilder {
  private final Server.Config config;

  public TomcatServerBuilder(Server.Config config) {
    this.config = config;
  }

  public Tomcat build() {
    // 禁用 MBean 注册，避免解析 mbeans-descriptors.xml 失败；须在 Registry 首次使用前调用
    Registry.disableRegistry();

    Tomcat tomcat = new Tomcat();
    tomcat.setBaseDir(config.base);
    //tomcat is startup class
    //server{service{engine{host{context}}}}
    configConnector(tomcat);
    configEngine(tomcat.getEngine());
    configHost((StandardHost) tomcat.getHost());
    prepareContext(tomcat, tomcat.getHost());
    // 嵌入式单应用没有重载/自动部署，server 级 PERIODIC_EVENT 无人消费：
    // 消费方只有 HostConfig.check()(autoDeploy=false 时直接返回) 与 TLS 证书重载监听的注册；ContainerBase 自己的事件不受影响
    ((StandardServer) tomcat.getServer()).setPeriodicEventDelay(0);
    return tomcat;
  }

  /**
   * 在这里配置线程
   *
   * @param tomcat
   */
  protected void configConnector(Tomcat tomcat) {
    Connector connector = new Connector("org.apache.coyote.http11.Http11NioProtocol");
    connector.setThrowOnFailure(true);
    connector.setPort(config.port);
    connector.setScheme("http");
    connector.setURIEncoding("UTF-8"); //设置编码
    connector.setXpoweredBy(false);
    connector.setProperty("bindOnInit", "false");

    org.apache.coyote.http11.Http11NioProtocol protocol =
      (org.apache.coyote.http11.Http11NioProtocol) connector.getProtocolHandler();

    protocol.setSSLEnabled(false);
    if (config.devMode) {
      protocol.setTcpNoDelay(true);// 禁用 TCP 延迟（Nagle 算法），提升实时性
    }
    //启用虚拟线程
    protocol.setExecutor(Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual().name("tomcat-vt-", 0).factory()
    ));

    protocol.setMaxConnections(config.getInt("connector.maxConnections").orElse(10000));

    //等待队列大小，超过最大线程时，最多排队 acceptCount 个请求
    protocol.setAcceptCount(config.getInt("connector.acceptCount").orElse(1000));

    config.getInt("connector.connectionTimeout").ifPresent(protocol::setConnectionTimeout);
    config.getInt("connector.keepAliveTimeout").ifPresent(protocol::setKeepAliveTimeout);
    config.getInt("connector.maxKeepAliveRequests").ifPresent(protocol::setMaxKeepAliveRequests);

    config.getInt("connector.processorCache").ifPresent(processorCache -> {
      // Tomcat 语义：-1 表示不限制（AbstractProtocol.ConnectionHandler.push）
      if (processorCache < -1) {
        throw new IllegalArgumentException("Property [connector.processorCache] expects -1(unlimited) or a non-negative number but was [" + processorCache + "]");
      }
      protocol.setProcessorCache(processorCache);
    });

    // 每连接的应用层读写缓冲，默认 8K（大文件响应走 sendfile，不经过这两个缓冲）
    config.getInt("connector.appReadBufSize")
      .ifPresent(size -> setSocketProperty(protocol, "appReadBufSize", size));
    config.getInt("connector.appWriteBufSize")
      .ifPresent(size -> setSocketProperty(protocol, "appWriteBufSize", size));

    tomcat.getService().addConnector(connector);
    tomcat.setConnector(connector);
  }

  private static void setSocketProperty(Http11NioProtocol protocol, String name, int size) {
    if (size <= 0) {
      throw new IllegalArgumentException("Property [connector." + name + "] expects a positive number but was [" + size + "]");
    }
    if (!protocol.setProperty("socket." + name, String.valueOf(size))) {
      throw new IllegalArgumentException("Property [connector." + name + "] is not supported by " + protocol.getClass().getName());
    }
  }

  protected void configEngine(Engine engine) {
    //engine.addLifecycleListener(new GlobalResourcesLifecycleListener());
    // backgroundProcessorDelay 是 host/context 后台处理的唯一驱动(ContainerBase.threadStart 仅在 delay>0 时调度)，
    // 设为 0 会使会话永不过期、静态资源缓存不回收、dev 热加载失效。会话实际过期粒度 = delay × processExpiresFrequency(默认 6)。
    var delay = config.getInt("engine.backgroundProcessorDelay").orElse(config.devMode ? 5 : config.backgroundProcessorDelay);
    engine.setBackgroundProcessorDelay(Math.max(1, delay));
  }

  protected void configHost(StandardHost host) {
    host.setAutoDeploy(false);
    host.setDeployOnStartup(false);
    if (!config.devMode) {
      host.setErrorReportValveClass("org.beangle.bas.engine.tomcat.SwallowErrorValve");
    }
  }

  protected void prepareContext(Tomcat tomcat, Host host) {
    StandardContext context = new StandardContext();
    context.setName(config.contextPath);
    context.setPath(config.contextPath);
    skipScanning(context); // disable scanning
    // 嵌入式单应用不存在重载/卸载后继续运行的场景，关闭这两项泄漏检查：
    // 它们靠反射扫描 ThreadLocal/RMI Target，没有 --add-opens 时只会打出警告。
    // 注意 setClearReferences* 的读取方是 context 而非 classloader（StandardContext.startInternal 会下发到 classloader）。
    // 停掉 webapp 线程(clearReferencesThreads)与注销 JDBC 驱动(clearReferencesJdbc)不受影响。
    context.setClearReferencesThreadLocals(false);
    context.setClearReferencesRmiTargets(false);
    // 嵌入式不支持 JSP，挡掉 Jasper 的 SCI（容器侧与应用侧同一个过滤正则）
    String sciFilter = "JasperInitializer";
    context.setContainerSciFilter(sciFilter);
    Pattern sciFilterPattern = Pattern.compile(sciFilter);

    var parentClassLoader = Thread.currentThread().getContextClassLoader();
    context.setParentClassLoader(parentClassLoader);
    context.setUseNaming(false);//嵌入式单应用不使用 JNDI，避免创建 NamingResources/NamingContextListener

    WebappLoader loader = new WebappLoader();
    //under graalvm or jvm
    if (JreCompat.isGraalAvailable()) {
      // native 模式下使用 config.docBase（已由 guessDocBase 解析为有效路径）
      context.setDocBase(config.docBase);
      loader.setLoaderInstance(new EmbeddedClassLoader(parentClassLoader));
      loader.setDelegate(true);
      context.addLifecycleListener(new FixContextListener());
      addInitializers(context, sciFilterPattern);
      context.setResourceOnlyServlets("default");
      disableTomcatSSL();
    } else {
      //embedded 模式
      disableTomcatSSL();
      context.setDocBase(config.docBase);
      loader.setLoaderInstance(new EmbeddedClassLoader(parentClassLoader));
      loader.setDelegate(true);
      context.addLifecycleListener(new FixContextListener());

      addInitializers(context, sciFilterPattern);
    }
    context.setLoader(loader);

    // default servlet and jsp
    addDefaults(context);

    if (config.devMode) context.setReloadable(true);
    context.setDefaultWebXml(Constants.NoDefaultWebXml);
    host.addChild(context);
  }

  private static void disableTomcatSSL() {
    // 禁用 OpenSSL 检测（Tomcat 不会尝试加载 libtcnative-1.dll 等 OpenSSL 库）
    System.setProperty("org.apache.tomcat.util.net.openssl.OpenSSL.disable", "true");
    // 禁用 JSSE SSL 实现（若无需 JSSE 提供的 SSL 功能）
    System.setProperty("org.apache.tomcat.util.net.jsse.JSSESocketFactory.disable", "true");
    // 禁用 SSL 主机配置自动初始化
    System.setProperty("org.apache.catalina.connector.Connector.SSL_HOST_CONFIG_DISABLED", "true");
    // 禁用 Tomcat 对 SSL 协议的检测
    System.setProperty("org.apache.tomcat.util.net.SSLProtocol.disable", "true");
  }

  private void skipScanning(StandardContext context) {
    StandardJarScanner scanner = new StandardJarScanner();
    scanner.setScanAllDirectories(false);
    scanner.setScanAllFiles(false);
    scanner.setScanClassPath(false);
    scanner.setScanManifest(false);
    scanner.setScanBootstrapClassPath(false);
    context.setJarScanner(scanner);
    context.setIgnoreAnnotations(true);
  }

  private void addInitializers(StandardContext context, Pattern filter) {
    try {
      ServiceLoader<ServletContainerInitializer> loader = ServiceLoader.load(ServletContainerInitializer.class);
      var services = new ArrayList<ServletContainerInitializer>();
      var iter = loader.iterator();
      while (iter.hasNext()) {
        var s = iter.next();
        var clazzName = s.getClass().getName();
        if (null == filter || !filter.matcher(clazzName).find()) {
          services.add(s);
        }
      }
      for (var s : services) {
        context.addServletContainerInitializer(s, null);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void addDefaults(Context ctx) {
    // Default servlet
    if (config.defaultServletSupport) {
      Wrapper dft = Tomcat.addServlet(ctx, "default", "org.apache.catalina.servlets.DefaultServlet");
      dft.setLoadOnStartup(1);
      dft.setOverridable(true);
      dft.addInitParameter("debug", "0");
      dft.addInitParameter("listings", "false");
      ctx.addServletMappingDecoded("/", "default");
    }

    // Sessions(minutes)
    ctx.setSessionTimeout(config.defaultSessionTimeout);
    // 会话 id 生成器推迟 SecureRandom 初始化（Tomcat 默认在启动时预热，实测 25~35ms）
    var manager = new StandardManager();
    manager.setSessionIdGenerator(new LazySessionIdGenerator());
    // 空串表示交给 JDK 的平台默认：NativePRNG 可用则用它(Linux/macOS)，否则用平台实现(如 DRBG)。
    // Tomcat 默认固定 SHA1PRNG，首次播种实测多花 ~10ms，且它自己也只在没有 SHA1PRNG 的 JRE 上才回退平台默认。
    manager.setSecureRandomAlgorithm("");
    ctx.setManager(manager);

    // MIME type mappings
    Tomcat.addDefaultMimeTypeMappings(ctx);
  }

  static class FixContextListener implements LifecycleListener {
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
      try {
        Context context = (Context) event.getLifecycle();
        if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
          context.setConfigured(true);
          // 只保留 Cookie 会话跟踪，避免 ;jsessionid 出现在 URL/Referer/日志中；必须在 STARTING_PREP 期间设置
          context.getServletContext().setSessionTrackingModes(EnumSet.of(SessionTrackingMode.COOKIE));
        }
      } catch (ClassCastException e) {
      }
    }
  }
}
