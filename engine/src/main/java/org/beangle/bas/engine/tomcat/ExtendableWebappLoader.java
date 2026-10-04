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

import org.apache.catalina.Context;
import org.apache.catalina.loader.WebappLoader;

public class ExtendableWebappLoader extends WebappLoader {

  public static String ExtendedLibsName = "bas_extended_libs";

  private String libs;

  public void setLibs(String libs) {
    this.libs = libs;
  }

  public void setContext(Context context) {
    if (null != this.libs) {
      context.addParameter(ExtendedLibsName, libs);
    }
    super.setContext(context);
  }

}
