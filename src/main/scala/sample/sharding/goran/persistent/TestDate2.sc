
trait Sorted {
  def deliveryDate: JustDate
  def sortField: String
}
// CustomerOrder and PurchaseOrder

object JustDate{
  def now = {
    val now = new java.util.Date()
    new JustDate(now.getYear-100, now.getMonth+1, now.getDate)
  }
}
class JustDate(year: Int, month: Int, day: Int) extends Sorted  {
  override def sortField = {
    f"$year%02d-$month%02d-$day%02d"
  }

  override def toString: String = sortField
  override def deliveryDate: JustDate = this

}

trait StockChange {
  def amountChange: Int
}

trait SortedAndStockChange extends Sorted with StockChange

case class CustomerOrder(deliveryDate: JustDate, amount: Int) extends SortedAndStockChange {
  def sortField = deliveryDate.toString
  def amountChange = -amount
}
case class PurchaseOrder(deliveryDate: JustDate, amount: Int) extends SortedAndStockChange {
  def sortField = deliveryDate.toString
  def amountChange = amount
}

case class SortableList(elements: List[SortedAndStockChange]*) {
  def all = {
    elements.foldLeft(List.empty[SortedAndStockChange]) ((l, e)=> l:::e)
  }
  def sortedAll = {
    all.sortBy(_.sortField)
  }
}

case class State(inStock: Int, customerOrders: List[CustomerOrder], purchaseOrders: List[PurchaseOrder]) {
  def stockPerDate:List[(JustDate, Int, Int)] = {
      val l = SortableList(customerOrders, purchaseOrders).sortedAll
    l.foldLeft(List((JustDate.now, inStock, inStock)))((list: List[Tuple3[JustDate, Int, Int]], el: SortedAndStockChange) =>
      (el.deliveryDate, el.amountChange, list.head._3 + el.amountChange) :: list).reverse
  }
}

val a = new JustDate(17, 12, 31)
val b = new JustDate(18, 1, 23)
val c = new JustDate(18, 2, 25)
val d = new JustDate(18, 1, 1)
val e = CustomerOrder(a, 5)
val f = CustomerOrder(b, 2)
val g = CustomerOrder(c, 7)
val h = CustomerOrder(d, 12)
val custOrders = List(e, f, g, h)
val i = PurchaseOrder(a, 1)
val j = PurchaseOrder(b, 2)
val k = PurchaseOrder(c, 3)
val l = PurchaseOrder(d, 4)
val purchaseOrders = List(i, j, k, l)
val state = State(0, custOrders, purchaseOrders)

state.stockPerDate

