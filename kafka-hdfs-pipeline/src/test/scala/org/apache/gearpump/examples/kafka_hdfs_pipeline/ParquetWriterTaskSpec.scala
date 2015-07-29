package org.apache.gearpump.examples.kafka_hdfs_pipeline

import akka.actor.ActorSystem
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.streaming.MockUtil
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.mockito.Mockito._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, Matchers, PropSpec}


class ParquetWriterTaskSpec extends PropSpec with PropertyChecks with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("PipeLineSpec")
  val context = MockUtil.mockTaskContext

  val userConfig = UserConfig.empty.withString(ParquetWriterTask.PARQUET_OUTPUT_DIRECTORY, "/parquet")

  override def afterAll(): Unit = {
    system.shutdown()
  }

  property("ParquetWriterTask should initialize with local parquet file opened for writing") {
    val appName = "KafkaHdfsPipeLine"
    when(context.appName).thenReturn(appName)
    val fs = FileSystem.get(new YarnConfiguration)
    val homeDir = fs.getHomeDirectory.toUri.getPath
    val path1 = new Path(homeDir, "gearpump")  + "/parquet/" + appName + ".parquet"
    val parquetWriterTask = new ParquetWriterTask(context, userConfig)
    val path2 = parquetWriterTask.absolutePath.stripPrefix("file:")
    assert(path1.equals(path2))
  }
}
