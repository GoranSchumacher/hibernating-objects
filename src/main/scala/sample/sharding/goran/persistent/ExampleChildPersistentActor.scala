package sample.sharding.goran.persistent

import akka.actor.Props
import akka.persistence._
import sample.sharding.goran.persistent.ExamplePersistentActor.{Evt, ExampleState, Increment}
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor.{Subscribe, Unsubscribe}
import sample.sharding.goran.persistent.traits.{LeanPersistAndHibernateTrait, PubSubTrait}

import scala.concurrent.duration._

/**
  * Example code from
  * https://doc.akka.io/docs/akka/current/scala/persistence.html
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 11/11/2017
  */

class ExampleChildPersistentActor extends PersistentActor with LeanPersistAndHibernateTrait with PubSubTrait {

  // Abstract members from PubSubTrait
  def fromRouter = null
  def fromName =  context.self.path.name

  // Abstract members from LeanPersistAndHibernateTrait
  //setTimeout(1 minute)
  var state = ExampleState()

  override def persistenceId = context.self.path.name

  lazy val examplesRouter = context.actorSelection("/user/examplesRouter").resolveOne(5 seconds)

  def updateState(event: Evt): Unit =
    state = state.updated(event)

  def numEvents =
    state.size


  val receiveRecover: Receive = {
    case evt: Evt => {
      println(s"CHILD Event received, persistenceId: ${persistenceId}, data: ${evt}")
      updateState(evt)
    }
    case SnapshotOffer(_, snapshot: ExampleState) => {
      println(s"CHILD Snapshot received, persistenceId: ${persistenceId}, data: ${snapshot}")
      state = snapshot
    }
  }

  override def receiveCommand = pubSubReceiveCommand orElse exampleChildReceiveCommand

  def exampleChildReceiveCommand: Receive = {
    case data: String =>
      persist(Evt(s"${data}-${numEvents}")) { event =>
        updateState(event)
      }

    case "print" => println(state)
  }

//  ///////// PubSub ////////////
//  lazy val pubSubChild = context.child("PubSubChildActor").getOrElse(context.actorOf(Props[PubSubPersistentActor], "PubSubChildActor"))
//  def pubSubReceiveCommand: Receive = {
//    case s: Subscribe => pubSubChild forward s
//    case s: Unsubscribe => pubSubChild forward s
//  }

}