package org.ergoplatform.explorer.http.api.v0.models

import io.circe.{Codec, Decoder, Encoder, Json}
import io.circe.generic.semiauto.{deriveCodec, deriveDecoder}
import io.circe.syntax._
import org.ergoplatform.explorer.{Id, TxId}
import org.ergoplatform.explorer.db.models.aggregates.{ExtendedDataInput, ExtendedInput, ExtendedOutput}
import org.ergoplatform.explorer.db.models.{Asset, Transaction}
import org.ergoplatform.explorer.http.api.v0.models.TransactionSummary.MiniBlockInfo
import org.ergoplatform.explorer.protocol.constants
import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.Derived

final case class TransactionSummary(
  id: TxId,
  miniBlockInfo: MiniBlockInfo,
  timestamp: Long,
  index: Int,
  confirmationsCount: Int,
  inputs: List[InputInfo],
  dataInputs: List[DataInputInfo],
  outputs: List[OutputInfo],
  size: Int,
  ioSummary: TxStats
)

object TransactionSummary {

  final case class MiniBlockInfo(id: Id, height: Int)

  implicit val decoder: Decoder[TransactionSummary] = deriveDecoder

  implicit val encoder: Encoder[TransactionSummary] = { ts =>
    Json.obj(
      "summary" -> Json.obj(
        "id"                 -> ts.id.asJson,
        "timestamp"          -> ts.timestamp.asJson,
        "index"              -> ts.index.asJson,
        "size"               -> ts.size.asJson,
        "confirmationsCount" -> ts.confirmationsCount.asJson,
        "block"              -> ts.miniBlockInfo.asJson
      ),
      "ioSummary"  -> ts.ioSummary.asJson,
      "inputs"     -> ts.inputs.asJson,
      "dataInputs" -> ts.dataInputs.asJson,
      "outputs"    -> ts.outputs.asJson
    )
  }

  implicit val codec: Codec[TransactionSummary] = Codec.from(decoder, encoder)

  implicit val codecBlockInfo: Codec[MiniBlockInfo] = deriveCodec

  implicit val schemaBlockInfo: Schema[MiniBlockInfo] =
    Schema
      .derive[MiniBlockInfo]
      .modify(_.id)(_.description("Block ID"))
      .modify(_.height)(_.description("Block height"))

  implicit val validatorBlockInfo: Validator[MiniBlockInfo] = Validator.derive

  implicit val schema: Schema[TransactionSummary] =
    Schema
      .derive[TransactionSummary]
      .modify(_.id)(_.description("Transaction ID"))
      .modify(_.timestamp)(_.description("Timestamp the transaction got into the network"))
      .modify(_.index)(_.description("Index of a transaction inside a block"))
      .modify(_.confirmationsCount)(_.description("Number of transaction confirmations"))
      .modify(_.size)(_.description("Size of transaction in bytes"))

  implicit val validator: Validator[TransactionSummary] = Validator.derive

  def apply(
    tx: Transaction,
    numConfirmations: Int,
    inputs: List[ExtendedInput],
    dataInputs: List[ExtendedDataInput],
    outputs: List[ExtendedOutput],
    assets: List[Asset]
  ): TransactionSummary = {
    val ins     = InputInfo.batch(inputs)
    val dataIns = DataInputInfo.batch(dataInputs)
    val outs    = OutputInfo.batch(outputs, assets)
    apply(tx, numConfirmations, ins, dataIns, outs)
  }

  private def apply(
    tx: Transaction,
    numConfirmations: Int,
    inputs: List[InputInfo],
    dataInputs: List[DataInputInfo],
    outputs: List[OutputInfo]
  ): TransactionSummary = {
    val stats     = TxStats(tx, inputs, outputs)
    val blockInfo = MiniBlockInfo(tx.headerId, tx.inclusionHeight)
    apply(
      tx.id,
      blockInfo,
      tx.timestamp,
      tx.index,
      numConfirmations,
      inputs,
      dataInputs,
      outputs,
      tx.size,
      stats
    )
  }
}
