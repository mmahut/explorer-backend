package org.ergoplatform.explorer.http.api.v0.models

import cats.instances.list._
import cats.instances.option._
import cats.syntax.traverse._
import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.TxId
import org.ergoplatform.explorer.db.models.aggregates.{ExtendedUDataInput, ExtendedUInput}
import org.ergoplatform.explorer.db.models.{UAsset, UOutput, UTransaction}
import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.Derived

final case class UTransactionInfo(
  id: TxId,
  inputs: List[UInputInfo],
  dataInputs: List[UDataInputInfo],
  outputs: List[UOutputInfo],
  creationTimestamp: Long,
  size: Int
)

object UTransactionInfo {

  implicit val codec: Codec[UTransactionInfo] = deriveCodec

  implicit val schema: Schema[UTransactionInfo] =
    Schema.derive[UTransactionInfo]
      .modify(_.id)(_.description("Transaction ID"))
      .modify(_.creationTimestamp)(
        _.description("Approximate time this transaction appeared in the network")
      )
      .modify(_.size)(
        _.description("Size of the transaction in bytes")
      )

  implicit val validator: Validator[UTransactionInfo] = Validator.derive

  def apply(
    tx: UTransaction,
    ins: List[ExtendedUInput],
    dataIns: List[ExtendedUDataInput],
    outs: List[UOutput],
    assets: List[UAsset]
  ): UTransactionInfo = {
    val inputsInfo     = UInputInfo.batch(ins)
    val dataInputsInfo = UDataInputInfo.batch(dataIns)
    val outputsInfo    = UOutputInfo.batch(outs, assets)
    new UTransactionInfo(tx.id, inputsInfo, dataInputsInfo, outputsInfo, tx.creationTimestamp, tx.size)
  }

  def batch(
    txs: List[UTransaction],
    ins: List[ExtendedUInput],
    dataIns: List[ExtendedUDataInput],
    outs: List[UOutput],
    assets: List[UAsset]
  ): List[UTransactionInfo] = {
    val assetsByBox    = assets.groupBy(_.boxId)
    val inputsByTx     = ins.groupBy(_.input.txId)
    val dataInputsByTx = dataIns.groupBy(_.input.txId)
    val outsByTx       = outs.groupBy(_.txId)
    txs
      .traverse { tx =>
        for {
          inputs <- inputsByTx.get(tx.id)
          dataInputs = dataInputsByTx.get(tx.id).toList.flatten
          outputs <- outsByTx.get(tx.id).map(_.sortBy(_.index))
          assets = outputs
                     .foldLeft(List.empty[UAsset]) { (acc, o) =>
                       assetsByBox.get(o.boxId).toList.flatten ++ acc
                     }
        } yield apply(tx, inputs, dataInputs, outputs, assets)
      }
      .toList
      .flatten
  }
}
