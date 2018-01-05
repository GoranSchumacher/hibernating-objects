package sample.sharding.goran.persistent

import java.util.Calendar

import akka.actor.{ActorLogging, ActorRef, Props}
import akka.persistence.{PersistentActor, SnapshotOffer}
import sample.sharding.goran.persistent.ArticlePersistentActor._
import sample.sharding.goran.persistent.childutils.PubSubPersistentActor.{NotifySubscribers, Subscription, SubscriptionEventOccurred}
import sample.sharding.goran.persistent.traits.{LeanPersistAndHibernateTrait, PubSubTrait}

import scala.concurrent.duration._

/**
  * @author Gøran Schumacher (GS) / Schumacher Consulting Aps
  * @version $Revision$ 31/12/2017
  */
object ArticlePersistentActor {

  trait Sorted {
    def deliveryDate: JustDate
    def sortField: String
  }

  // CustomerOrder and PurchaseOrder

  object JustDate {
    def now = {
      val now = new java.util.Date()
      new JustDate(now.getYear-100, now.getMonth+1, now.getDate)
    }
  }

  class JustDate(year: Int, month: Int, day: Int) extends Sorted with Serializable {
    override def sortField = {
      f"$year%02d-$month%02d-$day%02d"
    }

    override def toString: String = sortField

    override def deliveryDate: JustDate = this

  }

  trait StockChange {
    def id: String

    def amountChange: Int
  }

  trait SortedAndStockChange extends Sorted with StockChange

  case class CustomerOrder(id: String, deliveryDate: JustDate, amount: Int) extends SortedAndStockChange {
    def sortField = deliveryDate.toString

    def amountChange = -amount
  }

  case class PurchaseOrder(id: String, deliveryDate: JustDate, amount: Int) extends SortedAndStockChange {
    def sortField = deliveryDate.toString

    def amountChange = amount
  }

  case class SortableList(elements: List[SortedAndStockChange]) {
    def removeId(id: String) = {
      elements.filter(_.id != id)
    }

    def add(element: SortedAndStockChange) = {
      removeId(element.id).::(element)
    }

    def sort = {
      elements.sortBy(_.sortField)
    }
  }

  case class OrderState(inStock: Int, customerOrders: List[CustomerOrder], purchaseOrders: List[PurchaseOrder]) {
    def stockPerDate: List[(String, JustDate, Int, Int)] = {
      val l = SortableList(customerOrders ::: purchaseOrders).sort
      l.foldLeft(List(("Balance", JustDate.now, inStock, inStock)))((list, el) =>
        (el.getClass.getSimpleName, el.deliveryDate, el.amountChange, list.head._4 + el.amountChange) :: list).reverse
    }

    def addOrderToCustomerOrders(o: CustomerOrder) = {
      this.copy(customerOrders = SortableList(customerOrders).add(o).asInstanceOf[List[CustomerOrder]])
    }

    def addOrderToPurchaseOrders(o: PurchaseOrder) = {
      this.copy(purchaseOrders = SortableList(purchaseOrders).add(o).asInstanceOf[List[PurchaseOrder]])
    }

    def removeIdFromCustomerOrders(id: String) = {
      this.copy(customerOrders = SortableList(customerOrders).removeId(id).asInstanceOf[List[CustomerOrder]])
    }

    def removeIdFromPurchaseOrders(id: String) = {
      this.copy(purchaseOrders = SortableList(purchaseOrders).removeId(id).asInstanceOf[List[PurchaseOrder]])
    }

    def addToStock(amount: Int) = {
      this.copy(inStock = inStock + amount)
    }
  }

  // Messages
  case class MessageWrapper(name: String, message: Any)

  case class AddCustomerOrder(order: CustomerOrder)

  case class RemoveCustomerOrder(order: CustomerOrder)

  case class AddCustomerOrderFinal(order: CustomerOrder)

  case class AddPurchaseOrder(order: PurchaseOrder)

  case class AddPurchaseOrderFinal(order: PurchaseOrder)

  case class RemovePurchaseOrder(order: PurchaseOrder)

  case object GetStockPlan

  //Subscriptions
  case object IncrementSubscription extends Subscription

}

class ArticlePersistentActor(myRouter: ActorRef) extends PersistentActor with LeanPersistAndHibernateTrait with PubSubTrait with ActorLogging {
  // Abstract members from LeanPersistAndHibernateTrait
  override val hibernatingTimeout = 1 minute // 1 minute == Default value
  var state = OrderState(0, List.empty, List.empty)

  override def persistenceId = context.self.path.name // == Default value

  def receiveRecover: Receive = receiveRecoverLocal orElse receiveCommandLocal

  val receiveRecoverLocal: Receive = {
    case SnapshotOffer(_, snapshot: OrderState) => {
      println(s"Snapshot received, persistenceId: ${persistenceId}, data: ${snapshot}")
      state = snapshot
    }
  }

  override def receiveCommandLocal: Receive = pubSubReceiveCommand orElse localReceiveCommand1

  // Be aware that recover operations also is performed here
  // If your method has side-effects => Make shure these are not performed during recover using;
  // the method: recoveryRunning
  def localReceiveCommand1: Receive = {
    case cOrder@AddCustomerOrder(o) if (!recoveryRunning) =>
      persist(cOrder) { order =>
        state = state.addOrderToCustomerOrders(o)
      }

    case cOrder@AddCustomerOrderFinal(o) =>
      if (!recoveryRunning) {
        persist(cOrder) { order =>
          state = state.removeIdFromCustomerOrders(o.id).addToStock(-o.amount)
        }
      }
    case cOrder@RemoveCustomerOrder(o) =>
      if (!recoveryRunning) {
        persist(cOrder) { order =>
          state = state.removeIdFromCustomerOrders(o.id)
        }
      }

    case cOrder@AddPurchaseOrder(o) =>
      if (!recoveryRunning) {
        persist(cOrder) { order =>
          state = state.addOrderToPurchaseOrders(o)
        }
      }
    case cOrder@AddPurchaseOrderFinal(o) =>
      if (!recoveryRunning) {
        persist(cOrder) { order =>
          state = state.removeIdFromPurchaseOrders(o.id).addToStock(o.amount)
        }
      }
    case cOrder@RemovePurchaseOrder(o) =>
      if (!recoveryRunning) {
        persist(cOrder) { order =>
          state = state.removeIdFromPurchaseOrders(o.id)
        }
      }
    case GetStockPlan => sender() ! state.stockPerDate

    case SubscriptionEventOccurred(_, subscription, mess) => log.debug(s"SubscriptionEventOccurred: $subscription $mess")
  }
}
