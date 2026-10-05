# Changelog

本项目所有重要变更均记录在此文件中。

## [0.14.0] - 未发布

### Added
- 新增 Jetty 12（ee10，Servlet 6）嵌入式引擎：`org.beangle.bas.engine.jetty.Bootstrap`（`basctl make jetty`）；
  错误页对齐 Tomcat 的 `SwallowErrorValve`（生产只回状态与消息，dev 保留完整栈），webapp 启动失败即抛
- 连接器调优三引擎对齐：Tomcat 新增 `connector.enableLookups` / `connector.disableUploadTimeout`；
  Undertow / Jetty 支持 `connector.acceptCount` / `connector.connectionTimeout` / `connector.keepAliveTimeout`
  （对应 server.xml `<http>` 的 `enable-lookups` / `disable-upload-timeout` / `accept-count` / `connection-timeout`）
- `CmdOptions` 支持 `--docBase=`，由外部 creator 指定已解压的 webapp 目录（未指定时仍走 `guessDocBase`）
- Undertow 新增 `customize(DeploymentInfo)` 扩展点，嵌入方可在部署前追加 servlet / listener / initializer

### Changed
- 项目与构件由 sas 更名为 bas：`org.beangle.sas` → `org.beangle.bas`，`beangle-sas-engine` / `beangle-sas-juli` → `beangle-bas-engine` / `beangle-bas-juli`，
  包名 `org.beangle.sas.*` → `org.beangle.bas.*`，系统属性 `sas.home` → `bas.home`、`beangle.sas.disableDependencyLoader` → `beangle.bas.disableDependencyLoader`
- Undertow / Jetty 会话对齐 Tomcat：只保留 Cookie 跟踪，URL 不再出现 `;jsessionid`；Undertow 额外显式下发 `HttpOnly`（其默认不带）
- 三个嵌入式引擎统一不解析 `web.xml` / `web-fragment.xml`（Jetty 原先会解析，且与 `defaultServletSupport` 一起依赖容器的
  `webdefault-ee10.xml`）：应用初始化只走 SCI（`beangle.xml`），默认 servlet 改由引擎显式注册；完整 web.xml 语义交给 `server.xml` 的多实例模式
- 嵌入式 webapp 类加载器不再暴露 `WEB-INF/classes` / `WEB-INF/lib` 作为资源根（Jetty 新增 `EmbeddedClassLoader`，与 Tomcat 对齐）：
  应用类与依赖统一由 `-cp` 的父加载器提供，`classpath*:` 资源（如 `beangle.xml`）不再被重复枚举与合并
- 引擎启动横幅换成纯 ASCII 的 bas 图形（与 `basctl banner` / `bin/bas.sh version` 同一份标志）：只在交互终端打印，
  标准输出被重定向时只留版本行
- `Dependency` 重构为 `Resolver` / `LocalRepo` / `Artifact`，本地仓库支持 `-Dbas.repo`（jstart 以 `--local` 透传），
  兼容平铺与 SNAPSHOT 仓库的多种布局，按修改时间取最新
- `engine` 与 `beangle-bas-juli` 不再生成 `META-INF/beangle/dependencies`：前者的容器依赖由 `basctl` 的 `engines.ini` 维护，
  后者带上该清单会被 webapp 的 `DependencyClassLoader` 误认作引擎依赖
- 移除 `beangle-boot` / `beangle-template` 依赖；Undertow 升级 2.4.4.Final（servlet 2.0.3.Final），jcl-over-slf4j 升级 2.0.20
- README 重写，明确控制面（`basctl`）与运行时（`jstart`）分离

### Removed
- 删除 `core` 模块，配置模型、部署生成器与模板改由 `basctl` 维护
- 删除 `server` 模块（发行包 zip）与 `netinstall.sh`，`bin/*.sh` 控制脚本改由 `basctl init` 铺设

## [0.13.16] - 2026-09-29

### Added
- 应用全部启动失败时退出进程，释放监听端口

### Changed
- `resolve` 支持指定实例，`restart` 解析失败即中止

### Removed
- 移除 `start.sh` 中残留的 `sas_restart` 变量

## [0.13.15] - 2026-09-27

### Changed
- 升级 beangle-commons 6.3.7、beangle-template 0.2.14、beangle-boot 0.1.30
- 移除安装脚本中不再需要的 scala-xml 依赖

## [0.13.14] - 2026-09-21

### Changed
- 升级 Scala 3.9.0

## [0.13.13] - 2026-09-18

