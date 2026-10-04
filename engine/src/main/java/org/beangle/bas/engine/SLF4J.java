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

public class SLF4J {

  public static void bridgeJul2Slf4j() {
    try {
      var clazz = Class.forName("org.slf4j.bridge.SLF4JBridgeHandler");
      clazz.getMethod("removeHandlersForRootLogger").invoke(null);
      clazz.getMethod("install").invoke(null);
    } catch (Exception e) {
    }
  }

  public static void enableLogbackDevConfig() {
    if (EnvProfile.isDevMode()) {
      if (null == System.getProperty("logback.configurationFile")) {
        var devFile = SLF4J.class.getResource("/logback-dev.xml");
        if (null != devFile) {
          System.setProperty("logback.configurationFile", devFile.toString());
        }
      }
    }
  }

}
