# beangle-bas-juli 模块备忘

> 一句话定位：`juli` 模块产出 `beangle-bas-juli-<version>.jar`，一个**整体改名（shade）的自包含 fat jar**，
> 为 Bas 托管的 Tomcat 进程提供「JULI → SLF4J → Logback」日志链路，且与部署应用自带的 slf4j/logback 完全隔离。

## 1. 背景与作用

- Tomcat 自带的 `org.apache.juli.logging.LogFactory` 默认落到 `java.util.logging`，Bas 希望容器日志统一走 Logback
  （由 `conf/logback-catalina.xml` 配置）。
- 同一个 JVM 里，**部署的应用通常自带普通版 `org.slf4j` + `ch.qos.logback`**。如果容器日志也用同一套，
  两边的配置、初始化时机、版本会互相干扰。
- 方案：把 slf4j、logback、jcl-over-slf4j、tomcat-juli 等依赖在**打包时整体改名**塞进一个 jar，
  进程内同时存在两套互不相干的 logging 栈：

  | 使用方 | logging 栈 |
  |---|---|
  | 托管的 Tomcat 容器（bootstrap 启动） | `org.beangle.bas.slf4j` / `org.beangle.bas.logback`（jar 内自带） |
  | 部署的应用（webapp / 引擎嵌入模式） | 普通 `org.slf4j` / `ch.qos.logback`（应用自己的依赖） |

## 2. 构建机制

配置见 `build.sbt` 的 `lazy val juli` 段。

- **发布产物即 fat jar**：`Compile / packageBin := Def.uncached(assembly).value`，
  `assemblyJarName := "beangle-bas-juli-" + version + ".jar"`。`publishLocal`/`publishM2` 出来的就是它。
- 编译依赖（打包时全部内嵌，随后 shade）：`slf4j`、`jcl-over-slf4j`、`logback-core`、`logback-classic`、`tomcat-juli`。
- 模块自有源码只有一个 Java 类：`SLF4JConfigurator`。

### 2.1 Shade 规则

| 规则 | 作用 |
|---|---|
| `rename org.slf4j.** → org.beangle.bas.slf4j.@1` | slf4j API 整体改名 |
| `rename ch.qos.logback.** → org.beangle.bas.logback.@1` | logback 整体改名 |
| `rename org.apache.commons.logging.** → org.apache.juli.logging.@1` | jcl-over-slf4j "变身"为 JULI 的 LogFactory/Log |
| `zap org.apache.juli.logging.**` | 剔除 tomcat-juli 原生的 LogFactory（让上面的 rename 产物接管） |
| `zap org.apache.juli.**Handler**`、`**Format**` | 去掉 JUL 默认的 handler/format，日志统一进 SLF4J |
| `zap scala.**` | 防御性剔除（本模块无 Scala 代码） |
| `rename logback.ContextSelector → juli.logback.ContextSelector` | 避免与应用侧 logback 的 ContextSelector 冲突 |

Merge 策略（`assemblyMergeStrategy`）：丢弃 logback/slf4j/commons-logging 的**原始** `META-INF/services` 文件、
`module-info.class`、manifest/maven/license 等；其余取 first。

### 2.2 服务文件必须预先用 shade 后的名字写好

**资源文件不会被 rename**，所以 `juli/src/main/resources` 里的服务文件与配置是直接按目标名写的：

```
META-INF/services/org.beangle.bas.slf4j.spi.SLF4JServiceProvider
    → org.beangle.bas.logback.classic.spi.LogbackServiceProvider
META-INF/services/org.beangle.bas.logback.classic.spi.Configurator
    → org.beangle.bas.tomcat.juli.SLF4JConfigurator
META-INF/services/org.apache.juli.logging.LogFactory
    → org.apache.juli.logging.impl.SLF4JLogFactory
```

同样，内置默认配置 `logback-catalina.xml` 里的 appender/statusListener 类名写的也是
`org.beangle.bas.logback.core.*`（shade 后的名字）。

### 2.3 对 Java 版本敏感

shade 由 jarjar/ASM 完成，**ASM 版本决定能读取的 class 文件 major 版本**。
parent 中 `javacOptions --release` 提升后，必须同步确认 sbt-assembly 足够新。

## 3. 运行时机制

### 3.1 启动链（Tomcat 托管模式）

托管启动由 `basctl start` 生成 jstart spec、`jstart` 调用 `basctl make tomcat-dist`
准备引擎目录并写出最终 catalina 启动命令。关键点：

- `beangle-bas-juli` 是 tomcat 引擎的默认依赖（basctl `applyEngineDefault` 补上，见
  `bas/tomcatmaker.d`），随 `[engine]` 段解析到本地；
