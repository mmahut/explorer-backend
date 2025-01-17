package org.ergoplatform.explorer.http.api.v0.models

import io.circe.Codec
import io.circe.generic.semiauto.deriveCodec
import org.ergoplatform.explorer.TokenId
import org.ergoplatform.explorer.db.models.Asset
import org.ergoplatform.explorer.db.models.aggregates.ExtendedOutput
import sttp.tapir.{Schema, Validator}
import sttp.tapir.generic.Derived

final case class DexBuyOrderInfo(
  outputInfo: OutputInfo,
  tokenId: TokenId,
  tokenAmount: Long
)

object DexBuyOrderInfo {

  implicit val codec: Codec[DexBuyOrderInfo] = deriveCodec

  implicit val schema: Schema[DexBuyOrderInfo] = Schema.derive

  implicit val validator: Validator[DexBuyOrderInfo] = Validator.derive

  def apply(
    output: ExtendedOutput,
    tokenId: TokenId,
    tokenAmount: Long,
    assets: List[Asset]
  ): DexBuyOrderInfo =
    new DexBuyOrderInfo(OutputInfo(output, assets), tokenId, tokenAmount)
}
