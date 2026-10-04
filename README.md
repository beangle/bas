# Beangle Bas Server

简化和便捷 war 包发布、加强管理的定制应用服务器，基于 Tomcat 与 Undertow 构建。

## 特性

- **多实例管理**：一套安装目录下，通过配置文件管理多个 JVM/应用实例（Farm / Server），支持一键启停、状态查看
- **双引擎支持**：Tomcat 10 / 11（11 默认虚拟线程，JDK 21+）与 Undertow 2.4，按实例选择
- **嵌入模式**：`basctl run`（组件目录里是 `bas.sh run`）直接启动单个 war / Maven 坐标 / 远端 URL，参数顺序无关，无需手工配置
- **控制面 / 运行时分离**：`bin/*.sh` 只做薄封装，容器编排交给 [`basctl`](https://github.com/beangle/basctl)，依赖解析与启动交给 [`jstart`](https://github.com/beangle/jstart)
- **依赖自动解析**：通过本机 `jstart` 命令解析并下载 war 及其依赖，bas 只负责编排与生成
- **远程配置分发**：`bas.sh pull`（`basctl pull`）从控制端拉取 `server.xml`，`start.sh` 启动前自动检查远端配置
- **统一日志**：`juli` 模块将 Tomcat 日志桥接到 SLF4J / Logback，集中管理
- **防火墙配置生成**：由 basctl 按 `conf/server.xml` 生成 firewalld 端口规则
- **JNDI 资源与 Realm**：支持 Webapp 级 JNDI 资源引用与安全域配置

## 架构

```
beangle-bas
├── engine   # Tomcat / Undertow 嵌入式运行时
└── juli     # Tomcat juli → SLF4J/Logback 日志桥接（shaded 独立 jar）
```

## 快速开始

### 安装

先装好 [`basctl`](https://github.com/beangle/basctl)（控制面）与 `jstart`（构件解析与启动），
再用 `basctl init` 初始化目录。控制脚本（`bin/*.sh`）内嵌在 `basctl` 里，随其版本发布：

```bash
basctl init /opt/bas          # 写入 /opt/bas/bin/*.sh 并建 conf/
basctl init --force /opt/bas  # 升级 basctl 后刷新脚本
```

之后编辑 `conf/server.xml`（或用 `bin/bas.sh pull` 从控制端拉取）和 `bin/setenv.sh`，
再 `bin/start.sh <farm|server|all>`。

### 嵌入模式（单应用启动）

由 `basctl run` 完成（组件目录里是 `bin/bas.sh run`）：不读 `conf/server.xml`，把目标写成一份
单应用 launch spec，再交给 `jstart` 前台运行。参数顺序无关，JVM 选项（`-Xmx`、`-D`）可放在任意位置：

```bash
bin/bas.sh run /path/to/app.war [--port=8080] [--path=/app] [jvm_options]
bin/bas.sh run [jvm_options] group:artifact:version [--engine=undertow] [other_args]
bin/bas.sh run http://host.com/path/app.war [--port=8080] [other_args]
```

组件目录缺省 `/tmp/bas`，可用 `--base=` / `--instance=` 改变；工作目录由 `bas.sh run` 固定为
`$BAS_HOME`（也可显式传 `--workdir=`）。引擎与容器版本内置在 `basctl` 中，可用
`bas_engine_version` / `bas_scala_version` / `bas_commons_version` / `bas_slf4j_version` /
`bas_logback_version` / `bas_tomcat_version` / `bas_undertow_version` /
`bas_undertow_ee_version` 环境变量覆盖，详见 basctl 的 [docs/run.md](https://github.com/beangle/basctl/blob/develop/docs/run.md)。

`--dev=true` 开启开发模式（热加载、错误页），等价于 `-Dbeangle.config.profiles=dev`。该 profile 与 beangle-commons 的
`Environment` 共用同一个 key 与语义（逗号分隔、调试模式自动视为 dev、`-dev` 可关闭自动行为）。

#### 引擎参数

`--Dkey=value` 设置引擎参数，等价于 JVM 的 `-Dkey=value`（同时存在时 `--D` 优先）：

```bash
bin/bas.sh run /path/to/app.war --port=8080 --Dconnector.maxKeepAliveRequests=1000 --Dengine.backgroundProcessorDelay=30
```

| key | 默认 | 说明 |
| --- | --- | --- |
| `connector.maxConnections` | 10000 | 最大连接数 |
| `connector.acceptCount` | 1000 | 等待队列长度 |
| `connector.connectionTimeout` | 20000 | 建连/读超时（ms） |
| `connector.keepAliveTimeout` | 同 connectionTimeout | keep-alive 空闲超时（ms） |
| `connector.maxKeepAliveRequests` | 100 | 单个 keep-alive 连接的最大请求数。实测 10 万请求：默认 100 会重建 1000 次连接，提到 10000 后为 0 次，直连吞吐 +2~5%；前面挂 nginx（upstream keepalive）时实测无差异 |
| `connector.processorCache` | 200 | 空闲 Processor 池上限，`-1` 表示不限。实测并发 ≤1000 的稳定长连接负载下与 2000 无差异（池只在空闲数超过上限时丢弃） |
| `connector.appReadBufSize`、`connector.appWriteBufSize` | 8192 | 每连接应用层读写缓冲（转发到 Tomcat 的 `socket.appReadBufSize/appWriteBufSize`，须为正数）。实测 1KB 与 64KB 对 1MB 静态文件吞吐无差异（响应走 sendfile），主要影响请求体与非 sendfile 的动态输出 |
| `engine.backgroundProcessorDelay` | 10（dev 5） | 容器后台处理间隔（秒），驱动会话过期、静态资源缓存回收与热加载。0 会关闭这些功能，会被夹到 1；会话实际过期粒度 = 该值 × `processExpiresFrequency`(默认 6) |
| `server.defaultServletSupport` | false | 是否注册容器的默认 servlet（war 根下的静态文件与 welcome file）。默认关：`/index.html`、`/` 等直接 404，静态资源交给前端代理或应用自身（webmvc 的 `/static/**` 不受影响）；需要时用 `--Dserver.defaultServletSupport=true` 打开 |
| `buffer-size`、`io-thread`、`worker-threads`、`direct-buffers` | Undertow 默认 | 仅 `--engine=undertow` 生效 |

嵌入式模式不支持 JSP 与 access log；会话只保留 Cookie 跟踪，不会出现 `;jsessionid` 形式的 URL 重写。
会话仍由 `StandardManager` 管理，但会话 id 生成器不在启动时预热 SecureRandom（Tomcat 默认会预热，实测
25~35ms，低熵环境或旧 JDK 上可能到秒级），这份开销推迟到第一次真正创建会话时（一次性）；SecureRandom
算法交给平台默认（Linux/macOS 为 NativePRNG，其余平台由 JDK 选择），不再固定 Tomcat 的 SHA1PRNG。

### 多实例模式

1. 编辑 `conf/server.xml`，声明引擎、实例（farm/server）与应用（webapp）
2. 启动：`bin/start.sh farm_name`（或 `server_name`、`all`）
3. 停止：`bin/stop.sh all`

实例上的应用全部启动失败时（只部署一个就是它起不来，部署多个就是都没起来），进程会打印错误并退出以释放端口，
修好问题后直接 `bin/start.sh server_name` 即可；只要还剩一个应用可用就只报错，不影响同机其他应用。
嵌入式模式（`basctl run`）行为一致：Tomcat 起不来会以非 0 退出并释放端口，Undertow 在绑定端口前就会失败。

### 管理命令

`bin/*.sh` 是薄封装：解析、生成、启停等控制面动作全部委托本机 `basctl`，运行时交给 `jstart`。
两者默认取 `PATH` 上的同名命令，可用环境变量 `bas_basctl` / `bas_jstart` 指向本地构建产物。

```bash
bin/start.sh farm_name          # 生成 spec 并后台启动（basctl start），启动前按需刷新远端 server.xml
bin/stop.sh all                 # 停止实例（basctl stop）
bin/restart.sh all              # resolve 成功后 stop + start
bin/bas.sh run app.war          # 嵌入式启动单个 webapp（basctl run）
bin/bas.sh status               # 查看运行中的实例（basctl status）
bin/bas.sh version              # 显示版本与本机地址（basctl version）
bin/bas.sh resolve [farm_name|server_name|all]   # 只解析 webapp 依赖，不启动（basctl resolve）
bin/bas.sh pull                 # 从控制端拉取 server.xml（basctl pull）
```

需要预装 `basctl` 与 `jstart`（见各自项目）；缺失时启动/解析会明确报错。升级控制脚本 =
换用新版 `basctl` 后执行 `basctl init --force <dir>`。

## 配置（conf/server.xml）

配置文件通过 `<bas>` 根元素声明：元素与属性一律使用**小写连字符**命名（如
`<snapshot-repo>`、`max-heap-size`）。格式由
[bas-1.0.0.xsd](http://beangle.github.io/schema/bas-1.0.0.xsd) 定义（basctl 的
`resources/bas-1.0.0.xsd` 为源），可在 `conf/server.xml` 根元素上用
`xsi:noNamespaceSchemaLocation` 指向它以获得 IDE 补全与校验。主要组成：

| 元素 | 说明 |
| --- | --- |
| `repository` | 依赖本地/远程仓库（release），可选 `token` 访问受保护仓库 |
| `snapshot-repo` | SNAPSHOT 仓库，支持 `${bas_remote_url}` / `${bas_remote_token}` 占位 |
| `engines/engine` | 引擎定义（Tomcat 10/11 / Undertow），含版本、JSP 支持、listener、jar |
| `hosts/host` | 主机定义（name/ip） |
| `resources/resource` | JNDI 资源，供 webapp 引用 |
| `farms/farm` | 实例组：堆大小（`max-heap-size`）、HTTP Connector、server 列表 |
| `webapps/webapp` | 应用：uri、contextPath、`run-at`（部署目标）、libs、`resource-ref` |

`<proxy>` 节点（Nginx/HAProxy 反代配置）不在当前控制面（basctl）的迁移范围内，
会被忽略；反代请另行生成/维护。

`repository` / `snapshot-repo` 的 `remote` 与 `token` 都支持环境变量占位：`${bas_remote_url}`
解析为 `bas_remote_url`（截断到 `/api/` 之前），`token="${bas_remote_token}"` 解析为
`bas_remote_token`。两个变量互相独立——没有 `bas_remote_token` 时只把令牌置空（受保护仓库
会返回 401），不影响 `${bas_remote_url}` 的解析。

### jstart 集成

解析下载 war/jar 的工作交给本机 `jstart` 命令，bas 不再依赖 `beangle-boot` 的下载 API：

| 场景 | 使用的命令 |
| --- | --- |
| 正式版 war / 引擎构件 / 扩展 libs | `jstart fetch <gav> --local=<local> --remote=<remotes>`（release 仓库） |
| 开发版（SNAPSHOT）war / libs | `jstart fetch <gav> --local=<local> --snapshot-remote=<remotes>`（快照仓库；jstart 按上游 `latest` 头或 `maven-metadata.xml` 解析最新时间戳构建） |
| war 内依赖解析（原 `AppResolver`） | `jstart resolve <war> --local=<local> --remote=<remotes>` |
| 嵌入式模式（`basctl run`） | `jstart run <spec>`（spec 由 basctl 生成，容器准备走 `basctl make *-embed`） |

- 命令位置由环境变量 `bas_jstart` 指定，缺省取 `PATH` 上的 `jstart`；
- 需在部署主机上安装 `jstart`（见 beangle/jstart 项目）；缺失时解析会失败并提示
  `Cannot run jstart, install jstart or set bas_jstart to its path.`；
- 仓库读令牌按 jstart 的约定通过子进程环境变量 `micdn_token` 传递（对应
  `token="${bas_remote_token}"`），构件 GET 带 `Authorization: Bearer`；
- 镜像列表与 Maven Central 兜底由 jstart 决定，且**只作用于正式版**：bas 只透传
  `<repository remote="...">`，未配置时不传 `--remote`，由 jstart 使用内置默认
  （阿里云 → 华为云 → Central）。开发版走**独立**的 `--snapshot-remote=`（不套用这份
  兜底）：`<snapshot-repo>` 的 `remote` 原样透传为 `--snapshot-remote=`，未配置时再以
  `--offline` 调 jstart，彻底只用本地已有构件（不探测、不下载）；
- 开发版（SNAPSHOT）的别名解析也在 jstart 内完成：按 `--snapshot-remote` 顺序 HEAD 别名读 micdn 的
  `latest` 响应头，其次取版本目录的 `maven-metadata.xml`，把时间戳文件落到本地快照库
  （默认 `~/.m2/snapshots`）并复核 `.sha1`；本地已有同一构建时跳过下载，上游不可达时
  退回本地已有文件。HEAD 保持匿名（micdn 的 `<auth download-key>` 不限制 HEAD）。
  因此 bas 不再自己发 HTTP 请求，只是把快照仓库的 `--local`/`--snapshot-remote`/
  `micdn_token` 透传给 jstart。

### 引擎入口（creator）

war 的引擎入口（jstart `[engine] init` 协议：准备容器环境、写出最终启动 argv）由
[`basctl`](https://github.com/beangle/basctl) 提供（`basctl make tomcat-embed` /
`basctl make undertow-embed` / `basctl make tomcat-dist`）。`engine` 模块只保留
容器运行时类（`tomcat.Bootstrap` / `undertow.Bootstrap`、`DependencyClassLoader`、
`ExtendableWebappLoader`、`WebappFailFastListener` 等），不再内置 creator main。

## 构建

```bash
sbt compile    # 编译
sbt test       # 测试
sbt package    # 打包（engine、juli 的 jar；juli 走 assembly）
```

- sbt 2.0.x / Scala 3.3.x
- 嵌入式模式需要 JDK 21+（虚拟线程）；server 部署模式 Tomcat 10.x 可用 JDK 17+，Tomcat 11 需 JDK 21+
- 依赖管理基于 [sbt-beangle-parent](https://github.com/beangle/parent)

## License

GNU Lesser General Public License version 3 (LGPL-3.0)
