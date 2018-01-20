package sample.sharding

import sample.sharding.goran.persistent.ArticlePersistentActor._

import scala.util.{Failure, Success}

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 07/01/2018
  */
object ArticleTestApp {
  def main(args: Array[String]): Unit = {
    import akka.actor.ActorSystem
    import akka.util.Timeout
    import scala.concurrent.ExecutionContext.Implicits.global

    val system = ActorSystem("ShardingSystem")
    val articlesRouter = system.actorSelection("akka.tcp://ShardingSystem@127.0.0.1:2551/user/articlesRouter")

    import akka.pattern.ask
    import scala.concurrent.duration._
    implicit val timeout = Timeout(15 seconds)

    val articleId = "1"
    articlesRouter ! EntityWrapper(articleId, AddPurchaseOrder(PurchaseOrder("1001", new JustDate(2018, 1, 20), 100)))
    articlesRouter ! EntityWrapper(articleId, AddCustomerOrder(CustomerOrder("2001", new JustDate(2018, 1, 25), 5)))
    articlesRouter ! EntityWrapper(articleId, AddPurchaseOrder(PurchaseOrder("1002", new JustDate(2018, 2, 1), 15)))
    articlesRouter ! EntityWrapper(articleId, AddPurchaseOrder(PurchaseOrder("1003", new JustDate(2018, 2, 20), 25)))
    articlesRouter ! EntityWrapper(articleId, AddCustomerOrder(CustomerOrder("2002", new JustDate(2018, 2, 25), 8)))
    (articlesRouter ? EntityWrapper(articleId, GetStockPlan)).map{ a=>println("Result1: " + a)}

    articlesRouter ! EntityWrapper(articleId, AddPurchaseOrderFinal(PurchaseOrder("1001", new JustDate(2018, 1, 20), 90)))
    articlesRouter ! EntityWrapper(articleId, AddCustomerOrderFinal(CustomerOrder("2001", new JustDate(2018, 1, 25), 5)))
    (articlesRouter ? EntityWrapper(articleId, GetStockPlan)).map{ a=>println("Result2: " + a)}

  }
}
