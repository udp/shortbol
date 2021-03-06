package uk.co.turingatemyhamster.shortbol
package ops
package rewriteRule

import monocle.Monocle._

import sharedAst._
import sharedAst.sugar._
import longhandAst.{InstanceExp, PropertyExp}
import longhandAst.sugar._
import RewriteRule.allElements
import pragma.DefaultPrefixPragma
import terms.RDF
import terms.SBOL.displayId

/**
  *
  *
  * @author Matthew Pocock
  */
object RepairIdentities extends InstanceRewriter {

  final private val noDisplayId = (_: List[PropertyExp]).forall(_.property != displayId)
  final private val noAbout = (_: List[PropertyExp]).forall(_.property != RDF.about)

  import optics.longhand.InstanceExp._
  import optics.longhand.ConstructorApp._
  import optics.longhand.SBFile._
  import optics.longhand.PropertyValue._
  import optics.longhand.PropertyExp._
  import Nested.{value => nestedValue}


  lazy val bodyRequiresDisplayId: RewriteRule[List[PropertyExp]] = RewriteRule { (ps: List[PropertyExp]) =>
    for {
      id <- Eval.nextIdentifier
    } yield (displayId := slLit(id.name)) ::: ps
  } at noDisplayId

  def bodyRequiresAbout(parentId: Identifier): RewriteRule[List[PropertyExp]] = RewriteRule { (ps: List[PropertyExp]) =>
    for {
      longhandAst.PropertyExp(_, longhandAst.PropertyValue.Literal(StringLiteral(s, _, _))) <- ps find (_.property == displayId)
      about <- parentId match {
        case LocalName(_) =>
          None
        case QName(pfx, LocalName(ln)) =>
          Some(pfx :# s"$ln/${s.asString}")
        case Url(url) =>
          Some(Url(s"$url/${s.asString}"))
      }
    } yield {
      (RDF.about := about) ::: ps
    }
  } at noAbout

  lazy val recurseOverBody: RewriteRule[List[PropertyExp]] = RewriteRule { (ps: List[PropertyExp]) =>
    for {
      longhandAst.PropertyExp(_, longhandAst.PropertyValue.Reference(about)) <- ps find (_.property == RDF.about)
    } yield
      (bodyRequiresDisplayId andThen bodyRequiresAbout(about) andThen recurseOverBody) at
        body at
        nestedValue at
        asNested at
        value at
        allElements
  }

  lazy val recursefromInstanceExp = recurseOverBody at body at cstrApp

  lazy val cstrAppRequiresDisplayId = bodyRequiresDisplayId at body

  lazy val instanceExpRequiersDisplayIdAndAbout: RewriteRule[InstanceExp] = RewriteRule { (ie: InstanceExp) =>
    RewriteRule { (bdy: List[PropertyExp]) =>
      for {
        id <- DefaultPrefixPragma.rewrite(ie.identifier)
      } yield {
        val withAbout = (RDF.about := id) ::: bdy
        id match {
          case LocalName(ln) =>
            (displayId := slLit(ln)) ::: withAbout
          case QName(_, LocalName(ln)) =>
            (displayId := slLit(ln)) ::: withAbout
          case _ =>
            withAbout
        }
      }
    } at noDisplayId at noAbout at body at cstrApp
  }

  lazy val instanceExpRequiresAbout: RewriteRule[InstanceExp] = RewriteRule { (ie: InstanceExp) =>
    (cstrApp composeLens body) modify
      ((RDF.about := ie.identifier) ::: _) apply
      ie
  } at { (ie: InstanceExp) =>
    ie.cstrApp.body.collectFirst{ case PropertyExp(RDF.about, _) => () }.isEmpty
  }


  lazy val instanceRewrite =
    instanceExpRequiersDisplayIdAndAbout andThen instanceExpRequiresAbout andThen recursefromInstanceExp

}
