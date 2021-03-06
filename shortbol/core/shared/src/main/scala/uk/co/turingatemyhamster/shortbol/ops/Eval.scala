package uk.co.turingatemyhamster
package shortbol.ops

import shortbol.sharedAst._
import shortbol.{shorthandAst => sAst}
import shortbol.{longhandAst => lAst}

sealed trait LogLevel
{
  def pretty: String
}

object LogLevel {
  object Info extends LogLevel    { def pretty = "info" }
  object Warning extends LogLevel { def pretty = "warning" }
  object Error extends LogLevel   { def pretty = "error" }
}

case class LogMessage(msg: String, level: LogLevel, region: Region, cause: Option[Throwable])
{
  def pretty = s"${level.pretty}: $msg" + cause.map(t => " because " ++ t.getMessage).getOrElse("")
}

object LogMessage {
  def info(msg: String, region: Region, cause: Option[Throwable] = None) = LogMessage(msg, LogLevel.Info, region, cause)
  def warning(msg: String, region: Region, cause: Option[Throwable] = None) = LogMessage(msg, LogLevel.Warning, region, cause)
  def error(msg: String, region: Region, cause: Option[Throwable] = None) = LogMessage(msg, LogLevel.Error, region, cause)
}

case class Hooks(phook: Vector[sAst.Pragma => Eval.EvalState[List[sAst.Pragma]]] = Vector.empty,
                 ihook: Vector[lAst.InstanceExp => Eval.EvalState[List[lAst.InstanceExp]]] = Vector.empty,
                 chook: Vector[sAst.ConstructorDef => Eval.EvalState[List[sAst.ConstructorDef]]] = Vector.empty,
                 ahook: Vector[sAst.Assignment => Eval.EvalState[List[sAst.Assignment]]] = Vector.empty)
{
  def withPHooks(ps: (sAst.Pragma => Eval.EvalState[List[sAst.Pragma]])*) =
    copy(phook = phook ++ ps)

  def withIHooks(is: (lAst.InstanceExp => Eval.EvalState[List[lAst.InstanceExp]])*) =
    copy(ihook = ihook ++ is)

  def withCHooks(cs: (sAst.ConstructorDef => Eval.EvalState[List[sAst.ConstructorDef]])*) =
    copy(chook = chook ++ cs)

  def withAHooks(as: (sAst.Assignment => Eval.EvalState[List[sAst.Assignment]])*) =
    copy(ahook = ahook ++ as)
}

trait HooksOptics[H] {
  def withPHooks(h: H, ps: (sAst.Pragma => Eval.EvalState[List[sAst.Pragma]])*): H
  def withIHooks(h: H, is: (lAst.InstanceExp => Eval.EvalState[List[lAst.InstanceExp]])*): H
  def withCHooks(h: H, cs: (sAst.ConstructorDef => Eval.EvalState[List[sAst.ConstructorDef]])*): H
  def withAHooks(h: H, as: (sAst.Assignment => Eval.EvalState[List[sAst.Assignment]])*): H
}

