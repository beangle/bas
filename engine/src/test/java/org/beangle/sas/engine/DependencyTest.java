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

package org.beangle.sas.engine;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** {@link Dependency.LocalRepo} 的正式版/快照布局查找用例。 */
public class DependencyTest {

  public static void main(String[] args) throws Exception {
    testRelease();
    testSnapshotInMergedRepo();
    testSnapshotInDefaultLibrary();
    testSnapshotInFlatWebapps();
    System.out.println("DependencyTest passed");
  }

  private static void testRelease() throws IOException {
    File tmp = Files.createTempDirectory("dep-release").toFile();
    File repo = new File(tmp, "repo");
    File jar = new File(repo, "org/test/demo/1.0/demo-1.0.jar");
    touch(jar);
    Dependency.LocalRepo local = new Dependency.LocalRepo(repo.getAbsolutePath(),
      new File(tmp, "webapps").getAbsolutePath());
    assertEquals(jar.getAbsolutePath(), local.path(new Dependency.Artifact("org.test:demo:1.0")));
    Tools.delete(tmp);
  }

  /** jstart 显式 --local：快照与正式版同库，取时间戳最新的那个。 */
  private static void testSnapshotInMergedRepo() throws IOException {
    File tmp = Files.createTempDirectory("dep-merged").toFile();
    File vdir = new File(new File(tmp, "repo"), "org/test/demo/1.0-SNAPSHOT");
    touch(new File(vdir, "demo-1.0-20260101.010101-1.jar"));
    touch(new File(vdir, "demo-1.0-20260103.030303-3.jar"));
    touch(new File(vdir, "demo-1.0-20260102.020202-2.jar"));
    File newest = new File(vdir, "demo-1.0-20260103.030303-3.jar");
    Dependency.LocalRepo local = new Dependency.LocalRepo(new File(tmp, "repo").getAbsolutePath(),
      new File(tmp, "webapps").getAbsolutePath());
    assertEquals(newest.getAbsolutePath(), local.path(new Dependency.Artifact("org.test:demo:1.0-SNAPSHOT")));
    Tools.delete(tmp);
  }

  /** jstart 默认：快照落在 <repo>/../snapshots/g/a/version/。 */
  private static void testSnapshotInDefaultLibrary() throws IOException {
    File tmp = Files.createTempDirectory("dep-default").toFile();
    File vdir = new File(new File(tmp, "snapshots"), "org/test/demo/1.0-SNAPSHOT");
    File jar = new File(vdir, "demo-1.0-20260102.020202-2.jar");
    touch(jar);
    new File(tmp, "repo").mkdirs();
    Dependency.LocalRepo local = new Dependency.LocalRepo(new File(tmp, "repo").getAbsolutePath(),
      new File(tmp, "webapps").getAbsolutePath());
    assertEquals(jar.getAbsolutePath(), local.path(new Dependency.Artifact("org.test:demo:1.0-SNAPSHOT")));
    Tools.delete(tmp);
  }

  /** beangle-boot 平铺布局：<sas.home>/webapps/demo-1.0-SNAPSHOT.jar。 */
  private static void testSnapshotInFlatWebapps() throws IOException {
    File tmp = Files.createTempDirectory("dep-flat").toFile();
    File webapps = new File(tmp, "webapps");
    File jar = new File(webapps, "demo-1.0-SNAPSHOT.jar");
    touch(jar);
    new File(tmp, "repo").mkdirs();
    Dependency.LocalRepo local = new Dependency.LocalRepo(new File(tmp, "repo").getAbsolutePath(),
      webapps.getAbsolutePath());
    assertEquals(jar.getAbsolutePath(), local.path(new Dependency.Artifact("org.test:demo:1.0-SNAPSHOT")));
    Tools.delete(tmp);
  }

  private static void touch(File f) throws IOException {
    File parent = f.getParentFile();
    if (parent != null) parent.mkdirs();
    Files.write(f.toPath(), "x".getBytes(StandardCharsets.UTF_8));
  }

  private static void assertEquals(String expected, String result) {
    if (!expected.equals(result)) {
      throw new IllegalArgumentException("not equals:[" + expected + "] , result is [" + result + "]");
    }
  }
}
