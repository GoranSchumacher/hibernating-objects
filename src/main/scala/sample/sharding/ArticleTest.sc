import akka.actor.ActorSystem
import akka.util.Timeout
import sample.sharding.goran.persistent.ArticlePersistentActor._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

val system = ActorSystem("ShardingSystem")
val articlesRouter = system.actorSelection("akka.tcp://ShardingSystem@127.0.0.1:2551/user/articlesRouter")

import akka.pattern.ask
import scala.concurrent.duration._
implicit val timeout = Timeout(5 seconds)

articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1", new JustDate(2018, 1, 20), 100)))
articlesRouter ! MessageWrapper("1", AddCustomerOrder(CustomerOrder("1", new JustDate(2018, 1, 25), 5)))
articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1", new JustDate(2018, 2, 1), 15)))
articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1", new JustDate(2018, 2, 20), 25)))
articlesRouter ! MessageWrapper("1", AddCustomerOrder(CustomerOrder("1", new JustDate(2018, 2, 25), 8)))
//(articlesRouter ask MessageWrapper("1", GetStockPlan)).map{a=>println("Result:" + a)}

val future = (articlesRouter ask MessageWrapper("1", GetStockPlan))
  Thread.sleep(5000)
  future.onComplete{
  //case t: Try[Any] => log.debug(s"GetStockPlan: $t")
  case Success(result) => println(s"GetStockPlan: $result")
  case Failure(t) => println(s"$t")
}