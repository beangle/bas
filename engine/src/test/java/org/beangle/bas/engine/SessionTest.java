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

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.beangle.bas.engine.tomcat.LazySessionIdGenerator;
import org.beangle.bas.engine.tomcat.TomcatServerBuilder;

/** 会话仍由 StandardManager 管理，但会话 id 生成器不在容器启动时预热 SecureRandom */
public class SessionTest {
  public static void main(String[] args) {
    var config = CmdOptions.parse(new String[] { "--base=/tmp/bas-session-test", "--port=0" });
    var tomcat = new TomcatServerBuilder(config).build();
    var context = (StandardContext) tomcat.getHost().findChild("");

    assertTrue(context.getManager() instanceof StandardManager, "会话应由 StandardManager 管理");
    assertTrue(context.getManager().getSessionIdGenerator() instanceof LazySessionIdGenerator,
      "会话 id 生成器不应在启动时预热 SecureRandom");
    assertTrue(((StandardManager) context.getManager()).getSecureRandomAlgorithm().isEmpty(),
      "SecureRandom 算法应交给 JDK 的平台默认");
  }

  private static void assertTrue(boolean result, String message) {
    if (!result) throw new IllegalArgumentException(message);
  }
}
