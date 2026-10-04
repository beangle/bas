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

import org.apache.catalina.startup.Tomcat;
import org.beangle.bas.engine.*;

import java.util.logging.Logger;

public class Bootstrap {

  public static void main(String[] args) {
    boolean isNativeImage = Server.Config.isNativeImage();
    var startAt = System.currentTimeMillis();
    SLF4J.enableLogbackDevConfig();
    SLF4J.bridgeJul2Slf4j();
    if (EnvProfile.isDevMode()) {
      System.out.println(BasVersion.logo("tomcat"));
    }
    var logger = Logger.getLogger(Bootstrap.class.toString());
    Server.Config config = CmdOptions.parse(args);
    if (config.port < 0) {
      logger.severe("port " + Math.abs(config.port) + " is not available.");
      return;
    }
    Tomcat tomcat = new TomcatServerBuilder(config).build();
    final TomcatServer ts = new TomcatServer(tomcat);
    ts.start();
    var duration = (System.currentTimeMillis() - startAt) / 1000.0;
    var url = "http://localhost:" + config.port + config.contextPath;
    logger.info("Tomcat started in " + duration + "s, open " + url);

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      ts.shutdown();
      config.cleanup();
    }));
    // native 镜像下 AWT 不可用（GraphicsEnvironment 初始化会触发 JNI 致命错误），
    // 且服务端场景无桌面可打开浏览器，直接跳过
    if (!isNativeImage) {
      Desktops.openBrowser(url);
    }
  }

}
