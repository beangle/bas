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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.net.Socket;

public class Tools {
  public static boolean isLibEmpty(String path) {
    File file = new File(path);
    if (file.exists()) {
      try {
        ZipFile war = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = war.entries();
        boolean finded = false;
        while (entries.hasMoreElements() && !finded) {
          String entry = entries.nextElement().getName();
          finded = (entry.startsWith("WEB-INF/lib/") && entry.endsWith(".jar"));
        }
        return !finded;
      } catch (Exception e) {
        throw new RuntimeException("Cannot unzip war file located at " + path);
      }
    } else {
      throw new RuntimeException("Cannot find war file located at " + path);
    }
  }

  public static boolean delete(final File file) {
    if (null == file || !file.exists()) return false;
    Path path = file.toPath();
    try {
      Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
        @Override
        public FileVisitResult visitFile(Path file, @SuppressWarnings("unused") BasicFileAttributes attrs)
          throws IOException {
          Files.delete(file);
          return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException e)
          throws IOException {
          if (e == null) {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
          }
          throw e;
        }
      });
      return true;
    } catch (IOException e) {
      throw new RuntimeException("Failed to delete " + path, e);
    }
  }

  public static boolean isPortFree(int port) {
    try {
      new Socket("localhost", port).close();
      return false;
    } catch (IOException e) {
      return true;
    }
  }
}