case class EvalContext(prgms: Map[Identifier, List[sAst.Pragma]] = Map.empty,
                       cstrs: Map[Identifier, List[sAst.ConstructorDef]] = Map.empty,
                       vlxps: Map[Identifier, List[sAst.ValueExp]] = Map.empty,
                       insts: Map[Identifier, List[lAst.InstanceExp]] = Map.empty,
                       qnams: Map[LocalName, Set[QName]] = Map.empty,
                       hooks: Hooks = Hooks(),
                       logms: List[LogMessage] = List.empty,
                       newLN: () => LocalName = EvalContext.localNameMaker("syntheticId"))
{

  def withQNams(qs: QName*) =
    copy(qnams = qs.foldLeft(qnams) { case (m, q) => m + (q.localName -> (m.getOrElse(q.localName, Set.empty) + q))})

  def withConstructors(cs: sAst.ConstructorDef*) =
    copy(cstrs = cstrs ++ cs.map(c => c.id -> (c :: cstrs.getOrElse(c.id, Nil)))).withQNams(AllQNames.in(cs) :_*)

  def withAssignments(as: sAst.Assignment*) =
    copy(vlxps = vlxps ++ as.map(a => a.property -> (a.value :: vlxps.getOrElse(a.property, Nil)))).withQNams(AllQNames.in(as) :_*)

  def withPragmas(ps: sAst.Pragma*) =
    copy(prgms = prgms ++ ps.map(p => p.id -> (p :: prgms.getOrElse(p.id, Nil))))

  def withInstances(is: lAst.InstanceExp*) =
    copy(insts = insts ++ is.map(i => i.identifier -> (i :: insts.getOrElse(i.identifier, Nil)))).withQNams(AllQNames.in(is) :_*)

  def withLog(lm: LogMessage*) =
    copy(logms = logms ++ lm)

  def resolveLocalName(ln: LocalName): Set[QName] =
    qnams.getOrElse(ln, Set.empty)


  def resolveValue(id: Identifier): Option[sAst.ValueExp] =
    vlxps get id map (_.head) orElse { // todo: log if there are multiple elements in the list
      id match {
        case ln : LocalName =>
          (resolveLocalName(ln) map sAst.ValueExp.Identifier).headOption // todo: log clashes
        case _ => None
      }
    }

  def resolveCstr(id: Identifier): Option[sAst.ConstructorDef] =
    cstrs get id map (_.head) orElse { // todo: log if there are multiple elements in the list
      id match {
        case ln : LocalName =>
          (resolveLocalName(ln) flatMap resolveCstr).headOption // todo: log clashes
        case _ => None
      }
    }

  def resolveInst(id: Identifier): Option[lAst.InstanceExp] =
    insts get id map (_.head) orElse { // todo: log if there are multiple elements in the list
      id match {
        case ln : LocalName =>
          (resolveLocalName(ln) flatMap resolveInst).headOption // todo: log clashes
        case _ => None
      }
    }

  def withPHooks(ps: (sAst.Pragma => Eval.EvalState[List[sAst.Pragma]])*): EvalContext =
    copy(hooks = hooks.withPHooks(ps :_*))

  def withIHooks(is: (lAst.InstanceExp => Eval.EvalState[List[lAst.InstanceExp]])*): EvalContext =
    copy(hooks = hooks.withIHooks(is :_*))

  def withCHooks(cs: (sAst.ConstructorDef => Eval.EvalState[List[sAst.ConstructorDef]])*): EvalContext =
    copy(hooks = hooks.withCHooks(cs :_*))

  def withAHooks(as: (sAst.Assignment => Eval.EvalState[List[sAst.Assignment]])*): EvalContext =
    copy(hooks = hooks.withAHooks(as :_*))
}

object EvalContext {
  def localNameMaker(pfx: String) = {
    var i = 0
    () => {
      val ln = LocalName(s"${pfx}_$i")
      i += 1
      ln
    }
  }
}

sealed trait Eval[T] {
  self =>
  type Result
  def apply(t: T): Eval.EvalState[Result]
  def log(msg: String): Eval.Aux[T, self.Result] = new Eval[T] {
    override type Result = self.Result
    override def apply(t: T) = for {
      tt <- self.apply(t)
    } yield {
      println(s"$msg:\n\tin:  $t\n\tout: $tt")
      tt
    }
  }
}



import shapeless.{:+:, ::, CNil, Coproduct, Generic, HList, HNil, Inl, Inr, lens}
import shortbol.shapeless._
import scalaz.Scalaz._
import scalaz._


object Eval {
  type Aux[T, R] = Eval[T] { type Result = R }


  private object TypeclassFactory extends TypeClassCompanion2[Aux] {
    object typeClass extends TypeClass2[Aux] {
      override def coproduct[L, R <: Coproduct, LL, RR <: Coproduct](cl: => Aux[L, LL],
                                                                     cr: => Aux[R, RR]) = new Eval[L:+:R]
      {
        override type Result = LL :+: RR

        override def apply(t: L:+:R) = t match {
          case Inl(l) => for (el <- cl apply l) yield Inl(el)
          case Inr(r) => for (er <- cr apply r) yield Inr(er)
        }
      }

      override val emptyCoproduct = Eval.identityEval[CNil]

      override val emptyProduct = Eval.identityEval[HNil]

