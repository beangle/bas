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

import org.beangle.bas.engine.AbstractServer;
import org.eclipse.jetty.server.Server;

public class JettyServer extends AbstractServer {
  private final Server server;

  public JettyServer(Server server) {
    this.server = server;
  }

  @Override
  public void doStart() throws Exception {
    this.server.start();
  }

  @Override
  public void doStop() throws Exception {
    this.server.stop();
    this.server.destroy();
  }
}
