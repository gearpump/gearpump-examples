package io.gearpump.examples.atk_pipeline

import java.io.File
import java.net.URI

import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.Path
import org.trustedanalytics.atk.model.publish.format.ModelPublishFormat
import org.trustedanalytics.atk.scoring.interfaces.Model

import scala.concurrent.Future

trait Scoring extends java.io.Serializable {

  var model: Option[Model] = None

  def load(tar: String): Unit = {
    val pt = new Path(tar)
    val uri = new URI(tar)
    val hdfsFileSystem: org.apache.hadoop.fs.FileSystem = org.apache.hadoop.fs.FileSystem.get(uri, new Configuration())
    val tempFilePath = "/tmp/kmeans.tar"
    val local = new Path(tempFilePath)
    hdfsFileSystem.copyToLocalFile(false, pt, local)
    model = Option(ModelPublishFormat.read(new File(tempFilePath), Thread.currentThread().getContextClassLoader))
  }

  def score(vector: Seq[Array[String]]): Option[Future[Seq[Any]]]

}