      override def product[H, HH, T <: HList, TT <: HList](ch: Aux[H, HH], ct: Aux[T, TT]) = new Eval[H::T]
      {
        override type Result = HH::TT
        override def apply(ht: H::T) = for {
          eh <- ch(ht.head)
          et <- ct(ht.tail)
        } yield eh::et
      }

      override def project[F, G, FF, GG](instance: => Aux[G, GG],
                                         to: (F) => G,
                                         from: (GG) => FF) = new Eval[F] {
        override type Result = FF
        override def apply(t: F) = {
          for {
            u <- instance(to(t))
          } yield from(u)
        }
      }
    }
  }

  import TypeclassFactory._

  type EvalState[R] = EvalStateT[Id, R]
  type EvalStateT[M[_], R] = StateT[M, EvalContext, R]

  def log(logMessage: LogMessage) = modify((_: EvalContext).withLog(logMessage))
  def withPHooks(pHook: sAst.Pragma => Eval.EvalState[List[sAst.Pragma]]) = modify((_: EvalContext).withPHooks(pHook))
  def withIHooks(iHook: lAst.InstanceExp => Eval.EvalState[List[lAst.InstanceExp]]) = modify((_: EvalContext).withIHooks(iHook))
  def withCHooks(cHook: sAst.ConstructorDef => Eval.EvalState[List[sAst.ConstructorDef]]) = modify((_: EvalContext).withCHooks(cHook))
  def withAHooks(aHook: sAst.Assignment => Eval.EvalState[List[sAst.Assignment]]) = modify((_ : EvalContext).withAHooks(aHook))

  def constantEval[T, U](u: U) = new Eval[T] {
    override type Result = U
    override def apply(t: T) = u.point[EvalState]
  }

  def identityEval[T] = new Eval[T] {
    override type Result = T
    override def apply(t: T) = t.point[EvalState]
  }

  implicit class EvalOps[T](val _t: T) extends AnyVal {
    def eval[U](implicit e: Aux[T, U]): EvalState[U] = e(_t)
    def evalLog[U](msg: String)(implicit e: Aux[T, U]): EvalState[U] = e.log(msg).apply(_t)
  }

  implicit def listElements[T, U](implicit pa: Aux[T, U]): Aux[List[T], List[U]] = new Eval[List[T]] {
    override type Result = List[U]

    override def apply(ts: List[T]) = (ts map pa.apply).sequenceU
  }



  // Smelly! Find a way to compute this
  implicit lazy val topLevel: Aux[sAst.TopLevel, List[lAst.InstanceExp]] = {
    type U = List[lAst.InstanceExp]:+:
      List[lAst.InstanceExp]:+:
      List[lAst.InstanceExp]:+:
      List[lAst.InstanceExp]:+:
      List[lAst.InstanceExp]:+:
      List[lAst.InstanceExp]:+:CNil
    val g = Generic[sAst.TopLevel]
    val e = TypeclassFactory[g.Repr, U]
    typeClass.project[sAst.TopLevel, g.Repr, List[lAst.InstanceExp], U](e, g.to, _.unify)
  }

  implicit val propertyValue: Aux[sAst.PropertyValue, lAst.PropertyValue] = new Eval[sAst.PropertyValue] {
    override type Result = lAst.PropertyValue

    override def apply(t: sAst.PropertyValue) = t match {
      case sAst.PropertyValue.Literal(l) =>
        (lAst.PropertyValue.Literal(l) : lAst.PropertyValue).point[EvalState]
      case sAst.PropertyValue.Nested(n) =>
        for {
          ne <- n.eval
        } yield lAst.PropertyValue.Nested(ne)
      case sAst.PropertyValue.Reference(r) =>
        for {
          v <- (sAst.ValueExp.Identifier(r) : sAst.ValueExp).eval
        } yield v match {
          case sAst.ValueExp.Identifier(i) =>
            lAst.PropertyValue.Reference(i)
          case sAst.ValueExp.Literal(l) =>
            lAst.PropertyValue.Literal(l)
        }
    }
  }

  implicit val propertyExp: Aux[sAst.PropertyExp, lAst.PropertyExp] = new Eval[sAst.PropertyExp] {
    override type Result = lAst.PropertyExp

    override def apply(t: sAst.PropertyExp) = for {
      p <- t.property.eval
      v <- t.value.eval
    } yield {
      val pe = lAst.PropertyExp(p, v)
      pe.region = t.region
      pe
    }
  }

