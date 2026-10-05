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

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.beangle.bas.engine.Server;
import org.eclipse.jetty.ee10.servlet.ServletHolder;
import org.eclipse.jetty.ee10.webapp.WebAppContext;
import org.eclipse.jetty.server.ServerConnector;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Jetty 嵌入式容器冒烟用例：根 context 映射（bas 的 "" → Jetty 的 "/"）、
 * 嵌入式引擎不解析 web.xml、程序化注册的 servlet 可访问、`defaultServletSupport` 开/关时静态文件的行为。
 */
public class JettyTest {

  public static void main(String[] args) throws Exception {
    testRootContext();
    testProgrammaticServlet();
    testDefaultServlet();
    System.out.println("JettyTest passed");
  }

  /** 根 context（"" → "/"）：web.xml 声明的 servlet 不生效，静态文件默认关闭直接 404。 */
  private static void testRootContext() throws Exception {
    var base = Files.createTempDirectory("jetty-base");
    var docBase = base.resolve("webapps/ROOT");
    Files.createDirectories(docBase.resolve("WEB-INF/classes"));
    // 放一份完整的 web.xml：嵌入式引擎刻意不读它（与 tomcat/undertow 对齐）
    Files.writeString(docBase.resolve("WEB-INF/web.xml"), helloWebXml());
    Files.writeString(docBase.resolve("index.html"), "<html>index</html>");

    withServer(base, docBase, false, wac -> {
    }, url -> {
      assertEquals(404, status(url + "/hello"));
      // 默认 servlet 关闭：静态文件直接 404
      assertEquals(404, status(url + "/index.html"));
    });
  }

  /** 程序化注册的 servlet（等价于 SCI 读 beangle.xml 的路径）可访问；生产模式错误页不返回调用栈。 */
  private static void testProgrammaticServlet() throws Exception {
    var base = Files.createTempDirectory("jetty-base");
    var docBase = base.resolve("webapps/ROOT");
    Files.createDirectories(docBase);

    withServer(base, docBase, false, wac -> {
      wac.addServlet(new ServletHolder("hello", new HelloServlet()), "/hello");
      wac.addServlet(new ServletHolder("error", new ErrServlet()), "/error");
    }, url -> {
      assertEquals("hello", get(url + "/hello"));
      // 生产模式错误页不返回调用栈（对齐 tomcat 的 SwallowErrorValve）
      var error = raw(url + "/error");
      assertEquals(500, error.status);
      assertTrue(error.body.contains("boom"), "error message should be shown");
      assertTrue(!error.body.contains("at org.beangle.bas.engine.jetty.JettyTest$ErrServlet"),
        "stack trace must not leak to the error page");
    });
  }

  /** `--Dserver.defaultServletSupport=true`：默认 servlet 接管静态文件（welcome file / index.html）。 */
  private static void testDefaultServlet() throws Exception {
    var base = Files.createTempDirectory("jetty-base");
    var docBase = base.resolve("webapps/ROOT");
    Files.createDirectories(docBase);
    Files.writeString(docBase.resolve("index.html"), "<html>index</html>");

    withServer(base, docBase, true, wac -> {
    }, url -> {
      assertEquals(200, status(url + "/index.html"));
      assertTrue(get(url + "/").contains("index"), "welcome file should serve index.html");
    });
  }

  /** 启动真实 Jetty（端口 0 由系统分配），把根 url 交给断言，结束后一定停服清理。 */
  private static void withServer(Path base, Path docBase, boolean defaultServlet,
      Consumer<WebAppContext> beforeStart, UrlCheck check)
      throws Exception {
    var config = new Server.Config(base.toString(), "/", 0);
    config.setDocBase(docBase.toString());
    config.defaultServletSupport = defaultServlet;

    var jetty = new JettyServerBuilder(config).build();
    beforeStart.accept((WebAppContext) jetty.getHandler());
    var server = new JettyServer(jetty);
    server.start();
    try {
      int port = ((ServerConnector) jetty.getConnectors()[0]).getLocalPort();
      check.accept("http://localhost:" + port);
    } finally {
      server.shutdown();
      config.cleanup();
    }
  }

  private static String helloWebXml() {
    return """
      <?xml version="1.0" encoding="UTF-8"?>
      <web-app xmlns="https://jakarta.ee/xml/ns/jakartaee"
               xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
               xsi:schemaLocation="https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/web-app_6_0.xsd"
               version="6.0">
        <servlet>
          <servlet-name>hello</servlet-name>
          <servlet-class>org.beangle.bas.engine.jetty.JettyTest$HelloServlet</servlet-class>
        </servlet>
        <servlet-mapping>
          <servlet-name>hello</servlet-name>
          <url-pattern>/hello</url-pattern>
        </servlet-mapping>
        <servlet>
          <servlet-name>error</servlet-name>
          <servlet-class>org.beangle.bas.engine.jetty.JettyTest$ErrServlet</servlet-class>
        </servlet>
        <servlet-mapping>
          <servlet-name>error</servlet-name>
          <url-pattern>/error</url-pattern>
        </servlet-mapping>
      </web-app>
      """;
  }

  private interface UrlCheck {
    void accept(String url) throws Exception;
  }

  public static class HelloServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      resp.setContentType("text/plain");
      resp.getWriter().write("hello");
    }
  }

  public static class ErrServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) {
      throw new RuntimeException("boom");
    }
  }

  private record Resp(int status, String body) {
  }

  private static Resp raw(String url) throws IOException {
    var conn = open(url);
    var status = conn.getResponseCode();
    var stream = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
    var body = stream == null ? "" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    return new Resp(status, body);
  }

  private static String get(String url) throws IOException {
    var conn = open(url);
    if (conn.getResponseCode() != 200) {
      throw new IllegalArgumentException("GET " + url + " -> " + conn.getResponseCode());
    }
    return new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static int status(String url) throws IOException {
    return open(url).getResponseCode();
  }

  private static HttpURLConnection open(String url) throws IOException {
    var conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);
    return conn;
  }

  private static void assertEquals(Object expected, Object result) {
    if (!expected.equals(result)) {
      throw new IllegalArgumentException("not equals:[" + expected + "] , result is [" + result + "]");
    }
  }

  private static void assertTrue(boolean value, String message) {
    if (!value) {
      throw new IllegalArgumentException(message);
    }
  }
}
