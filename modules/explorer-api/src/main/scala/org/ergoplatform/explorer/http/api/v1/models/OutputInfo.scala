package org.ergoplatform.explorer.http.api.v1.models

import io.circe.{Codec, Json}
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.{Address, BoxId, HexString, TxId}
import org.ergoplatform.explorer.db.models.{Asset, Output}
import org.ergoplatform.explorer.db.models.aggregates.ExtendedOutput
import org.ergoplatform.explorer.http.api.models.AssetInfo
import sttp.tapir.{Schema, SchemaType, Validator}
import sttp.tapir.json.circe.validatorForCirceJson

final case class OutputInfo(
  boxId: BoxId,
  transactionId: TxId,
  value: Long,
  index: Int,
  creationHeight: Int,
  ergoTree: HexString,
  address: Option[Address],
  assets: List[AssetInfo],
  additionalRegisters: Json,
  spentTransactionId: Option[TxId],
  mainChain: Boolean
)

object OutputInfo {

  implicit val codec: Codec[OutputInfo] = deriveCodec

  implicit val schema: Schema[OutputInfo] =
    Schema
      .derive[OutputInfo]
      .modify(_.boxId)(_.description("Id of the box"))
      .modify(_.transactionId)(_.description("Id of the transaction that created the box"))
      .modify(_.value)(_.description("Value of the box in nanoERG"))
      .modify(_.index)(_.description("Index of the output in a transaction"))
      .modify(_.creationHeight)(_.description("Height at which the box was created"))
      .modify(_.ergoTree)(_.description("Serialized ergo tree"))
      .modify(_.address)(_.description("An address derived from ergo tree"))
      .modify(_.spentTransactionId)(_.description("Id of the transaction this output was spent by"))

  implicit val validator: Validator[OutputInfo] = Validator.derive

  implicit private def registersSchema: Schema[Json] =
    Schema(
      SchemaType.SOpenProduct(
        SchemaType.SObjectInfo("AdditionalRegisters"),
        Schema(SchemaType.SString)
      )
    )

  def apply(
    o: ExtendedOutput,
    assets: List[Asset]
  ): OutputInfo =
    OutputInfo(
      o.output.boxId,
      o.output.txId,
      o.output.value,
      o.output.index,
      o.output.creationHeight,
      o.output.ergoTree,
      o.output.addressOpt,
      assets.sortBy(_.index).map(x => AssetInfo(x.tokenId, x.index, x.amount)),
      o.output.additionalRegisters,
      o.spentByOpt,
      o.output.mainChain
    )

  def unspent(
    o: Output,
    assets: List[Asset]
  ): OutputInfo =
    OutputInfo(
      o.boxId,
      o.txId,
      o.value,
      o.index,
      o.creationHeight,
      o.ergoTree,
      o.addressOpt,
      assets.sortBy(_.index).map(x => AssetInfo(x.tokenId, x.index, x.amount)),
      o.additionalRegisters,
      None,
      o.mainChain
    )

  def batch(outputs: List[ExtendedOutput], assets: List[Asset]): List[OutputInfo] = {
    val groupedAssets = assets.groupBy(_.boxId)
    outputs
      .sortBy(_.output.index)
      .map(out => OutputInfo(out, groupedAssets.get(out.output.boxId).toList.flatten))
  }
}
