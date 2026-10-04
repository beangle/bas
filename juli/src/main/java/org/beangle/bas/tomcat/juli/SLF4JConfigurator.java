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

package org.beangle.bas.tomcat.juli;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.Configurator;
import ch.qos.logback.core.joran.spi.JoranException;
import ch.qos.logback.core.spi.ContextAwareBase;
import ch.qos.logback.core.status.InfoStatus;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * 查找logback-catalina.xml位置，进行配置，并且禁止其他内置的Configurator
 */
public class SLF4JConfigurator extends ContextAwareBase implements Configurator {

  @Override
  public ExecutionStatus configure(LoggerContext lc) {
    var basHome = System.getProperty("bas.home");
    String url;
    try {
      File confFile = new File(basHome + "/conf/logback-catalina.xml");
      if (confFile.exists()) {
        url = confFile.toURI().toURL().toString();
      } else {
        var resource = getClass().getClassLoader().getResource("logback-catalina.xml");
        if (null == resource) throw new RuntimeException("Cannot find logback-catalina.xml in classpath");
        url = resource.toString();
      }
      JoranConfigurator configurator = new JoranConfigurator();
      configurator.setContext(lc);
      lc.getStatusManager().add(new InfoStatus("Found resource [" + url + "]", this));
      lc.reset();

      configurator.doConfigure(new URL(url));
      lc.start();
    } catch (JoranException | MalformedURLException e) {
      throw new RuntimeException(e);
    }

    //这一个可以禁用其他配置
    return ExecutionStatus.DO_NOT_INVOKE_NEXT_IF_ANY;
  }

}
