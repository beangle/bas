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

package org.beangle.bas.engine.undertow;

import io.undertow.Undertow;
import org.beangle.bas.engine.AbstractServer;

class UndertowServer extends AbstractServer {

  private Undertow undertow;

  public UndertowServer(Undertow undertow) {
    this.undertow = undertow;
  }

  @Override
  public void doStart() {
    this.undertow.start();
  }

  @Override
  public void doStop() {
    this.undertow.stop();
  }

}
