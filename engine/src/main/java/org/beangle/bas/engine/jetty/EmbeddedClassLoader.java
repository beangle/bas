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

package org.beangle.bas.engine.jetty;

import org.eclipse.jetty.ee10.webapp.WebAppClassLoader;
import org.eclipse.jetty.ee10.webapp.WebAppContext;

import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Extension of Jetty's {@link WebAppClassLoader} that does not expose the webapp's own
 * classpath ({@code WEB-INF/classes}、{@code WEB-INF/lib}) as resources.
 *
 * <p>嵌入式模型下应用类与依赖都由 basctl 放在 JVM {@code -cp} 上（父加载器优先），Jetty 默认
 * 还会把 {@code WEB-INF/classes}、{@code WEB-INF/lib} 作为 webapp 类加载器自己的资源根，同一个
 * 目录于是可从父加载器与 webapp 两侧各取一次：{@code getResources} 会枚举出重复的 URL，
 * {@code classpath*:} 的配置（如 {@code beangle.xml}）就会被合并两遍。
 *
 * <p>这里与 tomcat 的 {@link org.beangle.bas.engine.tomcat.EmbeddedClassLoader} 对齐：webapp 侧
 * 不提供资源，统一由父加载器给出。类的加载不受影响——{@link java.net.URLClassLoader#findClass}
 * 不经过这两个方法。
 */
public class EmbeddedClassLoader extends WebAppClassLoader {

  public EmbeddedClassLoader(ClassLoader parent, WebAppContext context) {
    super(parent, context);
  }

  @Override
  public URL findResource(String name) {
    return null;
  }

  @Override
  public Enumeration<URL> findResources(String name) {
    return Collections.emptyEnumeration();
  }
}
