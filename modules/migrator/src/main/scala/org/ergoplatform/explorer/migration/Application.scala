package org.ergoplatform.explorer.migration

import cats.effect.{ExitCode, IO, IOApp, Resource}
import doobie.util.transactor.Transactor
import org.ergoplatform.explorer.db.DoobieTrans
import org.ergoplatform.explorer.migration.migrations.RegistersMigration
import pureconfig.generic.auto._

import scala.concurrent.duration._

object Application extends IOApp {

  def run(args: List[String]): IO[ExitCode] =
    resources(args.headOption).use {
      case (xa, conf) =>
        val migrations = makeMigrations(conf.offset, xa)
        migrations(conf.migrationId)
          .as(ExitCode.Success)
    }

  private def resources(configPathOpt: Option[String]) =
    for {
      settings <- Resource.liftF(MigrationConfig.load[IO](configPathOpt))
      xa       <- DoobieTrans[IO]("Migrator", settings.db)
    } yield xa -> settings

  private def makeMigrations(offset: Int, xa: Transactor[IO]) =
    Map("v4v5" -> RegistersMigration(RegistersMigrationConfig(batchSize = 1000, interval = 500.millis, offset), xa))
}
