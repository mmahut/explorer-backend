package org.ergoplatform.explorer.db.repositories

import cats.instances.try_._
import fs2.Stream
import org.ergoplatform.explorer.Err.RequestProcessingErr.{
  DexBuyOrderAttributesFailed,
  DexSellOrderAttributesFailed
}
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.models.aggregates.{
  DexBuyOrderOutput,
  DexSellOrderOutput,
  ExtendedOutput
}
import org.ergoplatform.explorer.db.models.schema.ctx._
import org.ergoplatform.explorer.db.quillCodecs._
import org.ergoplatform.explorer.{HexString, TokenId}
import scorex.util.encode.Base16
import sigmastate.Values.ByteArrayConstant
import sigmastate.serialization.ErgoTreeSerializer
import sigmastate.{SLong, Values}

import scala.util.{Failure, Success, Try}

/** [[ExtendedOutput]] for DEX sell/buy orders data access operations.
  */
trait DexOrdersRepo[D[_], S[_[_], _]] {

  /** Get all unspent main-chain DEX sell orders
    */
  def getAllMainUnspentSellOrderByTokenId(
    tokenId: TokenId
  ): S[D, DexSellOrderOutput]

  /** Get all unspent main-chain DEX buy orders
    */
  def getAllMainUnspentBuyOrderByTokenId(
    tokenId: TokenId
  ): S[D, DexBuyOrderOutput]
}

object DexOrdersRepo {

  def apply[D[_]: LiftConnectionIO]: DexOrdersRepo[D, Stream] =
    new Live[D]

  final private class Live[D[_]: LiftConnectionIO] extends DexOrdersRepo[D, Stream] {

    import org.ergoplatform.explorer.db.queries.{DexOrdersQuerySet => QS}

    private val liftK = LiftConnectionIO[D].liftConnectionIOK

    override def getAllMainUnspentSellOrderByTokenId(
      tokenId: TokenId
    ): Stream[D, DexSellOrderOutput] =
      stream(
        QS.getMainUnspentSellOrderByTokenId(
          tokenId,
          sellContractTemplate,
          0,
          Int.MaxValue
        )
      ).map(eOut =>
          DexSellOrderOutput(
            eOut,
            getTokenPriceFromSellOrderTree(eOut.output.ergoTree).get
          )
        )
        .translate(liftK)

    /** Get all unspent main-chain DEX buy orders
      */
    override def getAllMainUnspentBuyOrderByTokenId(
      tokenId: TokenId
    ): Stream[D, DexBuyOrderOutput] =
      stream(
        QS.getMainUnspentBuyOrderByTokenId(tokenId, buyContractTemplate, 0, Int.MaxValue)
      ).map(eOut =>
          DexBuyOrderOutput(eOut, getTokenInfoFromBuyOrderTree(eOut.output.ergoTree).get)
        )
        .translate(liftK)

  }

  val sellContractTemplate: HexString = HexString
    .fromString[Try](
      "eb027300d1eded91b1a57301e6c6b2a5730200040ed801d60193e4c6b2a5730300040ec5a7eded92c1b2a57304007305720193c2b2a5730600d07307"
    )
    .get

  val buyContractTemplate: HexString = HexString
    .fromString[Try](
      "eb027300d1eded91b1a57301e6c6b2a5730200040ed803d601e4c6b2a5730300020c4d0ed602eded91b172017304938cb27201730500017306928cb27201730700027308d60393e4c6b2a5730900040ec5a7eded720293c2b2a5730a00d0730b7203"
    )
    .get

  private val treeSerializer: ErgoTreeSerializer = new ErgoTreeSerializer

  def getTokenPriceFromSellOrderTree(ergoTree: HexString): Try[Long] =
    Base16
      .decode(ergoTree.unwrapped)
      .flatMap { bytes =>
        val tree = treeSerializer.deserializeErgoTree(bytes)
        tree.constants
          .lift(5)
          .collect {
            case Values.ConstantNode(value, SLong) =>
              Success(value.asInstanceOf[Long])
          }
          .getOrElse(
            Failure(
              DexSellOrderAttributesFailed(
                s"Cannot find tokenPrice in sell order ergo tree $ergoTree"
              )
            )
          )
      }

  case class TokenInfo(tokenId: TokenId, amount: Long)

  def getTokenInfoFromBuyOrderTree(ergoTree: HexString): Try[TokenInfo] =
    for {
      bytes <- Base16.decode(ergoTree.unwrapped)
      tree = treeSerializer.deserializeErgoTree(bytes)
      tokenId <- tree.constants
                  .lift(6)
                  .collect {
                    case ByteArrayConstant(coll) =>
                      Success(TokenId(Base16.encode(coll.toArray)))
                  }
                  .getOrElse(
                    Failure(
                      DexBuyOrderAttributesFailed(
                        s"Cannot find tokenId in the buy order ergo tree $ergoTree"
                      )
                    )
                  )
      tokenAmount <- tree.constants
                      .lift(8)
                      .collect {
                        case Values.ConstantNode(value, SLong) =>
                          Success(value.asInstanceOf[Long])
                      }
                      .getOrElse(
                        Failure(
                          DexBuyOrderAttributesFailed(
                            s"Cannot find token amount in the buy order ergo tree $ergoTree"
                          )
                        )
                      )

    } yield TokenInfo(tokenId, tokenAmount)
}
