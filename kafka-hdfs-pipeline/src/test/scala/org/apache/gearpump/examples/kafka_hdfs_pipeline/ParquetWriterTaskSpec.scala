package org.apache.gearpump.examples.kafka_hdfs_pipeline

import akka.actor.ActorSystem
import org.apache.avro.Schema
import org.apache.gearpump.Message
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.streaming.MockUtil
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.yarn.conf.YarnConfiguration
import org.apache.parquet.avro.{AvroParquetReader, AvroParquetWriter}
import org.apache.parquet.hadoop.ParquetReader
import org.apache.parquet.hadoop.api.ReadSupport
import org.mockito.Mockito
import org.mockito.Mockito._
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, Matchers, PropSpec}


class ParquetWriterTaskSpec extends PropSpec with PropertyChecks with Matchers with BeforeAndAfterAll {

  implicit var system: ActorSystem = ActorSystem("PipeLineSpec")
  val context = MockUtil.mockTaskContext
  val appName = "KafkaHdfsPipeLine"
  when(context.appName).thenReturn(appName)
  val fs = FileSystem.get(new YarnConfiguration)
  val homeDir = fs.getHomeDirectory.toUri.getPath
  val parquetDir = new Path(homeDir, "gearpump")  + "/parquet/"
  val parquetPath = parquetDir + appName + ".parquet"
  val parquetCrc = parquetDir + "." + appName + ".parquet.crc"
  val parquetWriter = Mockito.mock(classOf[AvroParquetWriter[SpaceShuttleRecord]])

  val anomaly = 0.252
  val now = System.currentTimeMillis

  val userConfig = UserConfig.empty.withString(ParquetWriterTask.PARQUET_OUTPUT_DIRECTORY, "/parquet")

  override def afterAll(): Unit = {
    List(parquetPath, parquetCrc, parquetDir).foreach(new java.io.File(_).delete)
    system.shutdown()
  }

  property("ParquetWriterTask should initialize with local parquet file opened for writing") {
    val parquetWriterTask = new ParquetWriterTask(context, userConfig)
    val path = parquetWriterTask.absolutePath.stripPrefix("file:")
    assert(parquetPath.equals(path))
    parquetWriterTask.onStop
  }

  property("ParquetWriterTask should write records to a parquet file") {
    val message = Message(SpaceShuttleRecord(now, anomaly), now)
    val parquetWriterTask = new ParquetWriterTask(context, userConfig)
    parquetWriterTask.parquetWriter = parquetWriter
    parquetWriterTask.onNext(message)
    verify(parquetWriterTask.parquetWriter).write(message.msg.asInstanceOf[SpaceShuttleRecord])
    parquetWriterTask.onStop
  }

  property("ParquetWriterTask should have verifiable written record") {
    val message = Message(SpaceShuttleRecord(now, anomaly), now)
    val parquetWriterTask = new ParquetWriterTask(context, userConfig)
    parquetWriterTask.onNext(message)
    parquetWriterTask.onStop
    val reader = new AvroParquetReader[SpaceShuttleRecord](new Path(parquetPath))
    val record = reader.read()
    assert(message.msg.asInstanceOf[SpaceShuttleRecord].anomaly == record.anomaly)
    assert(message.msg.asInstanceOf[SpaceShuttleRecord].ts == record.ts)
  }
}
