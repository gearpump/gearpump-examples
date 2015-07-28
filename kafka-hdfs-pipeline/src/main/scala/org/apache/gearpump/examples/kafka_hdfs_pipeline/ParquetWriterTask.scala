/*
* Licensed to the Apache Software Foundation (ASF) under one
* or more contributor license agreements. See the NOTICE file
* distributed with this work for additional information
* regarding copyright ownership. The ASF licenses this file
* to you under the Apache License, Version 2.0 (the
* "License"); you may not use this file except in compliance
* with the License. You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.gearpump.examples.kafka_hdfs_pipeline

import org.apache.gearpump.Message
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.examples.kafka_hdfs_pipeline.ParquetWriterTask._
import org.apache.gearpump.streaming.task.{StartTime, Task, TaskContext}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.parquet.avro.AvroParquetWriter
import org.apache.hadoop.yarn.conf.YarnConfiguration
import scala.util.{Failure, Success, Try}

class ParquetWriterTask(taskContext : TaskContext, config: UserConfig) extends Task(taskContext, config) {
  val outputFileName = taskContext.appName + ".parquet"
  val absolutePath = Option(getHdfs + config.getString(PARQUET_OUTPUT_DIRECTORY).getOrElse("/parquet") + "/" + outputFileName).map(deleteFile(_)).get
  val outputPath = new Path(absolutePath)
  val parquetWriter = new AvroParquetWriter[SpaceShuttleRecord](outputPath, SpaceShuttleRecord.SCHEMA$)
  def getYarnConf = new YarnConfiguration
  def getFs = FileSystem.get(getYarnConf)
  def getHdfs = new Path(getFs.getHomeDirectory, "gearpump")

  private def deleteFile(fileName: String): String = {
    val file = new Path(fileName)
    getFs.exists(file) match {
      case true =>
        getFs.delete(file,false)
      case false =>
    }
    fileName
  }

  override def onStart(startTime: StartTime): Unit = {
    LOG.info(s"ParquetWriter.onStart $absolutePath")
  }

  override def onNext(msg: Message): Unit = {
    Try({
      LOG.info("ParquetWriter")
      parquetWriter.write(msg.msg.asInstanceOf[SpaceShuttleRecord])
    }) match {
      case Success(ok) =>
      case Failure(throwable) =>
        LOG.error(s"failed ${throwable.getMessage}")
    }
  }

  override def onStop(): Unit = {
    LOG.info("ParquetWriter.onStop")
    parquetWriter.close()
  }
}

object ParquetWriterTask {
  val PARQUET_OUTPUT_DIRECTORY = "parquet.output.directory"
}
