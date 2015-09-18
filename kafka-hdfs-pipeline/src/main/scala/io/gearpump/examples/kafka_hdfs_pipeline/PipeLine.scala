/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gearpump.examples.kafka_hdfs_pipeline

import akka.actor.ActorSystem
import com.julianpeeters.avro.annotations._
import io.gearpump.cluster.UserConfig
import io.gearpump.cluster.client.ClientContext
import io.gearpump.cluster.main.{ArgumentsParser, CLIOption, ParseResult}
import io.gearpump.partitioner.ShufflePartitioner
import io.gearpump.streaming.kafka.{KafkaSource, KafkaStorageFactory}
import io.gearpump.streaming.source.DataSourceProcessor
import io.gearpump.streaming.{Processor, StreamApplication}
import io.gearpump.util.Graph._
import io.gearpump.util.{AkkaApp, Graph, LogUtil}
import org.slf4j.Logger

case class SpaceShuttleMessage(id: String, on: String, body: String)

/**
 * This annotation will translate the case class into a Avro type class.
 * Fields must be vars in order to be compatible with the SpecificRecord API.
 * We will use this case class directly in ParquetWriterTask.
 */
@AvroRecord
case class SpaceShuttleRecord(var ts: Long, var anomaly: Double)

object PipeLine extends AkkaApp with ArgumentsParser {
  private val LOG: Logger = LogUtil.getLogger(getClass)

  override val options: Array[(String, CLIOption[Any])] = Array(
    "reader"-> CLIOption[Int]("<kafka data reader number>", required = false, defaultValue = Some(2)),
    "scorer"-> CLIOption[Int]("<scorer number>", required = false, defaultValue = Some(2)),
    "writer"-> CLIOption[Int]("<parquet file writer number>", required = false, defaultValue = Some(1)),
    "output"-> CLIOption[String]("<output path directory>", required = false, defaultValue = Some("/parquet")),
    "topic" -> CLIOption[String]("<topic>", required = false, defaultValue = Some("topic-105")),
    "brokers" -> CLIOption[String]("<brokers>", required = false, defaultValue = Some("10.10.10.46:9092,10.10.10.164:9092,10.10.10.236:9092")),
    "zookeepers" -> CLIOption[String]("<zookeepers>", required = false, defaultValue = Some("10.10.10.46:2181,10.10.10.236:2181,10.10.10.164:2181/kafka"))
  )

  def application(config: ParseResult, system: ActorSystem): StreamApplication = {
    implicit val actorSystem = system
    val readerNum = config.getInt("reader")
    val scorerNum = config.getInt("scorer")
    val writerNum = config.getInt("writer")
    val outputPath = config.getString("output")
    val topic = config.getString("topic")
    val brokers = config.getString("brokers")
    val zookeepers = config.getString("zookeepers")
    val appConfig = UserConfig.empty.withString(ParquetWriterTask.PARQUET_OUTPUT_DIRECTORY, outputPath)
    val offsetStorageFactory = new KafkaStorageFactory(zookeepers, brokers)

    val partitioner = new ShufflePartitioner()
    val source = new KafkaSource(topic, zookeepers, offsetStorageFactory)
    val reader = DataSourceProcessor(source, readerNum)
    val scorer = Processor[ScoringTask](scorerNum)
    val writer = Processor[ParquetWriterTask](writerNum)

    val dag = Graph(reader ~ partitioner ~> scorer ~ partitioner ~> writer)
    val app = StreamApplication("KafkaHdfsPipeLine", dag, appConfig)
    app
  }

  override def main(akkaConf: Config, args: Array[String]): Unit = {
    val config = parse(args)
    val context = ClientContext(akkaConf)
    val appId = context.submit(application(config, context.system))
    context.close()
  }
}
