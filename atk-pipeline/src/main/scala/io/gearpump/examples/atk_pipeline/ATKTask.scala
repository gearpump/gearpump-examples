package io.gearpump.examples.atk_pipeline

import io.gearpump.Message
import io.gearpump.cluster.UserConfig
import io.gearpump.streaming.dsl.plan.OpTranslator.HandlerTask
import io.gearpump.streaming.task.{StartTime, TaskContext}

class ATKTask(taskContext: TaskContext, userConf: UserConfig) extends HandlerTask[Scoring](taskContext, userConf) {
  val TAR = "trustedanalytics.scoring-engine.archive-tar"

  override def onStart(startTime : StartTime) : Unit = {
    LOG.info("onStart")
    val tar = userConf.getString(TAR).get
    handler.load(tar)
  }

  override def onNext(msg : Message) : Unit = {
    LOG.info("onNext")
    val seq = msg.msg.asInstanceOf[Seq[Array[String]]]
    val score = handler.score(seq)
    score.foreach(result => {
      result.onComplete(seq => {
        seq.map(seq => {
          taskContext.output(new Message(seq))
        })
      })
    })
  }

}

object ATKTask {
  implicit val atkTask = classOf[ATKTask]
}
