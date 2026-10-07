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

/** 启动横幅：非终端只留版本行，交互终端在版本行前加纯 ASCII 图形。 */
public class BasVersionTest {

  public static void main(String[] args) {
    // 版本号来自 build.sbt 生成的常量，测试不写死具体版本（发版不用改测试）
    var version = BasVersionInfo.VERSION;
    assertTrue(version.matches("\\d+\\.\\d+\\.\\d+.*"), "version should look like x.y.z, but was " + version);

    // 非终端（写 console.out / 管道 / CI）：只有版本行
    assertEquals("beangle bas " + version + "(tomcat)", BasVersion.banner("tomcat", false));

    // 交互终端：ASCII 图形 + 版本行，整段保持纯 ASCII
    var art = BasVersion.banner("tomcat", true);
    assertTrue(art.startsWith(" ____"), "logo should start with the figure");
    assertTrue(art.contains("(  _ \\"), "logo should read bas, not sas");
    assertTrue(art.endsWith("\nbeangle bas " + version + "(tomcat)"), "version line should follow the figure");
    for (char ch : art.toCharArray()) {
      assertTrue(ch < 128, "banner must stay in ASCII");
    }

    // 版本行：comments 为空时不带括号
    assertEquals("beangle bas " + version, BasVersion.line(null));
    assertEquals("beangle bas " + version, BasVersion.line(""));

    System.out.println("BasVersionTest passed");
  }

  private static void assertEquals(String expected, String result) {
    if (!expected.equals(result)) {
      throw new IllegalArgumentException("not equals:[" + expected + "] , result is [" + result + "]");
    }
  }

  private static void assertTrue(boolean result, String message) {
    if (!result) throw new IllegalArgumentException(message);
  }
}
