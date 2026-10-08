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
import java.io.InputStreamReader;
import java.io.LineNumberReader;
import java.net.URL;
import java.util.*;

public class Dependency {
  public static final String DependenciesFile = "META-INF/beangle/dependencies";

  public static class Resolver {
    /**
     * 将两组工件进行合并，相同名称的以第一个集合出现的为准。
     *
     * @param first
     * @param second
     * @return
     */
    public static List<Artifact> merge(List<Artifact> first, List<Artifact> second) {
      if (second.isEmpty()) {
        return first;
      } else if (first.isEmpty()) {
        return second;
      } else {
        var keys = new HashSet<String>();
        List<Artifact> results = new ArrayList<>();
        results.addAll(first);
        for (Artifact a : first) {
          var key = a.groupId + ":" + a.artifactId;
          keys.add(key);
        }
        for (Artifact a : second) {
          var key = a.groupId + ":" + a.artifactId;
          if (!keys.contains(key)) {
            keys.add(key);
            results.add(a);
          }
        }
        return results;
      }
    }

    public static List<Artifact> parse(String gavs) {
      List<Artifact> artifacts = new ArrayList<Artifact>();
      if (null == gavs || gavs.isBlank()) return artifacts;
      var newGavs = gavs.replace(';', ',');
      newGavs = newGavs.replaceAll("\n", ",");
      newGavs = newGavs.replaceAll("\r", "");
      newGavs = newGavs.replaceAll(",,", ",");
      var gavArray = newGavs.trim().split(",");
      for (String line : gavArray) {
        artifacts.add(new Artifact(line.trim()));
      }
      return artifacts;
    }

    public static List<Artifact> resolve(URL resource) {
      List<Artifact> artifacts = new ArrayList<Artifact>();
      if (null == resource) return Collections.emptyList();
      try {
        InputStreamReader reader = new InputStreamReader(resource.openStream());
        LineNumberReader lr = new LineNumberReader(reader);
        String line = null;
        do {
          line = lr.readLine();
          if (line != null && !line.isEmpty()) {
            artifacts.add(new Artifact(line));
          }
        } while (line != null);
        lr.close();
      } catch (Exception e) {
        e.printStackTrace();
      }
      return artifacts;
    }
  }

  public static class LocalRepo {

    public final String base;

    public final String snapshotBase;

    public LocalRepo(String base, String snapshotBase) {
      this.base = base;
      this.snapshotBase = snapshotBase;
    }

    /**
     * 查找工具的文件路径
     * 如果是SNAPSHOT版本，从本地仓库的版本目录与 snapshotBase（平铺目录，如 webapps）中找出最新的
     * 如果是常规版本，从base对应的maven本地仓库中查找
     *
     * @param artifact snapshot/normal
     * @return
     */
    public String path(Artifact artifact) {
      if (artifact.version.endsWith("SNAPSHOT")) {
        return findLatest(artifact);
      } else {
        return base + "/" + artifact.groupId.replace('.', '/') + "/" + artifact.artifactId + "/"
          + artifact.version + "/" + artifact.artifactId + "-" + artifact.version + "." + artifact.packaging;
      }
    }

    /**
     * 从本地仓库与平铺目录（snapshotBase）中，查找最新的工件对应的文件
     *
     * @param artifact
     * @return
     */
    private String findLatest(Artifact artifact) {
      var flat = new File(snapshotBase + "/" + artifact.artifactId + "-" + artifact.version + "." + artifact.packaging);
      // 时间戳文件名基于去掉 -SNAPSHOT 的版本号：demo-1.0-20260101.010101-1.jar
      var baseVersion = artifact.version.endsWith("-SNAPSHOT")
        ? artifact.version.substring(0, artifact.version.length() - "-SNAPSHOT".length())
        : artifact.version;
      var prefix = artifact.artifactId + "-" + baseVersion;
      var suffix = "." + artifact.packaging;
      List<File> candidates = new ArrayList<>();
      if (flat.isFile()) candidates.add(flat);
      // 仓库版本目录：release 与 SNAPSHOT 同库同布局
      addNewest(candidates, new File(base, dirPath(artifact)), prefix, suffix);
      File best = null;
      for (File f : candidates) {
        if (best == null || f.lastModified() > best.lastModified()) best = f;
      }
      return (best != null ? best : flat).getAbsolutePath();
    }

    /** 版本目录下按文件名排序取最新的匹配文件（maven 时间戳/版本号递增），后缀过滤排除元数据。 */
    private static void addNewest(List<File> candidates, File dir, String prefix, String suffix) {
      File[] files = dir.listFiles(f -> f.isFile() && f.getName().startsWith(prefix) && f.getName().endsWith(suffix));
      if (files == null || files.length == 0) return;
      Arrays.sort(files, Comparator.comparing(File::getName));
      candidates.add(files[files.length - 1]);
    }

    /** maven 仓库中的版本目录：g/a/version。 */
    private static String dirPath(Artifact artifact) {
      return artifact.groupId.replace('.', '/') + "/" + artifact.artifactId + "/" + artifact.version;
    }

  }

  public static class Artifact {

    public final String groupId;
    public final String artifactId;
    public final String version;
    public final String packaging;

    public Artifact(String gav) {
      String[] infos = gav.split(":");
      this.groupId = infos[0];
      this.artifactId = infos[1];
      this.version = infos[2];
      this.packaging = (infos.length > 3) ? infos[3] : "jar";
    }

    public Artifact(String groupId, String artifactId, String version, String packaging) {
      super();
      this.groupId = groupId;
      this.artifactId = artifactId;
      this.version = version;
      this.packaging = packaging;
    }

    @Override
    public String toString() {
      return this.groupId + ":" + this.artifactId + ":" + this.version + "." + this.packaging;
    }
  }
}
