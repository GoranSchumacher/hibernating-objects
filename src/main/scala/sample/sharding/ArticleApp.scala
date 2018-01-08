package sample.sharding

import akka.actor.{ActorSystem, Props}
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import sample.sharding.goran.persistent.ArticlePersistentActor._
import sample.sharding.goran.persistent.{Articles, Examples}

import scala.util.{Failure, Success}

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 31/12/2017
  */
object ArticleApp {
  def main(args: Array[String]): Unit = {
    if (args.isEmpty)
      startup(Seq("2551", "2552", "0"))
    else
      startup(args)
  }

  def startup(ports: Seq[String]): Unit = {
    // In a production application you wouldn't typically start multiple ActorSystem instances in the
    // same JVM, here we do it to easily demonstrate these ActorSytems (which would be in separate JVM's)
    // talking to each other.
    ports foreach { port =>
      // Override the configuration of the port
      val config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load())

      // Create an Akka system
      val system = ActorSystem("ShardingSystem", config)
      // Create an actor that starts the sharding and sends random messages
      //system.actorOf(Props[Devices])
      val articlesRouter = system.actorOf(Props[Articles], "articlesRouter")
      }
    }
}