  implicit val bodyStmt: Aux[sAst.BodyStmt, List[lAst.PropertyExp]] = new Eval[sAst.BodyStmt] {
    override type Result = List[lAst.PropertyExp]

    override def apply(t: sAst.BodyStmt) = t match {
      case sAst.BodyStmt.PropertyExp(pe) =>
        for {
          p <- pe.eval
        } yield List(p)
      case _ =>
        List.empty[lAst.PropertyExp].point[EvalState]
    }
  }

  implicit val literal: Aux[Literal, Literal] = identityEval

  // fixme: see if this is redundant
  implicit val sbFile: Aux[sAst.SBFile, lAst.SBFile] = new Eval[sAst.SBFile] {
    override type Result = lAst.SBFile

    override def apply(t: sAst.SBFile) = for {
      ts <- t.tops.eval
    } yield {
      val f = lAst.SBFile(ts.flatten)
      f.region = t.region
      f
    }
  }

//  implicit val blankLine: Aux[sAst.BlankLine, sAst.BlankLine] = identityEval

  implicit val topLevel_blankLine: Aux[sAst.TopLevel.BlankLine, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.BlankLine] {
    override type Result = List[lAst.InstanceExp]

    override def apply(t: sAst.TopLevel.BlankLine) =
      List.empty[lAst.InstanceExp].point[EvalState]
  }

  implicit val topLevel_comment: Aux[sAst.TopLevel.Comment, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.Comment] {
      override type Result = List[lAst.InstanceExp]

      override def apply(t: sAst.TopLevel.Comment) =
        List.empty[lAst.InstanceExp].point[EvalState]
    }

  implicit val topLevel_pragma: Aux[sAst.TopLevel.Pragma, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.Pragma] {
    override type Result = List[lAst.InstanceExp]

    override def apply(t: sAst.TopLevel.Pragma) = for {
      p <- t.pragma.eval
      _ <- modify((_: EvalContext).withPragmas(p :_*))
    } yield Nil
  }

  implicit val pragama: Aux[sAst.Pragma, List[sAst.Pragma]] = new Eval[sAst.Pragma] {
    override type Result = List[sAst.Pragma]

    override def apply(t: sAst.Pragma) = for {
      phook <- gets((_: EvalContext).hooks.phook)
      hook = phook.foldl((p: sAst.Pragma) => (List(p)).point[EvalState])(
        h1 => h2 => (p0: sAst.Pragma) => for {
          p1 <- h1(p0)
          p2 <- (p1 map h2).sequence
        } yield p2.flatten)
      cd <- hook(t)
    } yield cd
  }

  implicit val assignment: Aux[sAst.Assignment, sAst.Assignment] = new Eval[sAst.Assignment] {
    override type Result = sAst.Assignment

    override def apply(t: sAst.Assignment) = for {
      p <- t.property.eval
      v <- t.value.eval
    } yield {
      val a = sAst.Assignment(p, v)
      a.region = t.region
      a
    }
  }

  implicit val topLevel_assignment: Aux[sAst.TopLevel.Assignment, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.Assignment] {
    override type Result = List[lAst.InstanceExp]

    override def apply(t: sAst.TopLevel.Assignment) = for {
      ahook <- gets((_: EvalContext).hooks.ahook)
      hook = ahook.foldl((a: sAst.Assignment) => List(a).point[EvalState])(
        h1 => h2 => (a0 : sAst.Assignment) => for {
          a1 <- h1(a0)
          a2 <- (a1 map h2).sequence
        } yield a2.flatten)
      a <- hook(t.assignment)
      _ <- modify((_: EvalContext).withAssignments(a :_*))
    } yield Nil
  }

  implicit val topLevel_constructorDef: Aux[sAst.TopLevel.ConstructorDef, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.ConstructorDef] {
    override type Result = List[lAst.InstanceExp]

    override def apply(t: sAst.TopLevel.ConstructorDef) = for {
      cd <- t.constructorDef.eval
      _ <- modify((_: EvalContext).withConstructors(cd :_*))
    } yield Nil
  }

