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

import java.awt.*;
import java.net.URI;

public class Desktops {

  public static void openBrowser(String url) {
    try {
      if (GraphicsEnvironment.isHeadless()) {
        return;
      }
      GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
      if (ge.isHeadlessInstance()) {
        return;
      }
      var desktopClass = Class.forName("java.awt.Desktop");
      var supported = (boolean) desktopClass.getMethod("isDesktopSupported").invoke(null);
      var uri = new URI(url);
      if (supported) {
        var desktop = desktopClass.getMethod("getDesktop").invoke(null);
        desktopClass.getMethod("browse", URI.class).invoke(desktop, uri);
        return;
      }
      var osName = System.getProperty("os.name").toLowerCase();
      var rt = Runtime.getRuntime();
      if (osName.contains("windows")) {
        rt.exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
      } else if (osName.contains("mac") || osName.contains("darwin")) {
        rt.exec(new String[]{"open", url});
      } else {
        var browsers = new String[]{"xdg-open", "chromium", "google-chrome", "firefox", "konqueror", "netscape", "opera", "midori"};
        for (String b : browsers) {
          try {
            rt.exec(new String[]{b, url});
            break;
          } catch (Throwable e) {
          }
        }
      }
    } catch (Throwable e) {
      //opening browser is optional,never block or crash server startup
    }
  }
}
