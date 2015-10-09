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

package io.gearpump.examples.tap_pipeline

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.gearpump.cluster.UserConfig
import io.gearpump.cluster.client.ClientContext
import io.gearpump.cluster.main.{ArgumentsParser, CLIOption, ParseResult}
import io.gearpump.external.hbase.HBaseSink
import io.gearpump.streaming.StreamApplication
import io.gearpump.streaming.kafka.{KafkaSource, KafkaStorageFactory}
import io.gearpump.streaming.sink.DataSinkProcessor
import io.gearpump.streaming.source.DataSourceProcessor
import io.gearpump.tap.TapJsonConfig
import io.gearpump.util.Graph._
import io.gearpump.util.{AkkaApp, Graph, LogUtil}
import org.slf4j.Logger

object PipeLine extends AkkaApp with ArgumentsParser {
  private val LOG: Logger = LogUtil.getLogger(getClass)

  override val options: Array[(String, CLIOption[Any])] = Array(
    "hbase"-> CLIOption[String]("<hbase instance>", required = false, defaultValue = Some("hbase")),
    "kafka"-> CLIOption[String]("<kafka instance>", required = false, defaultValue = Some("kafka")),
    "table"-> CLIOption[String]("<hbase table>", required = false, defaultValue = Some("gp_tap_table")),
    "topic"-> CLIOption[String]("<kafka topic>", required = false, defaultValue = Some("gp_tap_topic"))
  )

  def application(config: ParseResult, system: ActorSystem): StreamApplication = {
    implicit val actorSystem = system

    val conf = ConfigFactory.load
    val services = conf.root.withOnlyKey("VCAP_SERVICES").render(ConfigRenderOptions.defaults().setJson(true))
    val tjc = new TapJsonConfig(services)
    val hbaseconfig = tjc.getHBase(config.getString("hbase"))
    //val kafkaconfig = tjc.getKafka(config.getString("hbase"))
    val kafkaconfig = Map(
      "zookeepers" -> "10.10.10.46:9092,10.10.10.164:9092,10.10.10.236:9092",
      "brokers" -> "10.10.10.46:2181,10.10.10.236:2181,10.10.10.164:2181/kafka"
    )
    val topic = config.getString("topic")
    val table = config.getString("table")
    val zookeepers = kafkaconfig.get("zookeepers").get
    val brokers = kafkaconfig.get("brokers").get
    val source = DataSourceProcessor(new KafkaSource(topic, zookeepers,new KafkaStorageFactory(zookeepers, brokers)), 1)
    val sink = DataSinkProcessor(new HBaseSink(table, hbaseconfig), 1)
    val app = StreamApplication("TAPPipeline", Graph(
      source ~> sink
    ), UserConfig.empty)
    app
  }

  override def main(akkaConf: Config, args: Array[String]): Unit = {
    val config = parse(args)
    val context = ClientContext(akkaConf)
    val appId = context.submit(application(config, context.system))
    context.close()
  }

}
