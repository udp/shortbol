package uk.co.turingatemyhamster.shortbol

import ast._
import sugar._
import ops._
import utest._

/**
  *
  *
  * @author Matthew Pocock
  */
object ConstraintsTestSuite extends TestSuite {

  def success[A](cr: ConstraintRule[A], a: A): Unit = {
    cr(a).fold(
      unexpectedFailure => assert(cr == null, unexpectedFailure == null),
      va => assert(va == a)
    )
  }

  def failure[A](cr: ConstraintRule[A], a: A): Unit = {
    cr(a).fold(nel => (),
      unexpectedSuccess => assert(cr == null, unexpectedSuccess == null))
  }

  def failure[A](cr: ConstraintRule[A], a: A, cv: ConstraintViolation[A]): Unit = {
    cr(a).fold(
      nel => assert(nel.head == cv),
      unexpectedSuccess => assert(cr == null, unexpectedSuccess == null))
  }

  override def tests = TestSuite {

    'core - {
      'SucceedAlways - {
        success(
          AlwaysSucceed[Int](),
          5)
      }

      'FailAlways - {
        failure(
          AlwaysFail[Int](Some("Test failure")),
          5,
          ConstraintFailure(AlwaysFail[Int](Some("Test failure")), 5))
      }

      'If - {
        'onlyIf - {
          "success onlyIf success" - {
            success(
              ConstraintRule.success[Int] onlyIf ConstraintRule.success[Int],
              42)
          }

          "success onlyIf fail" - {
            success(
              ConstraintRule.success[Int] onlyIf ConstraintRule.fail[Int],
              42)
          }

          "fail onlyIf success" - {
            failure(
              ConstraintRule.fail[Int] onlyIf ConstraintRule.success[Int],
              42,
              ConstraintViolation.failure(ConstraintRule.fail[Int], 42))
          }

          "fail onlyIf fail" - {
            success(
              ConstraintRule.fail[Int] onlyIf ConstraintRule.fail[Int],
              42)
          }
        }

        'unless - {
          "success unless success" - {
            success(
              ConstraintRule.success[Int] unless ConstraintRule.success[Int],
              42)
          }

          "success unless fail" - {
            success(
              ConstraintRule.success[Int] unless ConstraintRule.fail[Int],
              42)
          }

          "fail unless success" - {
            success(
              ConstraintRule.fail[Int] unless ConstraintRule.success[Int],
              42)
          }

          "fail unless fail" - {
            failure(
              ConstraintRule.fail[Int] unless ConstraintRule.fail[Int],
              42,
              ConstraintViolation.failure(ConstraintRule.fail[Int], 42))
          }
        }
      }

      'NotLessThan - {
        'greater - {
          success(
            NotLessThan(42),
            100)
        }

        'lesser - {
          failure(
            NotLessThan(42),
            1,
            ConstraintViolation.failure(NotLessThan(42), 1)
          )
        }
      }

      'NotGreaterThan - {
        'greater - {
          failure(
            NotGreaterThan(42),
            100,
            ConstraintViolation.failure(NotGreaterThan(42), 100)
          )
        }

        'lesser - {
          success(
            NotGreaterThan(42),
            1)
        }
      }

      'sizeNotLessThan - {
        'greater - {
          success(
            At('size, NotLessThan(2))((_: List[Int]).size),
            List(1, 2, 3, 4)
          )
        }

        'lesser - {
          failure(
            At('size, NotLessThan(2))((_: List[Int]).size),
            List(1),
            ViolationAt(List(1), 'size, ConstraintViolation.failure(NotLessThan(2), 1)))
        }
      }

      'EqualTo - {
        'areEqual - {
          success(
            EqualTo("bob"),
            "bob"
          )
        }

        'areUnequal - {
          failure(
            EqualTo("bob"),
            "jane",
            ConstraintViolation.failure(EqualTo("bob"), "jane")
          )
        }
      }
    }

    'fromAssignments - {
      'minCardinality - {
        'isUndefinedForNotMin - {
          val cc = OWL.minCardinalityConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 2)))
          assert(cc.isEmpty)
        }

        'isDefinedForMin - {
          val cc = OWL.minCardinalityConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)))
          assert(cc.isDefined)
        }

        'succeedsWithMore - {
          val cc = OWL.minCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2))).get

          success(
            cc,
            BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: Nil)
        }

        'succeedsWithExact - {
          val cc = OWL.minCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2))).get

          success(
            cc,
            BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: Nil)
        }

        'failsWithFewer - {
          val cc = OWL.minCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2))).get
          val bs = BodyStmt.BlankLine(BlankLine()) :: List.empty[BodyStmt]

          failure(
            cc,
            bs,
            ViolationAt(bs, 'size, ConstraintViolation.failure(NotLessThan(2), 1)))
        }

      }

      'maxCardinality - {
        'isUndefinedForNotMax - {
          val cc = OWL.maxCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)))
          assert(cc.isEmpty)
        }

        'isDefinedForMax - {
          val cc = OWL.maxCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 2)))
          assert(cc.isDefined)
        }

        'failsWithMore - {
          val cc = OWL.maxCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 2))).get
          val bs = BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: List.empty[BodyStmt]

          failure(
            cc,
            bs,
            ViolationAt(bs, 'size, ConstraintViolation.failure(NotGreaterThan(2), 3)))
        }

        'succeedsWithExact - {
          val cc = OWL.maxCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 2))).get

          success(
            cc,
            BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: Nil)
        }

        'succeedsWithTooFew - {
          val cc = OWL.maxCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 2))).get
          val bs = BodyStmt.BlankLine(BlankLine()) :: List.empty[BodyStmt]

          success(
            cc,
            bs)
        }
      }

      'exactCardinality - {
        'isUndefinedForNotExact - {
          val cc = OWL.exactCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)))
          assert(cc.isEmpty)
        }

        'isDefinedForExact - {
          val cc = OWL.exactCardinalityConstraint(BodyStmt.Assignment(Assignment(OWL.owl_exactCardinality, 2)))
          assert(cc.nonEmpty)
        }

        'failsWithMore - {
          val cc = ApplyAll(
            OWL.exactCardinalityConstraint(
              BodyStmt.Assignment(Assignment(OWL.owl_exactCardinality, 2))))
          val bs = BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: List.empty[BodyStmt]

          failure(
            cc,
            bs,
            ViolationAt(bs, 'size, ConstraintViolation.failure(NotGreaterThan(2), 3)))
        }

        'succeedsWithExact - {
          val cc = ApplyAll(
            OWL.exactCardinalityConstraint(
              BodyStmt.Assignment(Assignment(OWL.owl_exactCardinality, 2))))

          success(
            cc,
            BodyStmt.BlankLine(BlankLine()) :: BodyStmt.BlankLine(BlankLine()) :: Nil)
        }

        'failsWithFewer - {
          val cc = ApplyAll(
            OWL.exactCardinalityConstraint(
              BodyStmt.Assignment(Assignment(OWL.owl_exactCardinality, 2))))
          val bs = BodyStmt.BlankLine(BlankLine()) :: List.empty[BodyStmt]

          failure(
            cc,
            bs,
            ViolationAt(bs, 'size, ConstraintViolation.failure(NotLessThan(2), 1)))
        }
      }

      'allValuesFrom - {
        val OWLC = OWL.fromContext(Fixture.configuredContext)

        'isUndefinedForNotAllValuesFrom - {
          val tc = OWLC.allValuesFromConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, "myClass")))
          assert(tc.isEmpty)
        }

        'isDefinedForAllValuesFrom - {
          val tc = OWLC.allValuesFromConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "myClass")))
          assert(tc.isDefined)
        }

        'succeedsWithMatchingType - {
          val tc = OWLC.allValuesFromConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "myClass"))).get
          val bs = BodyStmt.InstanceExp(InstanceExp(
            "rod",
            ConstructorApp(TpeConstructor1("myClass", Seq()), Seq()))) :: List.empty[BodyStmt]

          success(
            tc,
            bs)
        }

        'failsWithNonMatchingType - {
          val tc = OWLC.allValuesFromConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "myClass"))).get
          val bs = BodyStmt.InstanceExp(InstanceExp(
            "rod",
            ConstructorApp(TpeConstructor1("jane", Seq()), Seq()))) :: List.empty[BodyStmt]

          failure(
            tc,
            bs,
            ViolationAt(
              bs, 0, ViolationAt(
                bs(0), 'type, ConstraintViolation.failure(
                  EqualTo(Some("myClass" : Identifier)), Some("jane" : Identifier)))))
        }

        'failsWithNonMatchingType1 - {
          val tc = OWLC.allValuesFromConstraint(
            BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "myClass"))).get
          val bs =
            BodyStmt.InstanceExp(InstanceExp(
              "rod",
              ConstructorApp(TpeConstructor1("myClass", Seq()), Seq()))) ::
              BodyStmt.InstanceExp(InstanceExp(
                "jane",
                ConstructorApp(TpeConstructor1("freddy", Seq()), Seq()))) :: List.empty[BodyStmt]

          failure(
            tc,
            bs,
            ViolationAt(
              bs, 1, ViolationAt(
                bs(1), 'type, ConstraintViolation.failure(
                  EqualTo(Some("myClass" : Identifier)), Some("freddy" : Identifier)))))
        }

        'multipleRestrictions - {
          val r = OWLC.restrictions(
            BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)) ::
              BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 4)) ::
              BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "prov" :# "Agent")) ::
              List.empty[BodyStmt])

          r
        }
      }
    }

    'fromRestrictions - {
      val OWLC = OWL.fromContext(Fixture.configuredContext)

      val ri = InstanceExp(
        "dc" :# "name",
        ConstructorApp(
          TpeConstructor1(
            OWL.owl_propertyRestriction, Seq()),
          BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)) ::
            BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 4)) ::
            BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "prov" :# "Agent")) ::
            List.empty[BodyStmt]))

      val c = OWLC.restrictionInstance(ri)

      c
    }

    'fromOwlClass - {
      val OWLC = OWL.fromContext(Fixture.configuredContext)

      val oc = InstanceExp(
        "jane",
        ConstructorApp(
          TpeConstructor1(
            OWL.owl_class,
            Seq()
          ),
          Seq(
            BodyStmt.InstanceExp(
              InstanceExp(
                "dc" :# "name",
                ConstructorApp(
                  TpeConstructor1(
                    OWL.owl_propertyRestriction, Seq()),
                  BodyStmt.Assignment(Assignment(OWL.owl_minCardinality, 2)) ::
                    BodyStmt.Assignment(Assignment(OWL.owl_maxCardinality, 4)) ::
                    BodyStmt.Assignment(Assignment(OWL.owl_allValuesFrom, "prov" :# "Agent")) ::
                    List.empty[BodyStmt]))))))

      val c = OWLC.owlClassConstraint(oc)

      c
    }

    'withOntology - {

      val ontology =
        """
          |sbol:Identified : owl:Class
          |  dc:description = "The base type for SBOL entities."
          |  sbol:persistentIdentity : owl:propertyRestriction
          |    owl:subPropertyOf = prov:wasRevisionOf
          |    owl:minCardinality = 0
          |    owl:maxCardinality = 1
          |    owl:allValuesFrom = prov:Entity
          |  sbol:displayId : owl:propertyRestriction
          |    owl:subPropertyOf = dc:identifier
          |    owl:minCardinality = 0
          |    owl:maxCardinality = 1
          |    owl:allValuesFrom = xsd:string
          |
          |sbol:TopLevel : owl:Class
          |  dc:description = "The type of all entities that can be at the top of an SBOL file."
          |  owl:subClassOf = Identified
          |
          |sbol:Collection : owl:Class
          |  dc:description = "A group of TopLevel objects that have something in common."
          |  owl:subClassOf = TopLevel
          |  sbol:member : owl:propertyRestriction
          |    owl:allValuesFrom = TopLevel
        """.stripMargin

      import Eval.EvalOps
      import ShortbolParser.POps
      val ontologyCtxt = ShortbolParser.SBFile.withPositions("_ontology_", ontology).get.value.eval.exec(Fixture.configuredContext)

      val cSys = ConstraintSystem(OWL)

      def typecheck(sbol: String, c: EvalContext): ConstraintRule.CheckedConstraints[SBEvaluatedFile] =
        cSys(ShortbolParser.SBFile.withPositions("_test_", sbol).get.value.eval.run(c))

      'createsTypes - {
        println(s"createTypes: ${ontologyCtxt.insts}")
        val r = cSys fromContext ontologyCtxt
        println("----")

        r
      }

      'valType - {
        'withCorrectType - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:persistentIdentity : prov:Entity
            """.stripMargin,
            ontologyCtxt)

          assert(r.isSuccess)
        }

        'withIncorrectType -{
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:persistentIdentity : prov:Agent
            """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }
      }

      'refType {
        'refWithCorrectType - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:persistentIdentity = xP
              |
              |xP : prov:Entity
            """.stripMargin,
            ontologyCtxt)

          assert(r.isSuccess)
        }

        'refWithIncorrectType -{
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:persistentIdentity = xP
              |
              |xP : prov:Agent
            """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }

        'refWithMissingValue -{
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:persistentIdentity = xP
            """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }
      }

      'displayId - {
        'noString - {
          val r = typecheck(
            """
              |x : sbol:Identified
            """.stripMargin,
            ontologyCtxt)

          assert(r.isSuccess)
        }

        'oneString - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:displayId = "aaaa"
            """.stripMargin,
            ontologyCtxt)

          assert(r.isSuccess)
        }

        'twoString - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:displayId = "aaaa"
              |  sbol:displayId = "bbbb"
              """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }

        'oneInteger - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:displayId = 42
              """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }

        'oneDate - {
          val r = typecheck(
            """
              |x : sbol:Identified
              |  sbol:displayId = "28-01-1976"^^xsd:date
              """.stripMargin,
            ontologyCtxt)

          assert(r.isFailure)
        }
      }
    }
  }

}
