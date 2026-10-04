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

package org.beangle.bas.engine;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class CmdOptions {

  private static final Logger logger = Logger.getLogger(CmdOptions.class.getName());

  public static Server.Config parse(String[] args) {
    String base = null;
    String path = "";
    String docBase = null;
    int port = -1;
    boolean devMode = false;
    Map<String, String> properties = new HashMap<>();
    for (String arg : args) {
      if (arg.startsWith("--")) {
        if (arg.startsWith("--path=")) {
          path = arg.substring("--path=".length());
        } else if (arg.startsWith("--docBase=")) {
          docBase = arg.substring("--docBase=".length()).trim();
        } else if (arg.startsWith("--port=")) {
          port = toInt("--port", arg.substring("--port=".length()));
        } else if (arg.startsWith("--dev=")) {
          devMode = Boolean.parseBoolean(arg.substring("--dev=".length()));
          if (devMode) EnvProfile.enableDevMode();
        } else if (arg.startsWith("--base=")) {
          base = arg.substring("--base=".length()).trim();
        } else if (arg.startsWith("--D")) {
          //引擎属性，形如 --Dconnector.maxConnections=20000，无值(如 --Dxxx)时视为 true
          int idx = arg.indexOf('=');
          if (idx > 3) {
            properties.put(arg.substring(3, idx).trim(), arg.substring(idx + 1).trim());
          } else if (idx < 0 && arg.length() > 3) {
            properties.put(arg.substring(3).trim(), "true");
          } else {
            logger.warning("Malformed engine property [" + arg + "] ignored, expected --Dkey=value.");
          }
        } else {
          logger.warning("Unknown option [" + arg + "] ignored, engine properties are passed as --Dkey=value.");
        }
      } else {
        logger.warning("Unknown argument [" + arg + "] ignored, JVM options(-D/-X) should be given to java.");
      }
    }
    if (port == -1) {
      for (int i = 0; i < 100; i++) {
        port = 8080 + i;
        if (Tools.isPortFree(port)) break;
      }
    } else {
      if (!Tools.isPortFree(port)) {
        port = -Math.abs(port);
      }
    }

    Server.Config config = new Server.Config(Server.Config.initBase(base).getAbsolutePath(), path, port);
    config.properties.putAll(properties);
    config.defaultServletSupport = config.getBoolean("server.defaultServletSupport").orElse(config.defaultServletSupport);
    config.devMode = devMode || EnvProfile.isDevMode();
    config.setDocBase(docBase);
    return config;
  }

  private static int toInt(String name, String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(name + " expects a number but was [" + value + "]", e);
    }
  }
}
