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

public class ConfigTest {
  public static void main(String[] args) {
    assertEquals("", Server.Config.normalizePath(null));
    assertEquals("", Server.Config.normalizePath("/"));
    assertEquals("/api", Server.Config.normalizePath("api"));
    assertEquals("/api", Server.Config.normalizePath("api/"));
    assertEquals("/api", Server.Config.normalizePath("//api"));

    var config = new Server.Config("/tmp/bas-config-test", "/", 8080);

    // 未配置时返回 empty，而不是 null
    assertTrue(config.getInt("missing").isEmpty(), "missing int should be empty");
    assertTrue(config.getBoolean("missing").isEmpty(), "missing boolean should be empty");
    assertTrue(config.getProperty("missing").isEmpty(), "missing property should be empty");

    config.properties.put("counter", "42");
    assertEquals(42, config.getInt("counter").getAsInt());
    assertEquals(42, config.getInt("counter").orElse(7));
    assertEquals(7, config.getInt("missing").orElse(7));

    // 布尔值只认 true/false(忽略大小写)
    config.properties.put("flag", "TRUE");
    assertTrue(config.getBoolean("flag").orElse(false), "TRUE should parse as true");

    // 空白值等同于未配置
    config.properties.put("blank", "   ");
    assertTrue(config.getProperty("blank").isEmpty(), "blank property should be empty");

    // --Dkey=value 优先于系统属性 -Dkey=value
    try {
      System.setProperty("counter", "9");
      assertEquals(42, config.getInt("counter").getAsInt());
      config.properties.remove("counter");
      assertEquals(9, config.getInt("counter").getAsInt());
    } finally {
      System.clearProperty("counter");
    }

    config.properties.put("bad", "abc");
    assertErrorContains(() -> config.getInt("bad"), "Property [bad] expects a number");

    config.properties.put("flag", "yes");
    assertErrorContains(() -> config.getBoolean("flag"), "Property [flag] expects true/false");

    // 命令行 --Dkey=value 落到 properties
    var parsed = CmdOptions.parse(new String[] { "--base=/tmp/bas-config-test", "--port=0", "--Dcounter=5", "--Dflag" });
    assertEquals(5, parsed.getInt("counter").getAsInt());
    assertTrue(parsed.getBoolean("flag").orElse(false), "--Dflag should be true");

    // 默认 servlet 默认关闭，可用 --D 打开
    var noDefaultServlet = CmdOptions.parse(new String[] { "--base=/tmp/bas-config-test", "--port=0" });
    assertTrue(!noDefaultServlet.defaultServletSupport, "default servlet should be off by default");
    var withDefaultServlet = CmdOptions.parse(new String[] { "--base=/tmp/bas-config-test", "--port=0", "--Dserver.defaultServletSupport=true" });
    assertTrue(withDefaultServlet.defaultServletSupport, "default servlet should be enabled by --D");
  }

  private static void assertEquals(String expected, String result) {
    if (!expected.equals(result)) {
      throw new IllegalArgumentException("not equals:[" + expected + "] , result is [" + result + "]");
    }
  }

  private static void assertEquals(int expected, int result) {
    if (expected != result) {
      throw new IllegalArgumentException("not equals:[" + expected + "] , result is [" + result + "]");
    }
  }

  private static void assertTrue(boolean result, String message) {
    if (!result) throw new IllegalArgumentException(message);
  }

  private static void assertErrorContains(Runnable action, String expected) {
    try {
      action.run();
    } catch (IllegalArgumentException e) {
      if (null != e.getMessage() && e.getMessage().contains(expected)) return;
      throw new IllegalArgumentException("error message [" + e.getMessage() + "] does not contain [" + expected + "]");
    }
    throw new IllegalArgumentException("expected IllegalArgumentException containing [" + expected + "]");
  }
}
