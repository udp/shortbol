package uk.co.turingatemyhamster.shortbol
package pragma

/**
  * Created by nmrp3 on 22/06/16.
  */
trait PragmaHook {
  def pHook(p: ast.Pragma): ops.Eval.EvalState[Unit]
}