package uk.co.turingatemyhamster.shortbol
package pragma

import ast._
import ast.sugar._
import ops._
import ops.Eval._
import ShortbolParser.POps

import fastparse.core.Parsed.{Failure, Success}

import scalaz.Scalaz._
import scalaz._

/**
  * Created by nmrp3 on 22/06/16.
  */
object ImportPragma {
  self =>

  def apply(resolver: Resolver): Hook = new Hook {

    override def register(p: Pragma) = for {
      _ <- withPHooks(resolveImport)
    } yield List(p)

    def resolveImport(p: Pragma): EvalState[List[Pragma]] = p match {
      case Pragma(ID, args) =>
        args match {
          case Seq(ValueExp.Identifier(id)) =>
            resolver.resolve(id) flatMap {
              case \/-(imported) => for {
                _ <- log(LogMessage.info(s"Importing $id", p.region))
              } yield List(p)
              case -\/(err) =>
                for {
                  _ <- log(LogMessage.error(s"Import failed for $id", id.region, Some(err)))
                } yield Nil
            }
          case _ =>
            for {
              _ <- log(LogMessage.error(s"Malformed @import declaration", p.region))
            } yield Nil
        }
      case _ =>
        List(p).point[EvalState]
    }

    override val ID: LocalName = self.ID

    override val bootstrap: String = self.bootstrap
  }

  val ID: LocalName = "import"

  val bootstrap: String = "@pragma import url"
}

trait Resolver {
  def resolve(id: Identifier): EvalState[Throwable \/ SBEvaluatedFile]
}

object Resolver {
  def fromValues(vs: (Identifier, SBFile)*): Resolver = new Resolver {
    val id2F = Map(vs :_*)

    override def resolve(id: Identifier): EvalState[Throwable \/ SBEvaluatedFile] =
      id2F get id match {
        case Some(f) =>
          for {
            e <- f.eval
          } yield e.right
        case None => (new NoSuchElementException(s"Unable to resolve shortbol for $id") : Throwable).left.point[EvalState]
      }
  }

  def fromWeb: Resolver = new Resolver {
    override def resolve(id: Identifier): EvalState[Throwable \/ SBEvaluatedFile] = id.eval flatMap {
      case url : Url =>
        resolveUrl(url)
      case qn : QName =>
        PrefixPragma.resolve(qn) flatMap {
          case Some(url) =>
            resolveUrl(url)
          case None =>
            (new Exception(s"Could not resolve identifier $id via $qn") : Throwable).left.point[EvalState]
        }
      case i =>
        // todo: resolve non-url identifiers using the context
        (new Exception(s"Could not resolve identifier $id via $i") : Throwable).left.point[EvalState]
    }

    def resolveUrl(url: Url): EvalState[Throwable \/ SBEvaluatedFile] = for {
      base <- ImportBaseUrl.top
      relative = relativeUrl(base.collect{case Pragma(ImportBaseUrl.ID, (ValueExp.Identifier(url@Url(_)))::Nil) => url}, url)
      res <- Platform.slurp(relative.url) orElse
              Platform.slurp(relative.url ++ ".sbol") match {
        case -\/(t) =>
          t.left[SBEvaluatedFile].point[EvalState]
        case \/-(str) =>
          ImportBaseUrl.pushFrame(Pragma(ImportBaseUrl.ID, relative::Nil))(
            DefaultPrefixPragma.pushFrame(
              ShortbolParser.SBFile.withPositions(relative, str) match {
                case Success(s, _) =>
                  s.eval.map((_: SBEvaluatedFile).right[Throwable])
                case f: Failure =>
                  (new Exception(s"Failed to parse ${url.url} as ${relative.url} at ${f.index}: ${f.extra.traced}") : Throwable).left[SBEvaluatedFile].point[EvalState]
              }
            )
          )
      }
    } yield res

    def relativeUrl(b: Option[Url], l: Url): Url = b map { bu =>
      Url(bu.url.substring(0, bu.url.lastIndexOf("/")) ++ "/" ++ l.url) } getOrElse l
  }
}
