package org.ergoplatform.explorer.db.repositories

import cats.effect.Sync
import cats.syntax.option._
import doobie.free.connection.ConnectionIO
import org.ergoplatform.explorer.db.algebra.LiftConnectionIO
import org.ergoplatform.explorer.db.syntax.runConnectionIO._
import org.ergoplatform.explorer.db.{RealDbTest, repositories}
import org.scalatest.{Matchers, PropSpec}
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

class OutputRepoSpec
  extends PropSpec
  with Matchers
  with RealDbTest
  with ScalaCheckDrivenPropertyChecks {

  import org.ergoplatform.explorer.commonGenerators._
  import org.ergoplatform.explorer.db.models.generators._

  property("insert/getByBoxId") {
    withLiveRepos[ConnectionIO] { (hRepo, txRepo, oRepo) =>
      forSingleInstance(extOutputsWithTxWithHeaderGen(mainChain = true)) {
        case (header, tx, outputs) =>
          hRepo.insert(header).runWithIO()
          txRepo.insert(tx).runWithIO()
          outputs.foreach { extOut =>
            oRepo.getByBoxId(extOut.output.boxId).runWithIO() shouldBe None
            oRepo.insert(extOut.output).runWithIO()
            oRepo.getByBoxId(extOut.output.boxId).runWithIO() shouldBe Some(extOut)
          }
      }
    }
  }

  property("getAllByAddress/getAllByErgoTree") {
    withLiveRepos[ConnectionIO] { (hRepo, txRepo, oRepo) =>
      forSingleInstance(hexStringRGen.flatMap(hex => addressGen.map(_ -> hex))) {
        case (address, ergoTree) =>
          forSingleInstance(extOutputsWithTxWithHeaderGen(mainChain = true)) {
            case (header, tx, outputs) =>
              hRepo.insert(header).runWithIO()
              txRepo.insert(tx).runWithIO()
              val nonMatching = outputs.head
              val matching = outputs.tail
                .map { extOut =>
                  extOut.copy(
                    output = extOut.output.copy(addressOpt = address.some, ergoTree = ergoTree)
                  )
                }
              matching.foreach { extOut =>
                oRepo.insert(extOut.output).runWithIO()
              }
              oRepo.insert(nonMatching.output).runWithIO()
              oRepo
                .getByErgoTree(ergoTree, 0, Int.MaxValue)
                .compile
                .toList
                .runWithIO() should contain theSameElementsAs matching
          }
      }
    }
  }

  private def withLiveRepos[D[_]: LiftConnectionIO: Sync](
    body: (
      HeaderRepo[D],
      TransactionRepo[D, fs2.Stream],
      OutputRepo[D, fs2.Stream]
    ) => Any
  ): Any = {
    val headerRepo = repositories.HeaderRepo[D]
    val txRepo     = repositories.TransactionRepo[D]
    val outRepo    = repositories.OutputRepo[D]
    body(headerRepo, txRepo, outRepo)
  }
}
