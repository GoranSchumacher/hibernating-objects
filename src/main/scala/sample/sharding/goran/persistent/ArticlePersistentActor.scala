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

    def handleWrapper(wrapper: EntityWrapper):OrderState = {
      wrapper.message match {
        case AddCustomerOrder(o) =>
          addOrderToCustomerOrders(o)

        case AddCustomerOrderFinal(o) =>
          removeIdFromCustomerOrders(o.id).addToStock(-o.amount)

        case RemoveCustomerOrder(o) =>
          removeIdFromCustomerOrders(o.id)

        case AddPurchaseOrder(o) =>
          addOrderToPurchaseOrders(o)

        case AddPurchaseOrderFinal(o) =>
          removeIdFromPurchaseOrders(o.id).addToStock(o.amount)

        case RemovePurchaseOrder(o) =>
          removeIdFromPurchaseOrders(o.id)
      }

    }
  }

  // Messages
  case class EntityWrapper(name: String, message: Any)

  /**
    * Planned Customer order
    * @param order
    */
  case class AddCustomerOrder(order: CustomerOrder)

  case class RemoveCustomerOrder(order: CustomerOrder)

  /**
    * Delivered customer order
    * @param order
    */
  case class AddCustomerOrderFinal(order: CustomerOrder)

  /**
    * Planned purchase order
    * @param order
    */
  case class AddPurchaseOrder(order: PurchaseOrder)

  /**
    * Delivered purchase order
    * @param order
    */
  case class AddPurchaseOrderFinal(order: PurchaseOrder)

  case class RemovePurchaseOrder(order: PurchaseOrder)

  case object GetStockPlan

  //Subscriptions
  case object IncrementSubscription extends Subscription

}

class ArticlePersistentActor(myRouter: ActorRef) extends PersistentActor with LeanPersistAndHibernateTrait with PubSubTrait with ActorLogging {

  // Abstract members from PubSubTrait
  def fromRouter = myRouter
  def fromName =  context.self.path.name

  // Abstract members from LeanPersistAndHibernateTrait
  override val hibernatingTimeout = 1 minute // 1 minute == Default value
  var state = OrderState(0, List.empty, List.empty)
  override def persistenceId = context.self.path.name // == Default value
  override def purgeLogs: Boolean = true // When making snapshot it will purge logs from older entries preserving only the most recent snapshot and logs

  // Remember to call this, iff you want to hibernate
  context.setReceiveTimeout(hibernatingTimeout)

  def receiveRecover: Receive = receiveRecoverLocal orElse articleReceiveCommand

  val receiveRecoverLocal: Receive = {
    case SnapshotOffer(_, snapshot: OrderState) => {
      println(s"Recovery Snapshot received, persistenceId: ${persistenceId}, data: ${snapshot}")
      state = snapshot
    }
  }

  override def receiveCommand = super.receiveCommand orElse articleReceiveCommand

  // Be aware that recover operations also is performed here
  // If your method has side-effects => Make shure these are not performed during recover using;
  // the method: recoveryRunning
  def articleReceiveCommand: Receive = {

    case wrapper@ EntityWrapper(id, mess@ GetStockPlan)=>
      sender() ! s"Article: $persistenceId, ${state.stockPerDate}"

    case wrapper: EntityWrapper => {
      if (!recoveryRunning) {
        persist(wrapper) {a=>
          state = state.handleWrapper(wrapper)
        }
      } else {
        state = state.handleWrapper(wrapper)
        println(s"Recovery message received, persistenceId: ${persistenceId}, data: ${wrapper}")
      }
    }

    case SubscriptionEventOccurred(_, subscription, mess) => log.debug(s"SubscriptionEventOccurred: $subscription $mess")

    case mess@ _ => log.debug(s"UNKNOWN MESSAGE: $mess")

  }
}
