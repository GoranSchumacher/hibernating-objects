package sample.sharding.goran.persistent.childutils

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{PersistentActor, RecoveryCompleted, SnapshotOffer}
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor._
import sample.sharding.goran.persistent.traits.LeanPersistAndHibernateTrait

import scala.concurrent.duration._

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 26/12/2017
  */
object PubSubPersistentActor {
  case class Subscribe(fromRouter: EntityRef, toRouter: EntityRef, subscription: Subscription)
  case class Unsubscribe(fromRouter: EntityRef, toRouter: EntityRef, subscription: Subscription)

  class Subscription()
  case object Allsubscriptions extends Subscription

  // Sent from Entity to it's child PubSubPersistentActor,
  // here it will be  converted to a SubscriptionEventOccurred sent to subscriber Routers.
  case class NotifySubscribers(subscription: Subscription, message: Any)
  case class SubscriptionEventOccurred(entityName: String, subscription: Subscription, message: Any)

  case class  EntityRef(routerRef: ActorRef, entityName: String)
}

class PubSubPersistentActor extends LeanPersistAndHibernateTrait with ActorLogging{

  type StateType = Map[EntityRef, Set[String]]
  case class State(subscribers: StateType= Map[EntityRef, Set[String]]())

  var state: State=State()

  // Abstract members from LeanPersistAndHibernateTrait
  override def persistenceId = "PubSubChildActor"

  def receiveRecover: Receive = receiveRecoverLocal orElse pubSubPersistentreceiveCommand

  def receiveRecoverLocal: Receive = {
    case SnapshotOffer(_, snapshot: State) => {
      println(s"PubSubChildActor Snapshot received, persistenceId: ${persistenceId}, data: ${snapshot}")
      state = snapshot
    }
  }

  override def receiveCommand = super.receiveCommand orElse pubSubPersistentreceiveCommand

  def pubSubPersistentreceiveCommand: Receive = {
    // Be aware that recover operations also is performed here
    // If your method has side-effects => Make shure these are not performed during recover using;
    // the method: recoveryRunning
    case  Subscribe(fromRouter, _, subscription) => {
      val SubscriptionclassName = subscription.getClass.getName
      log.debug(s"Subscribe received, Sender: $sender(), ${SubscriptionclassName}")
      log.debug(s"State: $state")
      if(!state.subscribers.contains(fromRouter))
        state = State(state.subscribers + (fromRouter -> Set(SubscriptionclassName)))
      else if(!state.subscribers(fromRouter).contains(SubscriptionclassName)) {
          state = State(state.subscribers + (fromRouter -> (state.subscribers(fromRouter) + SubscriptionclassName)))
        }

      log.debug(s"New State: $state")
      //TODO Reply, except when recovering
    }
    case Unsubscribe(fromRouter, _, subscription)  => {
      val SubscriptionclassName = subscription.getClass.getName
      if(state.subscribers.contains(fromRouter))
        if(state.subscribers(fromRouter).contains(SubscriptionclassName))
          if(state.subscribers(fromRouter).size==1)
            state=State(state.subscribers-(fromRouter))
          else
            state=State(state.subscribers + (fromRouter -> state.subscribers(fromRouter).-(SubscriptionclassName)))
      //TODO Reply, except when recovering
    }
    case NotifySubscribers(subscription, message) =>
      filteredState(subscription).map{a=>
        log.debug(s"NotifySubscribers: $a-_1")
        a._1.routerRef ! SubscriptionEventOccurred(a._1.entityName, subscription, message)
      }

  }

  private def filteredState(wantedSubscription: Subscription): StateType = {
    state.subscribers.filter(_._2.contains(wantedSubscription.getClass.getName ) || (wantedSubscription==Allsubscriptions))
  }

}
