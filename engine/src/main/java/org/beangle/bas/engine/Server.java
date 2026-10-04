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
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.logging.Logger;

public interface Server {
  void start();

  void shutdown();

  class Config {
    private static boolean isTempBase = false;
    public final String base;
    public final String contextPath;
    public final int port;
    public boolean devMode = false;
    /** 是否注册容器的默认 servlet（war 根下的静态文件与 welcome file）。静态资源交给前端代理时设为 false */
    public boolean defaultServletSupport = false;
    public int defaultSessionTimeout = 30;//minutes
    /** 容器后台处理间隔(秒)，驱动会话过期、静态资源缓存回收与 dev 热加载；不允许为 0 */
    public int backgroundProcessorDelay = 10;
    public Map<String, String> properties = new HashMap<String, String>();
    public String docBase;

    public Config(String base, String contextPath, int port) {
      this.base = base;
      this.contextPath = normalizePath(contextPath);
      this.port = port;
    }

    public static String normalizePath(String p) {
      if (p == null || p.equals("/")) return "";
      else {
        String path = p;
        if (!path.startsWith("/")) path = "/" + path;
        if (path.endsWith("/")) path = path.substring(0, path.length() - 1);
        path = path.replaceAll("//", "/");
        return path;
      }
    }

    /** 读取整数属性，未配置时返回 {@link OptionalInt#empty()} */
    public OptionalInt getInt(String propertyName) {
      var v = getProperty(propertyName);
      return v.map(s -> OptionalInt.of(toInt(propertyName, s))).orElseGet(OptionalInt::empty);
    }

    /** 读取布尔属性，未配置时返回 {@link Optional#empty()}，取值只接受 true/false（忽略大小写） */
    public Optional<Boolean> getBoolean(String propertyName) {
      return getProperty(propertyName).map(v -> {
        if (v.equalsIgnoreCase("true")) return Boolean.TRUE;
        if (v.equalsIgnoreCase("false")) return Boolean.FALSE;
        throw new IllegalArgumentException("Property [" + propertyName + "] expects true/false but was [" + v + "]");
      });
    }

    /** 解析整数，失败时报出属性名，避免只看到 "For input string: xxx" */
    private static int toInt(String propertyName, String value) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        throw new IllegalArgumentException("Property [" + propertyName + "] expects a number but was [" + value + "]", e);
      }
    }

    /**
     * 读取引擎属性，先查启动参数(--Dkey=value)，再回退到系统属性(-Dkey=value)
     */
    public Optional<String> getProperty(String propertyName) {
      String v = properties.get(propertyName);
      if (null == v || v.isEmpty()) v = System.getProperty(propertyName);
      if (null == v) return Optional.empty();
      v = v.trim();
      return v.isEmpty() ? Optional.empty() : Optional.of(v);
    }

    public String getDefaultDocBase() {
      if (contextPath.isEmpty() || contextPath.equals("/")) {
        return base + "/webapps/ROOT";
      } else {
        return base + "/webapps/" + contextPath.substring(1).replace('/', '#');
      }
    }

    public static boolean isNativeImage() {
      return System.getProperty("org.graalvm.nativeimage.imagecode") != null;
    }

    public static File initBase(String base) {
      var logger = Logger.getLogger(Server.class.toString());
      try {
        File baseDir = null;
        if (null == base) {
          baseDir = Files.createTempDirectory("bas").toFile();
          logger.info("create base dir: " + baseDir.getAbsolutePath());
          baseDir.deleteOnExit();
          isTempBase = true;
        } else {
          baseDir = new File(base);
          baseDir.mkdirs();
        }
        new File(baseDir, "webapps").mkdirs();
        new File(baseDir, "temp").mkdirs();
        return baseDir;
      } catch (IOException ex) {
        throw new RuntimeException("Unable to create baseDir. java.io.tmpdir is set to " + System.getProperty("java.io.tmpdir"), ex);
      }
    }

    public void guessDocBase() {
      // native 模式下无法通过 getResource("").getFile() 探测 IDE 路径
      if (isNativeImage()) {
        this.docBase = this.getDefaultDocBase();
        new File(this.docBase).mkdirs();
        return;
      }
      var loader = Thread.currentThread().getContextClassLoader();
      //是否处于IDE开发环境
      var resource = loader.getResource("");
      if (resource != null) {
        String targetClassPath = resource.getFile();
        int targetIdx = targetClassPath.indexOf("/target/");
        if (targetIdx > 0) {
          String projectWebapp = targetClassPath.substring(0, targetIdx) + "/src/main/webapp";
          if (new File(projectWebapp).exists()) {
            this.docBase = projectWebapp;
          }
        }
      }
      if (null == this.docBase) {
        this.docBase = this.getDefaultDocBase();
      }
      new File(this.docBase).mkdirs();
    }

    /**
     * 显式指定 docBase：引擎入口 main 已经算好布局并解压好 webapp 时使用，跳过
     * guessDocBase 的 IDE/默认路径探测。
     */
    public void setDocBase(String docBase) {
      if (null != docBase && !docBase.isEmpty()) {
        this.docBase = docBase;
        new File(docBase).mkdirs();
      } else {
        guessDocBase();
      }
    }

    public void cleanup() {
      if (null != docBase) {
        var dir = docBase;
        dir = dir.replace('\\', '/');
        if (docBase.startsWith(base) && !dir.contains("src/main/webapp")) {
          Tools.delete(new File(docBase));
        }
        if (isTempBase) {
          Tools.delete(new File(base));
        }
      }
    }

  }
}
