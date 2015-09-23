package io.gearpump.examples.atk_pipeline

import scala.concurrent.Future

class KMeans extends Scoring {

  override def score(vector: Seq[Array[String]]): Option[Future[Seq[Any]]] = {
    model.map(model => {
      model.score(vector)
    })
  }

}
