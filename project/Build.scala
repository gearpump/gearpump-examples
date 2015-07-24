import com.typesafe.sbt.SbtPgp.autoImport._
import de.johoop.jacoco4sbt.JacocoPlugin.jacoco
import sbt.Keys._
import sbt._
import sbtassembly.Plugin.AssemblyKeys._
import sbtassembly.Plugin._
import xerial.sbt.Pack._
import xerial.sbt.Sonatype._

import scala.collection.immutable.Map.WithDefault

object Build extends sbt.Build {

  class DefaultValueMap[+B](value : B) extends WithDefault[String, B](null, (key) => value) {
    override def get(key: String) = Some(value)
  }

  /**
   * deploy can recognize the path
   */
  val travis_deploy = taskKey[Unit]("use this after sbt assembly packArchive, it will rename the package so that travis deploy can find the package.")
  
  val clouderaVersion = "2.6.0-cdh5.4.2"
  val clouderaHBaseVersion = "1.0.0-cdh5.4.2"
  val gearpumpVersion = "0.4.1-SNAPSHOT"
  val hadoopVersion = "2.6.0"
  val kafkaVersion = "0.8.2.1"
  val sprayVersion = "1.3.2"
  val parquetVersion = "1.7.0"
  
  val scalaVersionMajor = "scala-2.11"
  val scalaVersionNumber = "2.11.5"
  val scalaTestVersion = "2.2.0"
  val scalaCheckVersion = "1.11.3"
  val mockitoVersion = "1.10.17"