  implicit val constructorDef: Aux[sAst.ConstructorDef, List[sAst.ConstructorDef]] = new Eval[sAst.ConstructorDef] {
    override type Result = List[sAst.ConstructorDef]

    override def apply(t: sAst.ConstructorDef) = for {
      chook <- gets((_: EvalContext).hooks.chook)
      hook = chook.foldl((c: sAst.ConstructorDef) => List(c).point[EvalState])(
        h1 => h2 => (c0: sAst.ConstructorDef) => for {
          c1 <- h1(c0)
          c2 <- (c1 map h2).sequence
        } yield c2.flatten)
      cd <- hook(t)
    } yield cd
  }

  implicit val constructorApp: Aux[sAst.ConstructorApp, lAst.ConstructorApp] = new Eval[sAst.ConstructorApp] {
    override type Result = lAst.ConstructorApp

    override def apply(ca: sAst.ConstructorApp) = for {
      tcTcBody <- ca.cstr.eval
      (tc, tcBody) = tcTcBody
      body <- ca.body.eval
    } yield {
      val c = lAst.ConstructorApp(tc, tcBody ++ body.flatten)
      c.region = ca.region
      c
    }
  }


  implicit val instanceExp: Aux[sAst.InstanceExp, List[lAst.InstanceExp]] = new Eval[sAst.InstanceExp] {
    override type Result = List[lAst.InstanceExp]

    override def apply(i: sAst.InstanceExp) = for {
      ce <- i.cstrApp.eval
      ihook <- gets((_: EvalContext).hooks.ihook)
      hook = ihook.foldl((i: lAst.InstanceExp) => List(i).point[EvalState])(
        h1 => h2 => (i0: lAst.InstanceExp) => for
        {
          i1 <- h1(i0)
          i2 <- (i1 map h2).sequence
        } yield i2.flatten)
      ie = lAst.InstanceExp(i.identifier, ce)
      _ = ie.region = i.region
      is <- hook(ie)
    } yield is
  }

  implicit val topLevel_instanceExp: Aux[sAst.TopLevel.InstanceExp, List[lAst.InstanceExp]] = new Eval[sAst.TopLevel.InstanceExp] {
    override type Result = List[lAst.InstanceExp]

    override def apply(t: sAst.TopLevel.InstanceExp) = for {
      is <- t.instanceExp.eval
      _ <- modify((_: EvalContext).withInstances(is :_*))
    } yield is
  }

  implicit val constructorDefApp: Aux[(List[sAst.ValueExp], sAst.ConstructorDef), (lAst.TpeConstructor, List[lAst.PropertyExp])] = new Eval[(List[sAst.ValueExp], sAst.ConstructorDef)] {
    override type Result = (lAst.TpeConstructor, List[lAst.PropertyExp])

    override def apply(vscd: (List[sAst.ValueExp], sAst.ConstructorDef)) = for {
      cd <- withStack(vscd._2.args, vscd._1)(vscd._2.cstrApp.eval)
    } yield (cd.cstr, cd.body)

    def withStack[T](names: List[Identifier], values: List[sAst.ValueExp])(sf: EvalState[T]) = for {
      ec <- get[EvalContext]
      _ <- modify ((_: EvalContext).withAssignments(names zip values map (sAst.Assignment.apply _).tupled :_*))
      v <- sf
      _ <- put(ec) // fixme: should we only be only overwriting the bindings?
    } yield v
  }

  implicit val tpeConstructor1: Aux[sAst.TpeConstructor1, (lAst.TpeConstructor, List[lAst.PropertyExp])] = new Eval[sAst.TpeConstructor1] {
    override type Result = (lAst.TpeConstructor, List[lAst.PropertyExp])

    override def apply(t: sAst.TpeConstructor1) = for {
      ot <- resolveWithAssignment(t.id)
      args <- t.args.eval
      ts <- ot match {
        case Some(cd) =>
          (args, cd).eval
        case None =>
          for {
            id <- t.id.eval
            _ <-
              if(args.nonEmpty)
                Eval.log(LogMessage.error(s"Expected empty arguments list for ${t.id} but found $args", t.region))
              else
                ().point[EvalState]
          } yield {
            val t1 = lAst.TpeConstructor(id)
            t1.region = t.region
            (t1, List.empty)
          }
      }
    } yield ts

    def resolveWithAssignment(id: Identifier): EvalState[Option[sAst.ConstructorDef]] = for {
      c <- cstr(id)
      cc <- c match {
        case Some(_) =>
          c.point[EvalState]
        case None =>
          for {
            b <- resolveBinding(id)
            bb <- b match {
              case Some(sAst.ValueExp.Identifier(nid)) =>
                resolveWithAssignment(nid)
              case _ =>
                (None : Option[sAst.ConstructorDef]).point[EvalState]
            }
          } yield bb
      }
    } yield cc
  }