### Added
- 新增引擎启动参数 `--Dkey=value`，可在命令行设置引擎参数（等价于 JVM `-Dkey=value`，同时存在时 `--D` 优先）
- 新增 `server.defaultServletSupport` 配置项，控制是否注册容器的默认 servlet
- 嵌入式模式可配置 Processor 池上限（`connector.processorCache`）与应用层读写缓冲（`connector.appReadBufSize`、`connector.appWriteBufSize`）

### Changed
- 引擎参数读取改用 `OptionalInt`/`Optional<Boolean>`/`Optional<String>`，未配置即 empty，不再用默认值重载区分“没配”与“配成默认值”
- `engine.backgroundProcessorDelay` 可配（默认 10 秒，dev 模式 5 秒），驱动会话过期、静态资源缓存回收与热加载
- `EnvProfile.isDevMode` 对齐 beangle-commons 的 `Environment` 语义，profile 统一使用 `beangle.config.profiles`
- 嵌入式模式默认不再注册容器默认 servlet：`/index.html`、`/` 等直接 404，静态资源交给前端代理或应用自身（webmvc 的 `/static/**` 不受影响），需要时用 `--Dserver.defaultServletSupport=true` 打开
- 会话只保留 Cookie 跟踪，不再出现 `;jsessionid` 形式的 URL 重写
- 会话 id 生成器不再在启动时预热 SecureRandom（实测冷启动 116~121ms -> 80~86ms），SecureRandom 算法跟随平台默认（Linux/macOS 为 NativePRNG），不再固定 SHA1PRNG，会话语义不变
- 嵌入式模式不再向 `StandardServer` 投递无人消费的 PERIODIC_EVENT
- Tomcat 升级到 11.0.26，beangle-commons 升级到 6.3.6，beangle-template 0.2.13，beangle-boot 0.1.29，构建升级 sbt-beangle-build 0.1.8

### Fixed
- 修复 `engine.backgroundProcessorDelay` 只能取 Tomcat 默认值、且设为 0 会导致会话永不过期的问题
- 未识别的命令行参数不再被静默忽略，现在给出警告；数值解析失败会报出参数名
- native-image 补充 `AbstractHttp11Protocol.isSSLEnabled` 反射注册

### Removed
- 移除 access log 支持：`enableAccessLog` 配置项、`LogbackValve` 渲染与 logback-access 依赖一并删除，旧 server.xml 中的该属性读取时忽略
- 移除两个 leak-prevention listener 及单应用下无意义的 ThreadLocal/RMI 泄漏检查（实测冷启动中位 207ms -> 195ms，温启动无差异）

## [0.13.12] - 2026-09-06

### Added
- 补充 GraalVM native-image 反射与资源注册配置（`reflect-config.json`、`resource-config.json`），覆盖 Tomcat 内部类与 Bas 引擎

### Changed
- Tomcat 升级到 11.0.25，Undertow 2.4.3.Final / undertow-servlet 2.0.2.Final，logback 1.6.3，freemarker 2.3.35
- beangle-commons 升级到 6.3.2，beangle-template 0.2.11，beangle-boot 0.1.29，jcl-over-slf4j 2.0.19
- 构建升级到 sbt 2.0.8、sbt-beangle-parent 0.16.2，新增 sbt-beangle-build 0.1.5
- native-image 下 `Server.Config.guessDocBase` 不再依赖 classpath 探测，直接使用默认 webapps 目录

### Fixed
- 修复 native-image 启动时解析 `mbeans-descriptors.xml` 失败：禁用 MBean 注册改在 `TomcatServerBuilder.build()` 入口调用 `Registry.disableRegistry()`；`org.apache.tomcat.util.modeler.disable` 等系统属性 Tomcat 并不读取，予以移除，JVM 嵌入式模式同样不再注册 MBean
- native-image 下跳过 `Desktops.openBrowser`，避免 AWT 初始化触发 JNI 致命错误
- `guessDocBase` 在 classpath 资源缺失（如 native-image）时不再抛出空指针异常

### Removed
- 移除 engine 内嵌的 `native-image-args.txt`

## [0.13.11] - 2026-08-07

### Added
- Tomcat Connector 默认启用虚拟线程（JDK 21+），并移除 `maxThreads` / `minSpareThreads` 配置
- 构建迁移到 sbt 2.0.x，复用 `sbt-beangle-parent` 0.16.1 公共设置与依赖管理
- `launch.sh` 参数顺序无关，JVM 选项（`-Xmx`、`-D`）可放在任意位置

