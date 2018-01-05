import akka.actor.ActorRef

val m = Map("A" -> Set("Hej"))
val p = m + ("B" -> Set("Då"))

val x = p + ("A" -> p("A").+("Hej1"))

type stateType = Map[String, Set[String]]
case class State(subscribers: stateType=Map[String, Set[String]]())

var state = State()



if(state.subscribers.contains("A"))
  if(!state.subscribers("A").contains("a")) {
    state = State(state.subscribers + ("A" -> (state.subscribers("A") + "a")))
  }
  else
else
    state = State(state.subscribers + ("A" -> Set("a")))


val pp = state.subscribers.contains("A")


//c.subscribers.contains("a")
//val d = c.copy(subscribers = c.subscribers.+ ("Z" -> Set("zz")))
//val e = d.copy(subscribers = d.subscribers.+ ("A" -> Set("aa")))