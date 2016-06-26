package uk.co.turingatemyhamster.shortbol
package pragma

import ast._
import sugar._
import ops._
import ops.Eval._

import scalaz.Scalaz._
import scalaz._

object DefaultPrefixPragma {

  def apply: Hook = new Hook {
    override def register(p: Pragma) = for {
      _ <- withPHooks(pHook)
      _ <- withCHooks(cHook)
      _ <- withIHooks(iHook)
    } yield List(p)

    def pHook(p: Pragma): EvalState[List[Pragma]] = p match {
      case Pragma(ID, args) =>
        args match {
          case Seq(ValueExp.Identifier(pfx)) =>
            for {
              _ <- log(LogMessage.info(s"Registering default prefix $pfx", p.region))
            } yield List(p)
          case _ =>
            for {
              _ <- log(LogMessage.error(s"Malformed @defaultPrefix declaration", p.region))
            } yield Nil
        }
      case _ =>
        Eval.constant(List(p))
    }

    def iHook(i: InstanceExp): EvalState[List[InstanceExp]] = for {
      ii <- ChangeIdentifiers.at(rewrite).instanceExp(i)
    } yield ii::Nil

    def cHook(c: ConstructorDef): EvalState[List[ConstructorDef]] = for {
      cc <- ChangeIdentifiers.at(rewrite).constructorDef(c)
    } yield cc::Nil

    def rewrite(i: Identifier): EvalState[Identifier] = i match {
      case ln : LocalName =>
        for {
          dns <- gets((_: EvalContext).prgms.get(ID).flatMap(_.headOption))
          rewritten <- dns match {
            case Some(ps) =>
              ps.values.head match {
                case ValueExp.Identifier(LocalName(pfx)) =>
                  for {
                    _ <- log(LogMessage.info(s"Applying prefix $pfx to $ln", ln.region))
                  } yield {
                    val lnr = ln.region
                    val nsp = NSPrefix(pfx)
                    nsp.region = lnr.copy(endsAt = lnr.startsAt)
                    val qn = QName(nsp, ln)
                    qn.region = lnr
                    (qn : Identifier).point[EvalState]
                  }
                case _ =>
                  for {
                    _ <- log(LogMessage.error(s"Malformed @defaultPrefix declaration", ps.region))
                  } yield i.point[EvalState]
              }
            case None =>
              for {
                _ <- log(LogMessage.warning(
                  s"Can't resolve local name $i because no @defaultPrefix declaration is in scope", i.region))
              } yield i.point[EvalState]
          }
          is <- rewritten
        } yield is
      case _ =>
        i.point[EvalState]
    }

    override val ID: LocalName = "defaultPrefix"

    override val bootstrap: String = "@pragma defaultPrefix pfx"
  }
}
