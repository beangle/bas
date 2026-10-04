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

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * webapp启动失败时的守护监听器。
 *
 * <p>Tomcat对context启动失败（如数据库连不上导致listener抛错）只把状态置为不可用，
 * 既不抛异常也不停止服务，于是Connector照常绑定端口，进程看起来正常却不可用。
 * 应用全部没起来时进程已无可服务的对象，这里直接退出JVM释放端口，修好配置后start.sh即可恢复。
 * 只要还有一个应用可用就只打印错误，避免影响同机的其他应用。
 */
public class WebappFailFastListener implements LifecycleListener {

  private static final Logger log = Logger.getLogger(WebappFailFastListener.class.getName());

  @Override
  public void lifecycleEvent(LifecycleEvent event) {
    if (!Lifecycle.AFTER_START_EVENT.equals(event.getType())) return;
    if (!(event.getLifecycle() instanceof Server server)) return;

    var total = 0;
    var failed = new ArrayList<String>();
    for (Service service : server.findServices()) {
      if (!(service.getContainer() instanceof Engine engine)) continue;
      for (Container host : engine.findChildren()) {
        Container[] webapps = host.findChildren();
        total += webapps.length;
        for (Container webapp : webapps) {
          if (!webapp.getState().isAvailable()) failed.add(name(webapp));
        }
      }
    }
    if (failed.isEmpty()) return;
    if (failed.size() == total) {
      var who = 1 == total ? "Webapp " + failed.getFirst() : "All webapps " + failed;
      log.severe(who + " failed to start, bas exits to release the port.");
      System.exit(1);
    }
    log.severe("Webapps " + failed + " failed to start, server keeps running.");
  }

  static String name(Container webapp) {
    var name = webapp.getName();
    return null == name || name.isEmpty() ? "/" : name;
  }
}
