package sample.sharding.goran.persistent

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 11/11/2017
  */

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.util.Random
import akka.actor._
import akka.cluster.sharding._
import akka.util.Timeout
import sample.sharding.goran.persistent.ExamplePersistentActor.Increment
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor.Subscribe

object Examples {
  // Update a random device
  case object UpdateExample

  def examplePersistentActorProps(myRouter: ActorRef) = Props(classOf[ExamplePersistentActor], myRouter)
}

class Examples extends Actor with ActorLogging {
  import ExamplePersistentActor._

  private val extractEntityId: ShardRegion.ExtractEntityId = {
    case msg @ ExamplePersistentActor.Increment(id, _, _) => (id.toString, msg)
    case msg @ ExamplePersistentActor.Add(id, _, _) => (id.toString, msg)
    case msg@ Subscribe(_, to, _) => (to.entityName, msg)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case ExamplePersistentActor.Increment(id, _, _) => (id % numberOfShards).toString
    case ExamplePersistentActor.Add(id, _, _) => (id % numberOfShards).toString
    case Subscribe(_, to, _) => (to.entityName.toInt % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    //case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
  }

  val deviceRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = "ExamplePersistentActor",
    entityProps = Examples.examplePersistentActorProps(self),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  val random = new Random()
  val numberOfDevices = 50

  implicit val ec: ExecutionContext = context.dispatcher
  context.system.scheduler.schedule(10.seconds, 1 second, self, Increment)

  val randomEANs = "VARIANT_1 VARIANT_2 VARIANT_3 VARIANT_4 VARIANT_5 VARIANT_6 VARIANT_6".split(" ").toList
  def randomEAN = randomEANs(random.nextInt(randomEANs.size))

  val randomWords = "Hi there HDC Galeria Kaufhof how are you today soon it is Christmas!".split(" ").toList
  def randomWord = randomWords(random.nextInt(randomWords.size))

  def receive = {
    case Increment => {
      val deviceId = random.nextInt(numberOfDevices)
      val msg = ExamplePersistentActor.Increment(deviceId, randomEAN, randomWord)
      log.info(s"Examples Sending $msg");
      deviceRegion ! msg
    }

    case msg@ Add(id, _, _) => {
      val deviceId = id
      log.info(s"Sending $msg");
      import akka.pattern.ask
      implicit val timeout = Timeout(5 seconds)
      deviceRegion forward msg
    }

    case sub@ Subscribe(_, to, _) => {
      log.debug(s"Subscribe received from ${sender()} Message: $sub")
      deviceRegion ! sub
    }
    case mess@ _ => log.debug(s"UNKNOWN MESSAGE: $mess")
  }
}