- creator 把它放在 **Catalina 系统类路径**（`-cp`，与 `bin/bootstrap.jar` 同级），而不是
  `lib/`：Catalina 的 `Bootstrap` 在静态初始化里就要用 `org.apache.juli.logging.LogFactory`；
- 同时删除发行包自带的 `bin/tomcat-juli.jar`（creator 检测到引擎 classpath 含 juli 时删除），
  由本 jar 顶替。因此容器代码拿到的 `org.apache.juli.logging.LogFactory` 是 fat jar 内的
  `SLF4JLogFactory`（而不是原生实现）；
- juli fat jar **不能**带 `META-INF/beangle/dependencies`：它自带后会被 webapp 的
  `DependencyClassLoader` 当成引擎清单读走，把 juli 自身的依赖误当应用依赖解析。
  `build.sbt` 的 assembly merge 规则已显式 `discard` 该条目；
- `-Dbas.home` 是 `SLF4JConfigurator` 定位配置文件的依据。

### 3.2 日志链路

```
容器 commons-logging 调用
  → org.apache.juli.logging.LogFactory/SLF4JLogFactory   (jcl-over-slf4j shade 而来)
  → org.beangle.bas.slf4j.LoggerFactory                   (shaded slf4j)
  → ServiceLoader: org.beangle.bas.slf4j.spi.SLF4JServiceProvider
  → org.beangle.bas.logback.classic.spi.LogbackServiceProvider   (shaded logback)
  → ClassicEnvUtil.loadFromServiceLoader(Configurator)
  → SLF4JConfigurator.configure()
       1) 读取 $bas.home/conf/logback-catalina.xml（存在则用）
       2) 否则用 jar 内置默认（console 输出，root=INFO）
       3) 返回 DO_NOT_INVOKE_NEXT_IF_ANY，禁止其他内置 Configurator
```

### 3.3 隔离性（两个方向都成立）

- **应用侧有普通 logback**：两边包名不同，互不影响（ems portal 等应用正常用 `ch.qos.logback`）。
- **运行时没有外部 logback**：juli 自带 shaded logback，独立可运行（前提是 shade 完整，见 §5 自检清单）。

### 3.4 哪些模式不用 juli

引擎嵌入模式（`basctl run` / ems native，`beangle-bas-engine` + 普通 slf4j + 真 logback jar）
**不加载 juli jar**，直接使用普通 logging 栈。juli 只服务 `basctl start` 托管的 Tomcat 进程。

## 4. 用法

- **获取**：与其它引擎构件一样由 `basctl`/`jstart` 解析，在 spec 的 `[engine]` 段以
  `org.beangle.bas:beangle-bas-juli:<version>` 出现，版本与发布版本一致。
- **自定义容器日志**：编辑 `$BAS_HOME/conf/logback-catalina.xml`。
  ⚠️ **appender、listener 等类名必须写 shade 后的 `org.beangle.bas.logback.*`**，写 `ch.qos.logback.*` 会 ClassNotFound。
  内置默认模板：`juli/src/main/resources/logback-catalina.xml`。
- **升级**：发布新版本后同步 `<bas version>`（basctl 据此解析引擎与 juli 构件）。
- **关于外置 logback**：托管模式不依赖外置 logback；引擎模式需要。两者互不替代。

## 5. 发版自检清单

```bash
# 1) 构建日志无 "Shading is therefore impossible"

# 2) 无未 shade 残留（应输出 0）
unzip -p beangle-bas-juli-<ver>.jar org/beangle/bas/tomcat/juli/SLF4JConfigurator.class \
  | strings | grep -c ch/qos

# 3) 三个服务文件齐全且指向 shade 名
unzip -l beangle-bas-juli-<ver>.jar | grep META-INF/services

# 4) 纯 fat jar 冒烟（classpath 只有该 jar，能初始化 Logger 并输出日志）
```

## 6. 关键文件索引

| 文件 | 内容 |
|---|---|
| `build.sbt`（`lazy val juli` 段） | assembly、shade 规则、merge 策略 |
| `juli/src/main/java/org/beangle/bas/tomcat/juli/SLF4JConfigurator.java` | 唯一源码类，配置加载入口 |
| `juli/src/main/resources/logback-catalina.xml` | 内置默认容器日志配置（shade 后类名） |
| `juli/src/main/resources/META-INF/services/*` | 预先写好 shade 名的服务文件 |
| `build.sbt`（`juli` 段 merge 规则） | 丢弃 `META-INF/beangle/dependencies` |
| `basctl/src/bas/enginecreator.d` | juli 上系统 classpath、删 `bin/tomcat-juli.jar` |
| `basctl/src/bas/tomcatmaker.d` | `applyEngineDefault` 为 tomcat 引擎补 `beangle-bas-juli` |