  val commonSettings = Defaults.defaultSettings ++ Seq(jacoco.settings:_*) ++ sonatypeSettings  ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++
    Seq(
        resolvers ++= Seq(
          "patriknw at bintray" at "http://dl.bintray.com/patriknw/maven",
          "maven-repo" at "http://repo.maven.apache.org/maven2",
          "maven1-repo" at "http://repo1.maven.org/maven2",
          "maven2-repo" at "http://mvnrepository.com/artifact",
          "sonatype" at "https://oss.sonatype.org/content/repositories/releases",
          "bintray/non" at "http://dl.bintray.com/non/maven",
          "cloudera" at "https://repository.cloudera.com/artifactory/cloudera-repos",
          "clockfly" at "http://dl.bintray.com/clockfly/maven"
        ),
        addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full)
    ) ++
    Seq(
      addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0-M5" cross CrossVersion.full),
      scalaVersion := scalaVersionNumber,
      crossScalaVersions := Seq("2.10.5"),
      organization := "com.github.gearpump",
      useGpg := false,
      pgpSecretRing := file("./secring.asc"),
      pgpPublicRing := file("./pubring.asc"),
      scalacOptions ++= Seq("-Yclosure-elim","-Yinline"),
      publishMavenStyle := true,

      pgpPassphrase := Option(System.getenv().get("PASSPHRASE")).map(_.toArray),
      credentials += Credentials(
                   "Sonatype Nexus Repository Manager", 
                   "oss.sonatype.org", 
                   System.getenv().get("SONATYPE_USERNAME"), 
                   System.getenv().get("SONATYPE_PASSWORD")),
      
      pomIncludeRepository := { _ => false },

      publishTo := {
        val nexus = "https://oss.sonatype.org/"
        if (isSnapshot.value)
          Some("snapshots" at nexus + "content/repositories/snapshots")
        else
          Some("releases"  at nexus + "service/local/staging/deploy/maven2")
      },

      publishArtifact in Test := true,

      pomExtra := {
      <url>https://github.com/gearpump/gearpump-examples</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
        </license>
      </licenses>
      <scm>
        <connection>scm:git:github.com/gearpump/gearpump-examples</connection>
        <developerConnection>scm:git:git@github.com:-gearpump</developerConnection>
        <url>github.com/gearpump</url>
      </scm>
      <developers>
        <developer>
          <id>gearpump</id>
          <name>Gearpump Team</name>
          <url>https://github.com/gearpump/teams/gearpump</url>
        </developer>
      </developers>
    }
  )

  val hadoopDependency = Seq(
    ("org.apache.hadoop" % "hadoop-common" % clouderaVersion).
      exclude("org.fusesource.leveldbjni", "leveldbjni-all")
      exclude("org.mortbay.jetty", "jetty-util")
      exclude("org.mortbay.jetty", "jetty")
      exclude("org.apache.htrace", "htrace-core")
      exclude("tomcat", "jasper-runtime")
      exclude("commons-beanutils", "commons-beanutils-core")
      exclude("commons-beanutils", "commons-beanutils")
  )

  val myAssemblySettings = assemblySettings ++ Seq(
    test in assembly := {},
    assemblyOption in assembly ~= { _.copy(includeScala = true) }
  )

  lazy val root = Project(
    id = "gearpump",
    base = file("."),
    settings = commonSettings ++
      Seq(
        travis_deploy := {
          val packagePath = s"output/target/gearpump-pack-${version.value}.tar.gz"
          val target = s"target/binary.gearpump.tar.gz"
          println(s"[Travis-Deploy] Move file $packagePath to $target")
          new File(packagePath).renameTo(new File(target))
        }
      )
  ).aggregate(kafka_hdfs_pipeline, kafka_hbase_pipeline)

  lazy val kafka_hdfs_pipeline = Project(
    id = "gearpump-kafka-hdfs-pipeline",
    base = file("kafka-hdfs-pipeline"),
    settings = commonSettings ++ myAssemblySettings ++
      Seq(
        mergeStrategy in assembly := {
          case PathList("META-INF", "maven","org.slf4j","slf4j-api", ps) if ps.startsWith("pom") => MergeStrategy.discard
          case x =>
            val oldStrategy = (mergeStrategy in assembly).value
            oldStrategy(x)
        },
        libraryDependencies ++= Seq(
          "io.spray" %%  "spray-can" % sprayVersion,
          "com.github.intel-hadoop" %% "gearpump-core" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-daemon" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-streaming" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-external-kafka" % gearpumpVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2",
          "com.julianpeeters" % "avro-scala-macro-annotations_2.11" % "0.9.0",
          "org.apache.parquet" % "parquet-avro" % parquetVersion
            exclude("org.apache.htrace", "htrace-core"),
          "org.apache.hadoop" % "hadoop-hdfs" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("org.mortbay.jetty", "jetty-util")
            exclude("org.mortbay.jetty", "jetty")
            exclude("org.apache.htrace", "htrace-core")
            exclude("tomcat", "jasper-runtime"),
          "org.apache.hadoop" % "hadoop-yarn-api" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("com.google.guava", "guava")
            exclude("com.google.protobuf", "protobuf-java")
            exclude("commons-lang", "commons-lang")
            exclude("org.apache.htrace", "htrace-core")
            exclude("commons-logging", "commons-logging")
            exclude("org.apache.hadoop", "hadoop-annotations"),
          "org.apache.hadoop" % "hadoop-yarn-client" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("com.google.guava", "guava")
            exclude("com.sun.jersey", "jersey-client")
            exclude("commons-cli", "commons-cli")
            exclude("commons-lang", "commons-lang")
            exclude("commons-logging", "commons-logging")
            exclude("org.apache.htrace", "htrace-core")
            exclude("log4j", "log4j")
            exclude("org.apache.hadoop", "hadoop-annotations")
            exclude("org.mortbay.jetty", "jetty-util")
            exclude("org.apache.hadoop", "hadoop-yarn-api")
            exclude("org.apache.hadoop", "hadoop-yarn-common"),
          "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
          "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
          "org.mockito" % "mockito-core" % mockitoVersion % "test"
        ) ++ hadoopDependency,
        mainClass in (Compile, packageBin) := Some("org.apache.gearpump.examples.kafka_hdfs_pipeline.PipeLine"),
        target in assembly := baseDirectory.value.getParentFile / "target" / scalaVersionMajor
      )
  )
  
  lazy val kafka_hbase_pipeline = Project(
    id = "gearpump-kafka-hbase-pipeline",
    base = file("kafka-hbase-pipeline"),
    settings = commonSettings ++ myAssemblySettings ++
      Seq(
        mergeStrategy in assembly := {
          case PathList("META-INF", "maven","org.slf4j","slf4j-api", ps) if ps.startsWith("pom") => MergeStrategy.discard
          case x =>
            val oldStrategy = (mergeStrategy in assembly).value
            oldStrategy(x)
        },
        libraryDependencies ++= Seq(
          "io.spray" %%  "spray-can" % sprayVersion,
          "com.github.intel-hadoop" %% "gearpump-core" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-daemon" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-streaming" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-experiments-dsl" % gearpumpVersion % "provided"
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.github.intel-hadoop" %% "gearpump-external-kafka" % gearpumpVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2",
          "com.github.intel-hadoop" %% "gearpump-external-hbase" % gearpumpVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all"),
          "com.julianpeeters" % "avro-scala-macro-annotations_2.11" % "0.9.0",
          "org.apache.hadoop" % "hadoop-hdfs" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("org.mortbay.jetty", "jetty-util")
            exclude("org.mortbay.jetty", "jetty")
            exclude("org.apache.htrace", "htrace-core")
            exclude("tomcat", "jasper-runtime"),
          "org.apache.hadoop" % "hadoop-yarn-api" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("com.google.guava", "guava")
            exclude("com.google.protobuf", "protobuf-java")
            exclude("commons-lang", "commons-lang")
            exclude("org.apache.htrace", "htrace-core")
            exclude("commons-logging", "commons-logging")
            exclude("org.apache.hadoop", "hadoop-annotations"),
          "org.apache.hadoop" % "hadoop-yarn-client" % clouderaVersion
            exclude("org.fusesource.leveldbjni", "leveldbjni-all")
            exclude("com.google.guava", "guava")
            exclude("com.sun.jersey", "jersey-client")
            exclude("commons-cli", "commons-cli")
            exclude("commons-lang", "commons-lang")
            exclude("commons-logging", "commons-logging")
            exclude("org.apache.htrace", "htrace-core")
            exclude("log4j", "log4j")
            exclude("org.apache.hadoop", "hadoop-annotations")
            exclude("org.mortbay.jetty", "jetty-util")
            exclude("org.apache.hadoop", "hadoop-yarn-api")
            exclude("org.apache.hadoop", "hadoop-yarn-common"),
          "org.scalatest" %% "scalatest" % scalaTestVersion % "test",
          "org.scalacheck" %% "scalacheck" % scalaCheckVersion % "test",
          "org.mockito" % "mockito-core" % mockitoVersion % "test"
        ) ++ hadoopDependency,
        mainClass in (Compile, packageBin) := Some("org.apache.gearpump.examples.kafka_hbase_pipeline.PipeLine"),
        target in assembly := baseDirectory.value.getParentFile / "target" / scalaVersionMajor
      )
  )

}
