See ArticleTestApp

To run
=======
* Start ArticleApp - This will start up 3 ActorSystems on port 2551, 2552 (seed nodes) amd one random
* Start ArticleTestApp - This will make these calls;

articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1001", new JustDate(2018, 1, 20), 100)))
articlesRouter ! MessageWrapper("1", AddCustomerOrder(CustomerOrder("2001", new JustDate(2018, 1, 25), 5)))
articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1002", new JustDate(2018, 2, 1), 15)))
articlesRouter ! MessageWrapper("1", AddPurchaseOrder(PurchaseOrder("1003", new JustDate(2018, 2, 20), 25)))
articlesRouter ! MessageWrapper("1", AddCustomerOrder(CustomerOrder("2002", new JustDate(2018, 2, 25), 8)))
(articlesRouter ask MessageWrapper("1", GetStockPlan)).map{a=>println("Result:1" + a)}

MessageWrapper is sent to the router.
The router send the message to actor with id "1".

The above code, creates 3 purchase orders and 2 customer orders.
Since they are just preliminary, they are not added to the balance.
GetStockPlan list a projection of article balance using the balance and the list of purchase and customer orders.

Result:1
List((Balance,18-01-08,0,0),
(PurchaseOrder,2018-01-20,100,100),
(CustomerOrder,2018-01-25,-5,95),
(PurchaseOrder,2018-02-01,15,110),
(PurchaseOrder,2018-02-20,25,135),
(CustomerOrder,2018-02-25,-8,127))

Then you make one purchase and one customer order final.
Then the temporary orders are removed and the amount is added to the balance.

articlesRouter ! MessageWrapper("1", AddPurchaseOrderFinal(PurchaseOrder("1001", new JustDate(2018, 1, 20), 90)))
articlesRouter ! MessageWrapper("1", AddCustomerOrderFinal(CustomerOrder("2001", new JustDate(2018, 1, 25), 5)))
(articlesRouter ask MessageWrapper("1", GetStockPlan)).map{a=>println("Result:2" + a)}

Result:2
List((Balance,18-01-08,85,85),
(PurchaseOrder,2018-02-01,15,100),
(PurchaseOrder,2018-02-20,25,125),
(CustomerOrder,2018-02-25,-8,117))

Start an extra Instance
=======================
cd /Users/GSConsulting/Dev./Repos/hibernating-objects
sbt "runMain sample.sharding.ArticleApp 2555"