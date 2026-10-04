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

import io.undertow.Undertow;
import org.beangle.bas.engine.*;

import java.util.logging.Logger;

public class Bootstrap {

  public static void main(String[] args) throws Exception {
    var startAt = System.currentTimeMillis();
    SLF4J.enableLogbackDevConfig();
    SLF4J.bridgeJul2Slf4j();
    if (EnvProfile.isDevMode()) {
      System.out.println(BasVersion.logo("undertow"));
    }
    var logger = Logger.getLogger(Bootstrap.class.toString());
    Server.Config config = CmdOptions.parse(args);
    if (config.port < 0) {
      logger.severe("port " + Math.abs(config.port) + " is not available.");
      return;
    }
    Undertow undertow = new UndertowServerBuilder(config).build();
    final UndertowServer ts = new UndertowServer(undertow);
    ts.start();
    var duration = (System.currentTimeMillis() - startAt) / 1000.0;
    var url = "http://localhost:" + config.port + config.contextPath;
    logger.info("Undertow started in " + duration + "s, open " + url);

    Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
      @Override
      public void run() {
        ts.shutdown();
        config.cleanup();
      }
    }));
    Desktops.openBrowser(url);
  }
}
