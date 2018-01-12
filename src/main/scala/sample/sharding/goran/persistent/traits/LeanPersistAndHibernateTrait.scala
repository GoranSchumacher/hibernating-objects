package sample.sharding.goran.persistent.traits

import akka.actor.{ActorLogging, ReceiveTimeout}
import akka.persistence._

import scala.concurrent.duration._

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 13/11/2017
  */
trait LeanPersistAndHibernateTrait extends PersistentActor with ActorLogging {

  // Abstract members (could be over ridden)
  def hibernatingTimeout: Duration = 1 minute
  def state: Any
  def receiveCommandLocal: Receive
  override def persistenceId = context.self.path.name
  def purgeLogs: Boolean = true // When making snapshot it will purge logs from older entries preserving only the most recent snapshot and logs

  var inShutdownMode = false

  context.setReceiveTimeout(hibernatingTimeout)

  def receiveCommand: Receive = persistenceCallbacks orElse receiveCommandLocal


  def persistenceCallbacks: Receive = {
    case ReceiveTimeout => {                                      // Is not called by some reason
      log.debug(s"ReceiveTimeout, persistenceId: ${persistenceId}")
      context.setReceiveTimeout(Duration.Undefined)
      saveSnapshot(state)
      inShutdownMode=true
    }
    case SaveSnapshotSuccess(metadata) => {
      log.debug("successfully saved snapshot {}, Metadata: ", metadata)
      if(purgeLogs)
        deleteSnapshots(SnapshotSelectionCriteria.create(metadata.sequenceNr, metadata.timestamp - 1))
    }
    case SaveSnapshotFailure(metadata, cause: Throwable) => {
      log.debug(s"FAILED saved snapshot ${identity()}, cause: ${cause}")
    }
    case DeleteSnapshotsSuccess(criteria: SnapshotSelectionCriteria) => {
      println(s"successfully deleted snapshot {}, deleting to seq: ${criteria.maxSequenceNr}")
      deleteMessages(criteria.maxSequenceNr)
    }
    case DeleteMessagesSuccess(toSequenceNr) => {
      log.debug(s"successfully deleted messages {}, deleting to seq: ${toSequenceNr}")
      if(inShutdownMode)
        context.stop(self)
    }
  }

  override def postStop(): Unit = {
    log.debug(s"postStop called! id: ${persistenceId}. Path: ${self.path}")
    saveSnapshot(state)
  }

}
