import org.beangle.parent.Dependencies.*
import org.beangle.parent.Settings.*
import sbtassembly.AssemblyPlugin
import sbtassembly.AssemblyPlugin.autoImport.*
import sbtassembly.{MergeStrategy, PathList}

organization := "org.beangle.sas"
version := "0.14.0"

scmInfo := Some(
  ScmInfo(
    uri("https://github.com/beangle/sas"),
    "scm:git@github.com:beangle/sas.git"
  )
)

developers := List(
  Developer(
    id = "chaostone",
    name = "Tihua Duan",
    email = "duantihua@gmail.com",
    url = uri("http://github.com/duantihua")
  )
)

description := "The Beangle Simple Application Server (SAS)"
homepage := Some(uri("https://beangle.github.io/sas/index.html"))

val beangle_commons_ver = "6.3.7"
val apache_tomcat_ver = "11.0.26"
val io_undertow_ver = "2.4.4.Final"
val undertow_ee_ver = "2.0.3.Final"

val beangle_commons = "org.beangle.commons" % "beangle-commons" % beangle_commons_ver
val tomcat_juli = "org.apache.tomcat" % "tomcat-juli" % apache_tomcat_ver
val undertow_core = "io.undertow" % "undertow-core" % io_undertow_ver % "optional"
val undertow_servlet = "io.undertow.ee" % "undertow-servlet" % undertow_ee_ver % "optional"
val tomcat_embeded_core = ("org.apache.tomcat.embed" % "tomcat-embed-core" % apache_tomcat_ver % "optional").exclude("org.apache.tomcat", "tomcat-annotations-api")
val commonDeps = Seq(beangle_commons, scalatest)
val jcl_over_slf4j = "org.slf4j" % "jcl-over-slf4j" % "2.0.20"

lazy val root = (project in file("."))
  .settings(common,publish / skip := true)
  .aggregate(engine, juli)

lazy val engine = (project in file("engine"))
  .settings(
    name := "beangle-sas-engine",
    common,
    libraryDependencies ++= Seq(tomcat_embeded_core, undertow_core, undertow_servlet)
  )

lazy val juli = (project in file("juli"))
  .settings(
    name := "beangle-sas-juli",
    common,
    exportJars := false,
    libraryDependencies ++= Seq(slf4j, jcl_over_slf4j, logback_core, logback_classic, tomcat_juli),
    assemblyPackageScala / assembleArtifact := false,
    assemblyExcludedJars := {
      val cp = (assembly / fullClasspath).value
      cp filter { f => f.data.name.contains("scala") }
    },
    assemblyShadeRules := Seq(
      ShadeRule.zap("scala.**").inAll,
      ShadeRule.zap("org.apache.juli.logging.**").inAll,
      ShadeRule.zap("org.apache.juli.**Handler**").inAll,
      ShadeRule.zap("org.apache.juli.**Format**").inAll,
      ShadeRule.rename("org.apache.commons.logging.**" -> "org.apache.juli.logging.@1").inAll,
      ShadeRule.rename("org.slf4j.**" -> "org.beangle.sas.slf4j.@1").inAll,
      ShadeRule.rename("ch.qos.logback.**" -> "org.beangle.sas.logback.@1").inAll,
      ShadeRule.rename("logback.ContextSelector" -> "juli.logback.ContextSelector").inAll,
    ),
    assemblyMergeStrategy := {
      case PathList("META-INF", xs@_*) =>
        xs map (_.toLowerCase) match { //这里转成了小写，后面判断也使用小写
          case ("manifest.mf" :: Nil) | ("notice" :: Nil) | ("license" :: Nil) => MergeStrategy.discard
          case "maven" :: xs => MergeStrategy.discard
          // 容器日志 jar 只做日志桥接，随包生成的 META-INF/beangle/dependencies 会被
          // webapp 的 DependencyClassLoader 当成引擎清单读走，必须丢弃
          case "beangle" :: _ => MergeStrategy.discard
          case "services" :: "jakarta.servlet.servletcontainerinitializer" :: Nil => MergeStrategy.discard
          case "services" :: "org.slf4j.spi.slf4jserviceprovider" :: Nil => MergeStrategy.discard
          case "services" :: "org.apache.commons.logging.logfactory" :: Nil => MergeStrategy.discard
          case "services" :: "ch.qos.logback.classic.spi.configurator" :: Nil => MergeStrategy.discard
          case _ => MergeStrategy.first
        }
      case PathList("module-info.class") => MergeStrategy.discard
      case _ => MergeStrategy.first
    },
    assemblyJarName := "beangle-sas-juli-" + version.value + ".jar",
    Compile / packageBin := Def.uncached(assembly).value
  )
