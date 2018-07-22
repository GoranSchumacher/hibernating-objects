package sample.sharding.goran.persistent

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import sample.sharding.goran.persistent.ArticlePersistentActor._
import sample.sharding.goran.persistent.ExamplePersistentActor.{Add, Increment}
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor.Subscribe

import scala.collection.BitSet
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Random, Success, Try}
import scala.concurrent.duration._

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 31/12/2017
  */
object Articles {
  case object UpdateExample

  def articlePersistentActorProps(myRouter: ActorRef) = Props(classOf[ArticlePersistentActor], myRouter)

  case class ActorsState(actorBitSet:BitSet = BitSet.empty, activeActors: Int=0) {
    def addActor(actorStarted: ActorStarted) = {
      if(!actorBitSet.contains(actorStarted.nameAsInt))
        this.copy(actorBitSet =
        actorBitSet + actorStarted.nameAsInt,
          activeActors = activeActors +1
        )
      else
        this
    }
    def removeActor(actorHibernating: ActorHibernating) = {
      if(actorBitSet.contains(actorHibernating.nameAsInt))
        this.copy(actorBitSet =
        actorBitSet - actorHibernating.nameAsInt,
          activeActors = activeActors -1
        )
      else
        this
    }
  }

}
class Articles extends Actor with ActorLogging{
  private val extractEntityId: ShardRegion.ExtractEntityId = {
    //case MessageWrapper(name, msg) => (name, msg)
    // Sending the complete wrapper
    case wrapper@ EntityWrapper(name, msg) => (name, wrapper)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case EntityWrapper(name, msg) => (name.hashCode % numberOfShards).toString
    // Needed if you want to use 'remember entities':
    //case ShardRegion.StartEntity(id) => (id.toLong % numberOfShards).toString
  }

  val deviceRegion: ActorRef = ClusterSharding(context.system).start(
    typeName = "ArticlePersistentActor",
    entityProps = Articles.articlePersistentActorProps(self),
    settings = ClusterShardingSettings(context.system),
    extractEntityId = extractEntityId,
    extractShardId = extractShardId)

  val random = new Random()
  val numberOfDevices = 50

  implicit val ec: ExecutionContext = context.dispatcher
  context.system.scheduler.schedule(10.second, 100 milliseconds, self, Increment)
  //context.system.scheduler.scheduleOnce(10.seconds, self, Increment)

  context.system.scheduler.schedule(1 minute, 1 minute, self, "ActorStatus")

  def randomArticle = random.nextInt(1000000).toString

  def randomYear = random.nextInt(6)+17

  def randomMonth = random.nextInt(12)+1

  def randomDay = random.nextInt(28)+1

  def randomAmount = random.nextInt(19)+1

  def randomID = random.nextInt(1000000)

  var count1000: Int=0
  var count1000Start: Long=0
  def receive = {
    case Increment => {
      if(count1000<1) {
        count1000=1000
        count1000Start=System.currentTimeMillis()
      }
      for (i <- 1 to 10){
        callArticle(randomArticle)
        //callArticle(newRandomArticle)
      }
    }

    case sub@ Subscribe(_, to, _) => {
      log.debug(s"Subscribe received from ${sender()} Message: $sub")
      deviceRegion ! sub
    }
    case mess@ EntityWrapper(_, _) => deviceRegion forward mess

    case started: ActorStarted =>
      actorStatusState = actorStatusState.addActor(started)
    case hibernating: ActorHibernating =>
      actorStatusState = actorStatusState.removeActor(hibernating)
    case "ActorStatus" => log.info(f"Actor State, Active actors ${actorStatusState.activeActors}%,.0f")


    case mess@ _ => log.debug(s"UNKNOWN MESSAGE: $mess")
  }

  var actorStatusState: Articles.ActorsState = Articles.ActorsState()

  private def callArticle(newRandomArticle: String) = {
    val now = System.currentTimeMillis()
    val message = random.nextInt(3) match {
      case 0 => AddCustomerOrder(CustomerOrder(randomID.toString, new JustDate(randomYear, randomMonth, randomDay), randomAmount))
      case 1 => AddPurchaseOrder(PurchaseOrder(randomID.toString, new JustDate(randomYear, randomMonth, randomDay), randomAmount))
      case 2 => AddCustomerOrderFinal(CustomerOrder(randomID.toString, new JustDate(randomYear, randomMonth, randomDay), randomAmount))
      case 3 => AddPurchaseOrderFinal(PurchaseOrder(randomID.toString, new JustDate(randomYear, randomMonth, randomDay), randomAmount))
    }
    log.debug(s"New Message: Article: $newRandomArticle, Message: $message")
    deviceRegion ! EntityWrapper(newRandomArticle, message)

    import akka.pattern.ask
    implicit val timeout = akka.util.Timeout(30 seconds)
    (deviceRegion ask EntityWrapper(newRandomArticle, GetStockPlan)).onComplete {
      //case t: Try[Any] => log.debug(s"GetStockPlan: $t")
      case Success(result) => {
        log.info(s"GetStockPlan: $newRandomArticle-$result, Duration: ${System.currentTimeMillis() - now}")
        count1000 = count1000-1
        if(count1000==0)
          log.info(s"Count 1000 took: ${System.currentTimeMillis() - count1000Start}")
      }
      case Failure(t) => log.debug(s"$t")
    }
  }
}