  // fixme: TpeConstructorStar has borked semantics
  implicit val tpeConstructorStar: Aux[sAst.TpeConstructorStar, (lAst.TpeConstructor, List[lAst.PropertyExp])] = new Eval[sAst.TpeConstructorStar] {
    override type Result = (lAst.TpeConstructor, List[lAst.PropertyExp])

    override def apply(t: sAst.TpeConstructorStar) = ((??? : lAst.TpeConstructor) -> List.empty[lAst.PropertyExp]).point[EvalState]
  }

  implicit val tpeConstructor: Aux[sAst.TpeConstructor, (lAst.TpeConstructor, List[lAst.PropertyExp])] =
  {
    type U = (lAst.TpeConstructor, List[lAst.PropertyExp]):+:(lAst.TpeConstructor, List[lAst.PropertyExp]):+:CNil
    val g = Generic[sAst.TpeConstructor]
    val e = TypeclassFactory[g.Repr, U]
    typeClass.project[sAst.TpeConstructor, g.Repr, (lAst.TpeConstructor, List[lAst.PropertyExp]), U](e, g.to, _.unify)
  }

  implicit val identifier: Aux[Identifier, Identifier] = new Eval[Identifier] {
    override type Result = Identifier

    def apply(id: Identifier) = resolveWithAssignment(id)

    def resolveWithAssignment(id: Identifier): State[EvalContext, Identifier] = for {
      b <- resolveBinding(id)
      rb <- b match {
        case Some(sAst.ValueExp.Identifier(rid)) =>
          resolveWithAssignment(rid)
        case _ =>
          id.point[EvalState]
      }
    } yield rb
  }

  implicit val valueExp: Aux[sAst.ValueExp, sAst.ValueExp] = new Eval[sAst.ValueExp] {
    override type Result = sAst.ValueExp

    override def apply(t: sAst.ValueExp) = t match {
      case sAst.ValueExp.Literal(l) =>
        for {
          ll <- l.eval
        } yield sAst.ValueExp.Literal(ll)
      case sAst.ValueExp.Identifier(i) =>
        resolveWithAssignment(i)
    }

    def resolveWithAssignment(id: Identifier): State[EvalContext, sAst.ValueExp] = for {
      b <- resolveBinding(id)
      rb <- b match {
        case Some(sAst.ValueExp.Identifier(rid)) =>
          resolveWithAssignment(rid)
        case Some(l@sAst.ValueExp.Literal(_)) =>
          l.point[EvalState]
        case None =>
          sAst.ValueExp.Identifier(id).point[EvalState]
      }
    } yield rb
  }

  def as[T, U](implicit e: Aux[U, U], to: T <:< U): Aux[T, U] =
    typeClass.project[T, U, U, U](e, to, identity)

  implicit val localName: Aux[LocalName, Identifier] = as[LocalName, Identifier]
  implicit val qname: Aux[QName, Identifier] = as[QName, Identifier]
  implicit val url: Aux[Url, Identifier] = as[Url, Identifier]

  def cstr(id: Identifier): State[EvalContext, Option[sAst.ConstructorDef]] =
    gets ((_: EvalContext).resolveCstr(id))

  def inst(id: Identifier): State[EvalContext, Option[lAst.InstanceExp]] =
    gets ((_: EvalContext).resolveInst(id))

  def resolveBinding(id: Identifier): State[EvalContext, Option[sAst.ValueExp]] =
    gets ((_: EvalContext).resolveValue(id))

  def nextIdentifier: State[EvalContext, LocalName] =
    gets ((_: EvalContext).newLN) map (_.apply())
}
