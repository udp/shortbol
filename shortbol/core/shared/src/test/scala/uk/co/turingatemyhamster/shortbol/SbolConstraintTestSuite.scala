package uk.co.turingatemyhamster.shortbol

import ops._
import Eval.EvalOps
import ShortbolParser.POps
import ast.sugar._
import uk.co.turingatemyhamster.shortbol.ast.SBEvaluatedFile
import utest._

import scala.util.Random

/**
  *
  *
  * @author Matthew Pocock
  */
object SbolConstraintTestSuite extends TestSuite {

  val sbol = ConstraintSystem(SBOL)

  def succeeds(src: String): Unit = {
    val (c, v) = ShortbolParser.SBFile.withPositions("_test_", src).get.value.eval.run(Fixture.configuredContext)
    sbol()(c)(v).fold(
      nel => assert(nel == null),
      success => ()
    )
  }

  def fails(src: String): Unit = {
    val (c, v) = ShortbolParser.SBFile.withPositions("_test_", src).get.value.eval.run(Fixture.configuredContext)
    sbol()(c)(v).fold(
      nel => {
      },
      success => assert(false)
    )
  }

  val randomIdentifier = {
    val r = new Random()
    val cs = ('A' to 'Z') ++ ('a' to 'z')
    val ds = '0' to '9'
    val as = cs ++ ds

    () => (cs(r.nextInt(cs.size)) :: List.fill(7)(as(r.nextInt(as.size)))).mkString : ast.Identifier
  }

  def recovers(src: String): Unit = {
    val (c, v) = ShortbolParser.SBFile.withPositions("_test_", src).get.value.eval.run(Fixture.configuredContext)
    sbol(SBOLRecovery.nestedAboutRecovery(randomIdentifier))(c)(v).fold(
      nel => assert(nel == null),
      success => ()
    )
  }

  override def tests = TestSuite {
    'topLevel - {

      'nonTopLevel - {
        'noAbout - {
          succeeds(
            """
              |x : foaf:Person
              |  foaf:givenName = "bob"
            """.stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : foaf:Person
              |  rdf:about = <http://foo.bar.com/bob>
            """.stripMargin
          )
        }
      }

      'topLevel - {
        'noAbout - {
          succeeds(
            """
              |x : sbol:TopLevel
              |  foaf:givenName = "bob"
            """.stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : sbol:TopLevel
              |  rdf:about = <http://foo.bar.com/bob>
            """.stripMargin
          )
        }
      }
    }

    'nestedNonIdentified - {

      'nonTopLevel - {
        'noAbout - {
          succeeds(
            """
              |x : prov:Agent
              |  prov:actedOnBehalfOf : foaf:Person
              |    foaf:givenName = "bob"
            """.stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : prov:Agent
              |  prov:actedOnBehalfOf : foaf:Person
              |    rdf:about = <http://foo.bar.com/bob>
            """.stripMargin
          )
        }
      }

      'topLevel - {
        'noAbout - {
          succeeds(
            """
              |x : sbol:TopLevel
              |  prov:actedOnBehalfOf : foaf:Person
              |    foaf:givenName = "bob"
              |""".stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : sbol:TopLevel
              |  prov:actedOnBehalfOf : foaf:Person
              |    rdf:about = <http://foo.bar.com/bob>
              |""".stripMargin
          )
        }
      }
    }

    'nestedIdentified - {

      'nonTopLevel - {
        'noAbout - {
          succeeds(
            """
              |x : prov:Agent
              |  prov:actedOnBehalfOf : sbol:Identified
              |    foaf:givenName = "bob"
            """.stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : prov:Agent
              |  prov:actedOnBehalfOf : sbol:Identified
              |    rdf:about = <http://foo.bar.com/bob>
            """.stripMargin
          )
        }
      }

      'topLevel - {
        'noAboutFails - {
          fails(
            """
              |x : sbol:TopLevel
              |  prov:actedOnBehalfOf : sbol:Identified
              |    foaf:givenName = "bob"
              |""".stripMargin
          )
        }

        'noAboutRecovers - {
          recovers(
            """
              |x : sbol:TopLevel
              |  prov:actedOnBehalfOf : sbol:Identified
              |    foaf:givenName = "bob"
              |""".stripMargin
          )
        }

        'about - {
          succeeds(
            """
              |x : sbol:TopLevel
              |  prov:actedOnBehalfOf : sbol:Identified
              |    rdf:about = <http://foo.bar.com/bob>
              |""".stripMargin
          )
        }
      }
    }


  }

}
