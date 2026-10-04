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

/**
 * Abstract Server
 */
public abstract class AbstractServer implements Server {
  /**
   * whether it started
   */
  protected boolean started = false;

  private Object monitor = new Object();

  /**
   * start server
   */
  public final void start() {
    synchronized (this.monitor) {
      if (this.started) return;
      try {
        doStart();
        this.started = true;
      } catch (Exception ex) {
        try {
          doStop();
        } catch (Exception e) {
        }
        throw new RuntimeException("Unable to start " + getClass().getSimpleName(), ex);
      }
    }
  }

  /**
   * shutdown the server
   */
  public final void shutdown() {
    synchronized (this.monitor) {
      if (!this.started) return;
      this.started = false;
      try {
        doStop();
      } catch (Exception ex) {
        throw new RuntimeException("Unable to stop " + getClass().getSimpleName(), ex);
      }
    }
  }

  /**
   * actual start start process
   *
   * @throws Exception
   */
  public abstract void doStart() throws Exception;

  /**
   * concrete server shutdown process
   *
   * @throws Exception
   */
  public abstract void doStop() throws Exception;

}
