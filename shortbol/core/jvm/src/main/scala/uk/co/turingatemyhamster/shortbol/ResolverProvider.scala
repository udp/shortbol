package uk.co.turingatemyhamster.shortbol

import java.net.URI

import fastparse.core.Parsed.{Failure, Success}
import uk.co.turingatemyhamster.shortbol.ast.{SBFile, Url}
import uk.co.turingatemyhamster.shortbol.ops.{ResolverBase, ShortbolParser}

import scala.io.Source
import scalaz._
import Scalaz._

/**
 * Created by nmrp3 on 08/09/15.
 */
trait ResolverProvider extends ResolverBase {
  override def resolve(baseUrl: Option[Url], url: Url): Throwable \/ SBFile = {
    val resUri = baseUrl match {
      case Some(b) =>
        new URI(b.url).resolve(url.url).toString
      case None =>
        url.url
    }
    val src = Source.fromURL(resUri)
    ShortbolParser.SBFile.parse(src.mkString) match {
      case Success(s, _) =>
        s.copy(rdfAbout = Some(url), source = Some(Url(resUri))).right
      case f: Failure =>
        new Exception(s"Failed to parse $url at ${f.index}: ${f.extra.traced}").left
    }
  }
}
