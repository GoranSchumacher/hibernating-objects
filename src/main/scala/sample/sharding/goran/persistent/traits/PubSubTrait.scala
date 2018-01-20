package sample.sharding.goran.persistent.traits

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.PersistentActor
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor._


/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 30/12/2017
  */
trait PubSubTrait extends LeanPersistAndHibernateTrait with ActorLogging{

  def fromRouter: ActorRef
  def fromName: String

  lazy val pubSubChild = context.child("PubSubChildActor").getOrElse(context.actorOf(Props[PubSubPersistentActor], "PubSubChildActor"))


  override def receiveCommand = super.receiveCommand orElse pubSubReceiveCommand

  def pubSubReceiveCommand: Receive = {
    case s: Subscribe => pubSubChild forward s
    case s: Unsubscribe => pubSubChild forward s
  }

  def subscribe(toRouter: ActorRef, toName: String, subscription: Subscription): Unit = {
    toRouter ! Subscribe(EntityRef(fromRouter, fromName), EntityRef(toRouter, toName), subscription) //TODO Add Sender() to message
    log.debug(s"TRYING TO SEND Subscribe to ${toRouter}")
  }

  def notifySubscribers(subscription: Subscription, message: Any) = {
    pubSubChild ! NotifySubscribers(subscription, message)
  }
}
