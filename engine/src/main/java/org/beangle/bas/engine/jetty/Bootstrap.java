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

import org.beangle.bas.engine.*;

import java.util.logging.Logger;

public class Bootstrap {

  public static void main(String[] args) {
    boolean isNativeImage = Server.Config.isNativeImage();
    var startAt = System.currentTimeMillis();
    SLF4J.enableLogbackDevConfig();
    SLF4J.bridgeJul2Slf4j();
    if (EnvProfile.isDevMode()) {
      System.out.println(BasVersion.logo("jetty"));
    }
    var logger = Logger.getLogger(Bootstrap.class.toString());
    Server.Config config = CmdOptions.parse(args);
    if (config.port < 0) {
      logger.severe("port " + Math.abs(config.port) + " is not available.");
      return;
    }
    var server = new JettyServerBuilder(config).build();
    final JettyServer js = new JettyServer(server);
    try {
      js.start();
    } catch (RuntimeException e) {
      // webapp 启动失败（throwUnavailableOnStartupException）时释放端口后退出
      var cause = e.getCause() == null ? e : e.getCause();
      logger.severe("Jetty failed to start: " + cause.getMessage());
      System.exit(1);
      return;
    }
    var duration = (System.currentTimeMillis() - startAt) / 1000.0;
    var url = "http://localhost:" + config.port + config.contextPath;
    logger.info("Jetty started in " + duration + "s, open " + url);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      js.shutdown();
      config.cleanup();
    }));
    if (!isNativeImage) {
      Desktops.openBrowser(url);
    }
  }
}
