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

import io.undertow.servlet.Servlets;
import io.undertow.servlet.api.DeploymentInfo;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.beangle.bas.engine.Server;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Undertow 嵌入式容器冒烟用例：只保留 Cookie 会话跟踪，URL 上不再出现 ;jsessionid。
 * Undertow 不解析 web.xml，因此这里通过 {@code customize} 钩子注册测试 servlet。
 */
public class UndertowTest {

  public static void main(String[] args) throws Exception {
    var base = Files.createTempDirectory("undertow-base");
    var docBase = base.resolve("webapps/ROOT");
    Files.createDirectories(docBase);

    var config = new Server.Config(base.toString(), "/", 0);
    config.setDocBase(docBase.toString());

    var undertow = new UndertowServerBuilder(config) {
      @Override
      protected void customize(DeploymentInfo deployment) {
        deployment.addServlet(Servlets.servlet("session", SessionServlet.class).addMapping("/session"));
      }
    }.build();
    var server = new UndertowServer(undertow);
    server.start();
    try {
      var address = (InetSocketAddress) undertow.getListenerInfo().get(0).getAddress();
      var url = "http://localhost:" + address.getPort() + "/session";
      var resp = get(url);
      var parts = resp.body.split("\\|");
      assertEquals("[COOKIE]", parts[0]);
      assertEquals("/keep", parts[1]);
      // Cookie 跟踪仍生效：会话 id 就是 Set-Cookie 里下发的 JSESSIONID
      assertTrue(resp.setCookie != null && resp.setCookie.startsWith("JSESSIONID=" + parts[2]),
        "session cookie should carry the created session id, but was [" + resp.setCookie + "]");
      assertTrue(resp.setCookie.contains("HttpOnly"), "session cookie should be HttpOnly");
    } finally {
      server.shutdown();
      config.cleanup();
    }
    System.out.println("UndertowTest passed");
  }

  public static class SessionServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
      var session = req.getSession(true);
      resp.setContentType("text/plain");
      resp.getWriter().write(req.getServletContext().getEffectiveSessionTrackingModes()
        + "|" + resp.encodeURL("/keep") + "|" + session.getId());
    }
  }

  private record Resp(String body, String setCookie) {
  }

  private static Resp get(String url) throws IOException {
    var conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
    conn.setRequestMethod("GET");
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(5000);
    if (conn.getResponseCode() != 200) {
      throw new IllegalArgumentException("GET " + url + " -> " + conn.getResponseCode());
    }
    var body = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return new Resp(body, conn.getHeaderField("Set-Cookie"));
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
