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

package org.beangle.bas.engine.undertow;

import io.undertow.Handlers;
import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.handlers.resource.FileResourceManager;
import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import io.undertow.servlet.api.ServletContainer;
import io.undertow.servlet.api.ServletContainerInitializerInfo;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.servlet.api.ServletStackTraces;
import io.undertow.servlet.handlers.DefaultServlet;
import jakarta.servlet.ServletContainerInitializer;
import jakarta.servlet.ServletException;
import jakarta.servlet.SessionTrackingMode;
import org.beangle.bas.engine.Server;
import org.xnio.Options;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Set;

public class UndertowServerBuilder {
  private final Server.Config config;

  public UndertowServerBuilder(Server.Config config) {
    this.config = config;
  }

  public Undertow build() throws ServletException {
    Undertow.Builder builder = Undertow.builder();

    config.getInt("buffer-size").ifPresent(builder::setBufferSize);
    config.getInt("io-thread").ifPresent(builder::setIoThreads);
    config.getInt("worker-threads").ifPresent(builder::setWorkerThreads);
    config.getBoolean("direct-buffers").ifPresent(builder::setDirectBuffers);
    // 对齐 tomcat 的 connector.acceptCount：accept 队列长度（Undertow 默认 1000）
    config.getInt("connector.acceptCount").ifPresent(count -> builder.setSocketOption(Options.BACKLOG, count));
    // 对齐 tomcat 的 connector.connectionTimeout：Undertow 用单一 NO_REQUEST_TIMEOUT 覆盖首请求与 keep-alive 空闲
    var noRequestTimeout = config.getInt("connector.connectionTimeout");
    if (noRequestTimeout.isEmpty()) noRequestTimeout = config.getInt("connector.keepAliveTimeout");
    noRequestTimeout.ifPresent(timeout -> builder.setServerOption(UndertowOptions.NO_REQUEST_TIMEOUT, timeout));

    builder.addHttpListener(config.port, null);
    builder.setServerOption(UndertowOptions.SHUTDOWN_TIMEOUT, 0);
    builder.setServerOption(UndertowOptions.ENABLE_HTTP2, false);
    ServletContainer sc = Servlets.newContainer();
    builder.setHandler(createDeployments(sc));
    return builder.build();
  }

  /** 定制 deployment：嵌入方可在此追加 servlet、listener 或 initializer */
  protected void customize(DeploymentInfo deployment) {
  }

  private ClassLoader getServletClassLoader() {
    return getClass().getClassLoader();
  }

  private void addInitializers(DeploymentInfo deployment) {
    var classLoader = getServletClassLoader();
    try {
      var urls = classLoader.getResources("META-INF/services/jakarta.servlet.ServletContainerInitializer");
      while (urls.hasMoreElements()) {
        var url = urls.nextElement();
        var serviceName = readServiceName(url);
        //不是竞品的初始化服务
        if (null != serviceName && !serviceName.startsWith("org.apache.tomcat.") && !serviceName.startsWith("org.eclipse.jetty.")) {
          var clazz = (Class<? extends ServletContainerInitializer>) classLoader.loadClass(serviceName);
          deployment.addServletContainerInitializer(new ServletContainerInitializerInfo(clazz, Collections.emptySet()));
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private HttpHandler createDeployments(ServletContainer sc) throws ServletException {
    DeploymentInfo di = Servlets.deployment();
    addInitializers(di);

    di.setUrlEncoding("UTF-8");
    di.setDefaultEncoding("UTF-8");
    di.setDefaultRequestEncoding("UTF-8");
    di.setClassLoader(getServletClassLoader());
    di.setContextPath(config.contextPath);
    //di.setResourceManager(new DefaultResourceLoader());
    di.setDeploymentName(config.contextPath.replace('/', '_'));
    if (config.defaultServletSupport) {
      di.addServlet(Servlets.servlet("default", DefaultServlet.class));
    }
    di.setServletStackTraces(ServletStackTraces.NONE);
    di.setEagerFilterInit(true);
    di.setTempDir(new File(config.base + File.separator + "temp"));

    di.setResourceManager(new FileResourceManager(new File(config.docBase), 1024));
    // 只保留 Cookie 会话跟踪，避免 ;jsessionid 出现在 URL/Referer/日志中；Undertow 默认不下发 HttpOnly（tomcat/jetty 默认下发）
    di.setServletSessionConfig(new ServletSessionConfig()
      .setHttpOnly(true)
      .setSessionTrackingModes(Set.of(SessionTrackingMode.COOKIE)));
    //ignore mimetype registration
    customize(di);
    var manager = sc.addDeployment(di);
    manager.deploy();
    var sm = manager.getDeployment().getSessionManager();
    sm.setDefaultSessionTimeout(config.defaultSessionTimeout);

    var h = manager.start();
    var ctxPath = config.contextPath.isEmpty() ? "/" : config.contextPath;
    var pathHandler = Handlers.path();
    pathHandler.addPrefixPath(ctxPath, h);
    return pathHandler;
  }

  private String readServiceName(URL url) {
    String serviceName = null;
    try (InputStream inputStream = url.openStream();
         InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
         LineNumberReader lineNumberReader = new LineNumberReader(reader)) {
      String line;
      while ((line = lineNumberReader.readLine()) != null) {
        line = line.trim();
        if (!line.startsWith("#")) {
          serviceName = line;
        }
      }

    } catch (IOException e) {
    }
    return serviceName;
  }

}