### Changed
- Undertow 升级到 2.4.2.Final，servlet 迁移到 `io.undertow.ee`（undertow-servlet 2.0.1.Final）
- beangle-commons 升级到 6.2.2，beangle-template 0.2.8，beangle-boot 0.1.28
- Tomcat 升级到 11.0.24，slf4j 2.0.18，logback 1.6.1，logback-access 2.0.14
- Scala 升级到 3.3.8（scala-library 2.13.18）
- `logo` 渲染从 `Version` 重构为 `Logo`
- server 部署版仅对 Tomcat 11 生成 `useVirtualThreads`，兼容 Tomcat 10.x
- 嵌入式模式 classpath 补充 `jul-to-slf4j` 桥接，Tomcat JUL 日志可接入 SLF4J/Logback

### Fixed
- 修复 Tomcat 11 生成的默认 web.xml 命名空间错误，按版本渲染 Servlet 6.1 / 6.0 / 5.0
- 修复 Webapp `jspSupport` 配置未解析导致 JSP 无法启用
- 修复 Undertow 非根 contextPath 前缀路由失效、`direct-buffers` 误用 `setBufferSize`
- 修复 Undertow Bootstrap 误用 Tomcat logger
- 修复 `launch.sh` 的 `--path` 未传给 Bootstrap 导致解压目录与 docBase 不一致
- `Desktops.openBrowser` 增加异常防护，不再因打开浏览器失败而中断服务器启动
- `Firewall.apply` 改用 ProcessBuilder 并校验 `firewall-cmd` 退出码
- `start.sh` 使用 `$SERVER_BASE` 替代循环变量 `$dir`

### Removed
- Tomcat 侧 HTTP/2 与 HTTPS 支持（改由前端代理承担）
- Connector `compression*` 配置
- Vibed 引擎支持（`VibedMaker`）
- `juli.logback.configurationFile` 系统属性支持（`SLF4JConfigurator` 固定查找 `conf/logback-catalina.xml`）

## [v0.13.10] - 2026-04-12

### Added
- HTTP/2 支持
- gzip 压缩

### Changed
- 升级到 commons 6.1.0

## [v0.13.9] - 2026-02-06

### Changed
- 升级到 commons 6
- 新增路径规范化（normalize path）函数

## [v0.13.8] - 2025-12-08

### Added
- 支持 Undertow 空路径部署（empty path）

## [v0.13.7] - 2025-12-08

### Added
- 支持嵌入模式启动外部 war
- 支持 Undertow 打印 logo
- 启动时合适的清理（suitable cleanup）

### Fixed
- 修复 `restart.sh`

## [v0.13.6] - 2025-11-25

### Changed
- 升级 parent 0.15.1
- 移除更多 Tomcat jar

### Fixed
- 修复 catalina 日志重复打印

## [v0.13.5] - 2025-11-12

### Added
- 支持将其他日志系统桥接到 SLF4J
- 支持更多 Tomcat Connector 配置

### Changed
- 开发模式禁用 APR

## [v0.13.4] - 2025-09-26

### Added
- 增强 Undertow initializers
- Nginx 重定向 host 支持

### Fixed
- 修复 `pull server.xml` 404 错误

## [v0.13.3] - 2025-08-05

### Changed
- 升级 boot 0.1.18

## [v0.13.2] - 2025-08-05

### Added
- 支持从仓库下载 SNAPSHOT
- 支持解析最新 SNAPSHOT 版本
- 支持解析 `bas_remote_url` 占位

### Changed
- 升级 boot 0.1.17

## [v0.13.1] - 2025-07-30

### Added
- 支持 SNAPSHOT 扩展库（extended libs）

## [v0.13.0] - 2025-07-30

### Added
- 支持将扩展库（extended lib）部署到 webapp context
- 端口检测（CmdOptions）
- Websocket 支持

### Changed
- 适配 Tomcat 10 / 11
- 升级 parent 0.13.10，boot 0.1.6
- 简化 `EmbeddedClassLoader`

### Fixed
- 修复端口检测错误

## [v0.12.x]

### Added
- 支持 restart
- logback-classic 集成
- `DependencyClassLoader` 增加 `enableNotFoundClassResourceCache`

### Changed
- 升级到 commons 5.6.11、Scala 3.3.4、logback access 2.0.4
- 移除 tomcat-native 依赖，Java 11 装配

### Fixed
- 修复若干 bug
