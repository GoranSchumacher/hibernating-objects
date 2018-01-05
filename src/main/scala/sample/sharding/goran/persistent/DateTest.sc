trait Sorted {
  def sortField: String
}

class Date(year: Int, month: Int, day: Int) extends Sorted {
  override def sortField = {
    f"$year%02d-$month%02d-$day%02d"
  }

  override def toString: String = sortField
}

class sortedList()
case class CustomerOrder(deliveryDate: Date, amount: Int) extends Sorted {
  def sortField = deliveryDate.toString
}
case class PurchaseOrder(deliveryDate: Date, amount: Int) extends Sorted {
  def sortField = deliveryDate.toString
}

val a = new Date(17, 12, 31)
val b = new Date(18, 1, 23)
val c = new Date(18, 2, 25)
val d = new Date(18, 1, 1)

val l1 = List(a, b, c, d)
l1.sortBy(_.toString)

val e = CustomerOrder(a, 5)
val f = CustomerOrder(b, 2)
val g = CustomerOrder(c, 7)
val h = CustomerOrder(d, 12)
val l2 = List(e, f, g, h)

l2.sortBy(_.deliveryDate.toString)

val i = PurchaseOrder(a, 1)
val j = PurchaseOrder(b, 2)
val k = PurchaseOrder(c, 3)
val l = PurchaseOrder(d, 4)
val l3 = List(i, j, k, l)

val p = l3.::(i)

case class SortableList(elements: List[Sorted]*) {
  def x = {
    elements.foldLeft(List.empty[Sorted]) ((l: List[Sorted], e:List[Sorted])=> l:::e)
  }
  def sortedAll = {
    x.sortBy(_.sortField)
  }
}

val l4 = SortableList(l2, l3).sortedAll
  //.sortBy(_.toString)

