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

import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.StandardSessionIdGenerator;

/**
 * 与 Tomcat 默认的会话 id 生成器行为一致，只是不在容器启动时预热 SecureRandom。
 * <p>
 * Tomcat 的 {@code SessionIdGeneratorBase.startInternal()} 会先造一个会话 id 来强制初始化 SecureRandom
 * （默认 SHA1PRNG，实测 25~35ms；低熵环境或旧 JDK 上可能到秒级）。嵌入式应用通常并不创建容器会话，
 * 这份开销推迟到第一次真正创建会话时才付出，启动路径不再受它影响。
 */
public class LazySessionIdGenerator extends StandardSessionIdGenerator {

  @Override
  protected void startInternal() throws LifecycleException {
    setState(LifecycleState.STARTING);
  }
}
