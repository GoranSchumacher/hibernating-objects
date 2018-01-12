package sample.sharding.goran.persistent

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.cluster.sharding.{ClusterSharding, ClusterShardingSettings, ShardRegion}
import sample.sharding.goran.persistent.ArticlePersistentActor._
import sample.sharding.goran.persistent.ExamplePersistentActor.{Add, Increment}
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor.Subscribe

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

}
class Articles extends Actor with ActorLogging{
  private val extractEntityId: ShardRegion.ExtractEntityId = {
    //case MessageWrapper(name, msg) => (name, msg)
    // Sending the complete wrapper
    case wrapper@ MessageWrapper(name, msg) => (name, wrapper)
  }

  private val numberOfShards = 100

  private val extractShardId: ShardRegion.ExtractShardId = {
    case MessageWrapper(name, msg) => (name.hashCode % numberOfShards).toString
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
  context.system.scheduler.schedule(10.seconds, 1 second, self, Increment)

//  val randomArticles = "123 456 789 987 654 321".split(" ").toList
//  def randomArticle = randomArticles(random.nextInt(randomArticles.size))
  val randomArticles = 1 to 1000
  def randomArticle = randomArticles(random.nextInt(randomArticles.size)).toString

  val randomYears = "17 18 19 20 21 22".split(" ").toList
  def randomYear = randomYears(random.nextInt(randomYears.size))

  val randomMonths = 1 to 12
  def randomMonth = randomMonths(random.nextInt(randomMonths.size))

  val randomDays = 1 to 28
  def randomDay = randomDays(random.nextInt(randomDays.size))

  def randomAmount = random.nextInt(19)+1

  def randomID = random.nextInt(100)

  def receive = {
    case Increment => {
      val message = random.nextInt(4) match {
        case 0 => AddCustomerOrder(CustomerOrder(randomID.toString, new JustDate(randomYear.toInt, randomMonth, randomDay), randomAmount))
        case 1 => AddPurchaseOrder(PurchaseOrder(randomID.toString, new JustDate(randomYear.toInt, randomMonth, randomDay), randomAmount))
        case 2 => AddCustomerOrderFinal(CustomerOrder(randomID.toString, new JustDate(randomYear.toInt, randomMonth, randomDay), randomAmount))
        case 3 => AddPurchaseOrderFinal(PurchaseOrder(randomID.toString, new JustDate(randomYear.toInt, randomMonth, randomDay), randomAmount))
      }
      deviceRegion ! MessageWrapper(randomArticle, message)

      import akka.pattern.ask
      implicit val timeout = akka.util.Timeout(30 seconds)
      (deviceRegion ask MessageWrapper(randomArticle, GetStockPlan)).onComplete{
        //case t: Try[Any] => log.debug(s"GetStockPlan: $t")
        case Success(result) => log.debug(s"GetStockPlan: $result")
        case Failure(t) => log.debug(s"$t")
      }
    }

    case sub@ Subscribe(_, to, _) => {
      log.debug(s"Subscribe received from ${sender()} Message: $sub")
      deviceRegion ! sub
    }
    case mess@ MessageWrapper(_, _) => deviceRegion forward mess
    case mess@ _ => log.debug(s"UNKNOWN MESSAGE: $mess")
  }
}
