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

import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.apache.tomcat.util.compat.JreCompat;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;

/**
 * Extension of Tomcat's {@link ParallelWebappClassLoader} that does not consider the
 * {@link ClassLoader#getSystemClassLoader() system classloader},
 * Migrate from spring-boot TomcatEmbeddedWebappClassLoader.
 */
public class EmbeddedClassLoader extends ParallelWebappClassLoader {

  public EmbeddedClassLoader(ClassLoader parent) {
    super(parent);
  }

  @Override
  public URL findResource(String name) {
    return null;
  }

  @Override
  public Enumeration<URL> findResources(String name) throws IOException {
    return Collections.emptyEnumeration();
  }

  @Override
  public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
    synchronized (JreCompat.isGraalAvailable() ? this : getClassLoadingLock(name)) {
      Class<?> result = findLoaded(name);
      result = (result != null) ? result : doLoadClass(name);
      if (result == null) {
        throw new ClassNotFoundException(name);
      }
      if (resolve) resolveClass(result);
      return result;
    }
  }

  private Class<?> findLoaded(String name) {
    Class<?> resultClass = findLoadedClass0(name);
    resultClass = (resultClass != null || JreCompat.isGraalAvailable()) ? resultClass : findLoadedClass(name);
    return resultClass;
  }

  private Class<?> doLoadClass(String name) throws ClassNotFoundException {
    if ((this.delegate || filter(name, true))) {
      var result = find(name, this.parent);
      return (result != null) ? result : find(name);
    } else {
      var result = find(name);
      return (result != null) ? result : find(name, this.parent);
    }
  }

  @Override
  protected void addURL(URL url) {
    System.out.println("Ignoring request to add " + url + " to the tomcat classloader");
  }

  private Class<?> find(String name, ClassLoader loader) {
    try {
      return Class.forName(name, false, loader);
    } catch (ClassNotFoundException ex) {
      return null;
    }
  }

  private Class<?> find(String name) {
    try {
      return findClass(name);
    } catch (ClassNotFoundException ex) {
      return null;
    }
  }

}
