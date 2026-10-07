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

public class BasVersion {

  /** 启动标志：纯 ASCII，任何字符集的终端都能显示（与 basctl 的 `asciiLogo()` 是同一份图形）。 */
  static final String ASCII_LOGO = """
 ____    __    ___
(  _ \\  /__\\  / __)
 ) _ < /(__)\\ \\__ \\
(____/(__)(__)(___/
""";

  /**
   * 启动横幅：图形 + 版本行，`comments` 为容器名（tomcat / undertow / jetty）。
   *
   * 图形只在交互终端（{@code System.console() != null}）出现；标准输出被重定向（写 `console.out`、
   * 管道、CI）时只留版本行，日志里不留图形。
   */
  public static String banner(String comments) {
    return banner(comments, System.console() != null);
  }

  /** 供测试与调试指定终端条件，见 {@link #banner(String)}。 */
  static String banner(String comments, boolean console) {
    var line = line(comments);
    return console ? ASCII_LOGO + line : line;
  }

  /**
   * 版本行，如 `beangle bas <version>(tomcat)(DEV mode)`。
   *
   * 版本号取自编译期生成的 {@link BasVersionInfo}（唯一来源是 build.sbt 的 `version`），
   * 因此发版只需改 build.sbt。
   */
  public static String line(String comments) {
    var str = "beangle bas " + BasVersionInfo.VERSION;
    if (null != comments && !comments.isEmpty()) {
      str += "(" + comments + ")";
    }
    if (EnvProfile.isDevMode()) {
      str += "(DEV mode)";
    }
    return str;
  }
}
