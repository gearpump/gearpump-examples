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

package io.gearpump.examples.atk_pipeline

import akka.actor.ActorSystem
import com.typesafe.config.{ConfigRenderOptions, ConfigFactory}
import io.gearpump.cluster.UserConfig
import io.gearpump.cluster.client.ClientContext
import io.gearpump.cluster.main.{ArgumentsParser, CLIOption, ParseResult}
import io.gearpump.streaming.dsl.CollectionDataSource
import io.gearpump.streaming.dsl.plan.OpTranslator.{HandlerTask, SourceTask}
import io.gearpump.streaming.kafka.{KafkaSource, KafkaStorageFactory}
import io.gearpump.streaming.source.DataSource
import io.gearpump.streaming.{Processor, StreamApplication}
import io.gearpump.tap.TapJsonConfig
import io.gearpump.util.Graph._
import io.gearpump.util.{AkkaApp, Graph, LogUtil}
import org.slf4j.Logger


object PipeLine extends AkkaApp with ArgumentsParser {
  private val LOG: Logger = LogUtil.getLogger(getClass)

  override val options: Array[(String, CLIOption[Any])] = Array(
    "models"-> CLIOption[String]("<models found in hdfs>", required = false, defaultValue = Some("/user/gearpump/atk/kmeans.tar")),
    "randomforest"-> CLIOption[String]("<tar file location in hdfs>", required = false, defaultValue = Some("/user/gearpump/atk/randomforest.tar")),
    "hbase"-> CLIOption[String]("<hbase instance>", required = false, defaultValue = Some("hbase")),
    "kafka"-> CLIOption[String]("<kafka instance>", required = false, defaultValue = Some("kafka")),
    "zookeeper"-> CLIOption[String]("<zookeeper instance>", required = false, defaultValue = Some("zookeeper")),
    "table"-> CLIOption[String]("<hbase table>", required = false, defaultValue = Some("gp_tap_table")),
    "topic"-> CLIOption[String]("<kafka topic>", required = false, defaultValue = Some("gp_tap_topic"))
  )

  def application(config: ParseResult, system: ActorSystem): StreamApplication = {
    import ATKTask._
    import SourceTask._
    implicit val actorSystem = system
    val TAR = "trustedanalytics.scoring-engine.archive-tar"
    val appConfig = UserConfig.empty.withString(TAR, config.getString("tar"))
    val conf = ConfigFactory.load
    val services = conf.root.withOnlyKey("VCAP_SERVICES").render(ConfigRenderOptions.defaults().setJson(true))
    val tjc = new TapJsonConfig(services)
    val hbaseconfig = tjc.getHBase(config.getString("hbase"))
    val kafkaconfig = tjc.getKafkaConfig(config.getString("kafka"))
    val zookeeperconfig = tjc.getZookeeperConfig(config.getString("zookeeper"))
    val topic = config.getString("topic")
    val table = config.getString("table")
    val zookeepers = zookeeperconfig.get("zookeepers")
    val brokers = kafkaconfig.get("brokers")
    val offsetStorageFactory = new KafkaStorageFactory(zookeepers, brokers)
    val source = new KafkaSource(topic, zookeepers, offsetStorageFactory)
    val kafka = Processor[HandlerTask,DataSource](source, 1, "KafkaSource", UserConfig.empty)
    val kmeans = Processor[HandlerTask,Scoring](new KMeans, 1, "ATK", appConfig)
    val app = StreamApplication("ATKPipeline", Graph(
      kafka ~> kmeans
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

