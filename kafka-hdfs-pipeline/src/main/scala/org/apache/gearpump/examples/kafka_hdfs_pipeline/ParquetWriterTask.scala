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
  val absolutePath = Option(config.getString(PARQUET_OUTPUT_DIRECTORY).get + "/" + outputFileName).map(deleteFile(_)).get
  val outputPath = new Path(absolutePath)
  val parquetWriter = new AvroParquetWriter[SpaceShuttleRecord](outputPath, SpaceShuttleRecord.SCHEMA$)
  def getYarnConf = new YarnConfiguration
  def getFs = FileSystem.get(getYarnConf)
  def getHdfs = new Path(getFs.getHomeDirectory, "/user/gearpump")
  var count = 0

  private def deleteFile(fileName: String): String = {
    val file = new java.io.File(fileName.stripPrefix("file://"))
    file.exists match {
      case true =>
        LOG.info(s"deleting $fileName")
        file.delete match {
          case true =>
          case false =>
            LOG.info("could not delete $name")
        }
      case false =>
        LOG.info(s"$fileName does not exist")
    }
    fileName
  }

  override def onStart(startTime: StartTime): Unit = {
    LOG.info("ParquetWriter.onStart")
    deleteFile(config.getString(PARQUET_OUTPUT_DIRECTORY).get + "/" + "." + outputFileName + ".crc")
  }

  override def onNext(msg: Message): Unit = {
    Try({
      LOG.info("ParquetWriter")
      parquetWriter.write(msg.msg.asInstanceOf[SpaceShuttleRecord])
      if(count % 50 == 0) {
        getFs.copyFromLocalFile(false, true, new Path(config.getString(PARQUET_OUTPUT_DIRECTORY).get, outputFileName), getHdfs)
      }
      count = count + 1
    }) match {
      case Success(ok) =>
      case Failure(throwable) =>
        LOG.error(s"failed ${throwable.getMessage}")
    }
  }

  override def onStop(): Unit ={
    LOG.info("ParquetWriter.onStop")
    parquetWriter.close()
    getFs.copyFromLocalFile(false, true, new Path(config.getString(PARQUET_OUTPUT_DIRECTORY).get, outputFileName), getHdfs)
  }
}

object ParquetWriterTask {
  val PARQUET_OUTPUT_DIRECTORY = "parquet.output.directory"
}
