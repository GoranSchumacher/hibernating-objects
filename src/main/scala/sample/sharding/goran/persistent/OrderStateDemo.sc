// Shows how to test the state object outside the actor

import sample.sharding.goran.persistent.ArticlePersistentActor._

var state = sample.sharding.goran.persistent.ArticlePersistentActor.OrderState(0, List.empty[CustomerOrder], List.empty[PurchaseOrder])

println(s"Orders: ${state.stockPerDate}")

state=state.handleWrapper(EntityWrapper("ANY", AddPurchaseOrder(PurchaseOrder("1001", new JustDate(2018, 1, 20), 100))))
state.stockPerDate


state=state.handleWrapper(EntityWrapper("ANY", AddPurchaseOrderFinal(PurchaseOrder("1001", new JustDate(2018, 1, 20), 100))))
state.stockPerDate


state=state.handleWrapper(EntityWrapper("ANY", AddCustomerOrder(CustomerOrder("8002", new JustDate(2018, 2, 20), 20))))
state.stockPerDate

state=state.handleWrapper(EntityWrapper("ANY", AddCustomerOrderFinal(CustomerOrder("8004", new JustDate(2018, 2, 20), 20))))
state.stockPerDate
