package sample.sharding.goran.persistent

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence._
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor._
import sample.sharding.goran.persistent.traits.{LeanPersistAndHibernateTrait, PubSubTrait}
import ExamplePersistentActor._

import scala.concurrent.duration._

/**
  * Example code from
  * https://doc.akka.io/docs/akka/current/scala/persistence.html
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 11/11/2017
  */

object ExamplePersistentActor {

  case class Cmd(deviceId: Int, data: String)
  case class Increment(deviceId: Int, word1: String, word2: String)
  case class Add(deviceId: Int, num1: Int, num2: Int)

  case class Evt(data: String)

  case class ExampleState(events: List[String] = Nil) {
    def updated(evt: Evt): ExampleState = copy(evt.data :: events)

    def size: Int = events.length

    override def toString: String = events.reverse.toString
  }

  case object IncrementSubscription extends Subscription

}

class ExamplePersistentActor(myRouter: ActorRef) extends PersistentActor with LeanPersistAndHibernateTrait with PubSubTrait with ActorLogging {

  // Abstract members from PubSubTrait
  def fromRouter = null
  def fromName =  context.self.path.name

  // Abstract members from LeanPersistAndHibernateTrait
  override val hibernatingTimeout = 1 minute // 1 minute == Default value
  var state = ExampleState()
  override def persistenceId = context.self.path.name // == Default value

  def updateState(event: Evt): Unit =
    state = state.updated(event)

  val receiveRecover: Receive = {
    case evt: Evt => {
      println(s"Event received, persistenceId: ${persistenceId}, data: ${evt}")
      updateState(evt)
    }
    case SnapshotOffer(_, snapshot: ExampleState) => {
      println(s"Snapshot received, persistenceId: ${persistenceId}, data: ${snapshot}")
      state = snapshot
    }
  }

  override def receiveCommand: Receive = pubSubReceiveCommand orElse examplePersistentReceiveCommand

  def examplePersistentReceiveCommand: Receive = {
    case increment@ Increment(deviceId, word1, word2) =>
      persist(Evt(s"${word1}-${state.size}")) { event =>
        val child = context.child(word1).getOrElse(context.actorOf(Props[ExampleChildPersistentActor], word1))
        //pubSubChild ! NotifySubscribers(IncrementSubscription, increment)
        notifySubscribers(IncrementSubscription, increment)
        println(s"Number of children: ${context.children.toList.size}")
        child ! word2
        updateState(event)
      }

    case "print" => println(state)

    case Add(deviceId, num1, num2) => {
      sender ! num1+num2
    }
    case SubscriptionEventOccurred(_, subscription, mess) => log.debug(s"SubscriptionEventOccurred: $subscription $mess")
  }

   //Subscribe to actor 1 iff I am actor 2
    override def preStart(): Unit = {
      if (persistenceId == "2") {
        log.debug(s"START TRYING TO SEND Subscribe PersistenceID${persistenceId}")
        subscribe(myRouter, "1", IncrementSubscription)
      }
      super.preStart()
    }
}