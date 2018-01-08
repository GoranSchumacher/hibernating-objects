package sample.sharding

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 24/12/2017
  */
object TestApp {
    def main(args: Array[String]): Unit = {
      import akka.actor.ActorSystem
      import akka.util.Timeout
      import sample.sharding.goran.persistent.ExamplePersistentActor.{Add, Increment}

      import scala.concurrent.{Await, Future}
      import scala.concurrent.ExecutionContext.Implicits.global

      val system = ActorSystem("ShardingSystem")
      val parentActor = system.actorSelection("akka.tcp://ShardingSystem@127.0.0.1:2551/user/examplesRouter")

      import akka.pattern.ask
      import scala.concurrent.duration._
      implicit val timeout = Timeout(15 seconds)

      //parentActor ! Increment
      val fut:Future[Any] = parentActor ? Add(1, 3, 4)
      fut.map(a=>println(s"Result: $a"))

      //(parentActor ? Add(2, 2, 3)).map(a=>println(s"Result(2+3): $a"))
      //(parentActor ? Add(3, 3, 8)).map(a=>println(s"Result(3+8): $a"))
      //(parentActor ? Add(4, 7, 9)).map(a=>println(s"Result(7+9): $a"))
      //(parentActor ? Add(5, 5, 12)).map(a=>println(s"Result(5+12): $a"))


      val fut1:Future[Int] = (parentActor ? Add(2, 2, 3)).asInstanceOf[Future[Int]]
      val fut2:Future[Int] = (parentActor ? Add(3, 3, 8)).asInstanceOf[Future[Int]]
      val fut3:Future[Int] = (parentActor ? Add(4, 7, 9)).asInstanceOf[Future[Int]]
      val fut4:Future[Int] = (parentActor ? Add(5, 5, 12)).asInstanceOf[Future[Int]]

      val res =
        for {
          res1 <- fut1
          res2 <- fut2
          res3 <- fut3
          res4 <- fut4
          result = res1+res2+res3+res4
        } yield result

      println(s"Total Result is: ${Await.result(res, atMost = 25 seconds)}")

    }
}
