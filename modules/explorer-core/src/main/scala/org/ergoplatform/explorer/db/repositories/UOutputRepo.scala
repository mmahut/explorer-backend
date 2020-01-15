package org.ergoplatform.explorer.db.repositories

import fs2.Stream
import cats.syntax.functor._
import org.ergoplatform.explorer.TxId
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.syntax.liftConnectionIO._
import org.ergoplatform.explorer.db.models.UOutput

/** [[UOutput]] data access operations.
  */
trait UOutputRepo[D[_], S[_[_], _]] {

  /** Put a given unconfirmed `output` to persistence.
    */
  def insert(output: UOutput): D[Unit]

  /** Put a given list of unconfirmed outputs to persistence.
    */
  def insertMany(outputs: List[UOutput]): D[Unit]

  /** Get all outputs containing in unconfirmed transactions.
    */
  def getAll(offset: Int, limit: Int): S[D, UOutput]

  /** Get all unconfirmed outputs related to transaction with a given `txId`.
    */
  def getAllByTxId(txId: TxId): D[List[UOutput]]
}

object UOutputRepo {

  def apply[D[_]: LiftConnectionIO]: UOutputRepo[D, Stream] =
    new Live[D]

  final private class Live[D[_]: LiftConnectionIO] extends UOutputRepo[D, Stream] {

    import org.ergoplatform.explorer.db.queries.{UOutputQuerySet => QS}

    def insert(output: UOutput): D[Unit] =
      QS.insert(output).void.liftConnectionIO

    def insertMany(outputs: List[UOutput]): D[Unit] =
      QS.insertMany(outputs).void.liftConnectionIO

    def getAll(offset: Int, limit: Int): Stream[D, UOutput] =
      QS.getAll(offset, limit).translate(LiftConnectionIO[D].liftConnectionIOK)

    def getAllByTxId(txId: TxId): D[List[UOutput]] =
      QS.getAllByTxId(txId).liftConnectionIO
  }
}
