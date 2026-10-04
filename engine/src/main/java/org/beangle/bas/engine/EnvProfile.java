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

import java.lang.management.ManagementFactory;
import java.util.LinkedHashSet;
import java.util.Set;

public class EnvProfile {

  /** 与 beangle-commons 的 Environment.ProfileKey 保持一致 */
  private static final String profileKey = "beangle.config.profiles";

  public static boolean isDebugMode() {
    var args = ManagementFactory.getRuntimeMXBean().getInputArguments();
    return args.toString().indexOf("-agentlib:jdwp") > 0;
  }

  /**
   * 当前激活的 profile 集合，逻辑对齐 beangle-commons 的 Environment.profiles：
   * 逗号分隔、去空格与空项，调试模式(JDWP)下自动加入 dev，除非显式声明了 -dev。
   */
  public static Set<String> profiles() {
    Set<String> profiles = new LinkedHashSet<String>();
    String value = System.getProperty(profileKey);
    if (null != value) {
      for (String profile : value.split(",")) {
        String p = profile.trim();
        if (!p.isEmpty()) profiles.add(p);
      }
    }
    if (isDebugMode() && !profiles.contains("-dev")) profiles.add("dev");
    return profiles;
  }

  public static boolean isDevMode() {
    return profiles().contains("dev");
  }

  public static void enableDevMode() {
    Set<String> profiles = profiles();
    if (profiles.contains("dev")) return;
    profiles.add("dev");
    System.setProperty(profileKey, String.join(",", profiles));
  }
}
