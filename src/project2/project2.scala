import akka.actor.{ActorSystem, Actor, Props}
import akka.actor.ActorRef
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.math.abs
import akka.actor.PoisonPill

sealed trait Message
case object Rumor extends Message
case object Remove_Me extends Message
case object Gossip_Exit extends Message
case object Self_Message extends Message
case object Build_Network extends Message
case class Init_Node (neighbors: Array[ActorRef])
case class PushSum (ss: Double, ww: Double)
case class PushSum_Exit (result: Double)



abstract class Node extends Actor {
  val rand = new Random
  var Neighbors:Array[ActorRef] = Array.empty
  var rumor_count:Int = 0
  def Random_neighbor():ActorRef = {
    if(Neighbors.isEmpty) {
      return self     
    }else{
      return Neighbors(rand.nextInt(Neighbors.length))
    }  
  }
  def remove_neighbor(Actor_i:ActorRef):Array[ActorRef] ={
    //println(self.path.name+" remove "+Actor_i.path.name)
    Neighbors = Neighbors filter (_ != Actor_i)
    return Neighbors
  }
  def Init(neighbors: Array[ActorRef]) {
    Neighbors = neighbors
  }
  def Send_Remove() {
    Neighbors.foreach(_ ! Remove_Me)
  }
}

class GossipNode(master: ActorRef) extends Node {
  context.system.scheduler.schedule(0 milliseconds, 1 milliseconds, self, Self_Message)
  
  def Send_Rumor(random_neighbor: ActorRef) {
    if(rumor_count > 0) {
      random_neighbor ! Rumor
    }
  }
  
  def receive = {
    
      case Init_Node(neighbors) => Init(neighbors)
    		  					
      case Rumor => rumor_count = rumor_count + 1
//      				 if(rumor_count > 10)
                       //println(rumor_count + ":" + self.path.name + " receive rumor from " + sender.path.name)
      				 if(rumor_count == 10) {
      				   Send_Remove()
      				   println(self.path.name+" exit")
      				   master ! Gossip_Exit
      				   //context.stop(self)
      				   context.system.scheduler.scheduleOnce(1000 milliseconds) {
      				     self ! PoisonPill
      				   }
      				 }

      case Self_Message => Send_Rumor(Random_neighbor())
      
      case Remove_Me => remove_neighbor(sender)
      

  }

}

class PushSumNode (master: ActorRef) extends Node {
  var s : Double = 0
  var w : Double = 1
  var nConv: Int = 0
  
  def Send_SW(random_neighbor: ActorRef) {
      random_neighbor ! PushSum(s/2, w/2)
      s = s /2
      w = w /2
  }
  def receive = {
    case Init_Node(neighbors) => Init(neighbors)
    								 s = self.path.name.toInt
    case Rumor => Send_SW(Random_neighbor())
    case PushSum(ss, ww) => if(abs(s/w-(ss+s)/(ww+w)) < 1e-10) {
    					       nConv = nConv +1
    						 } else{
    						   nConv = 0
    						 }
    						 s = s + ss
    						 w = w + ww
    				         if(nConv == 5) {
    				           master ! PushSum_Exit(s/w)
    				         } else{
    				           Send_SW(Random_neighbor())
    				         }
    				           
    
  }
  
  
}

abstract class Network(val numNodes: Int, val algorithm: String) extends Actor {
  val rand = new Random
  var numof_GossipNode: Int = 0
  var start: Long = 0
  var  Net:Array[ActorRef] = new Array[ActorRef](numNodes)

  def Neighbors(id:Int):Array[ActorRef]
  def receive = {
    case Build_Network => algorithm match {
    								case "gossip" => //Net = Array.fill(numNodes)(context.actorOf(Props(new GossipNode(self))))
    												  for(i<-0 to numNodes-1) {
    												    Net(i) = context.actorOf(Props(new GossipNode(self)),i.toString)
    												  }
    								case "push-sum" =>  for(i<-0 to numNodes-1) {
    												       Net(i) = context.actorOf(Props(new PushSumNode(self)),i.toString)
    												     }
								    }
    			  		   for(node <- Net) {
    			  		     val neighbors = Neighbors(node.path.name.toInt)
//    			  		     println("actor " +i+" neighbors are ")
//    			  		     for(n <- neighbors)
//    			  		       println(n.path.name)
    			  		     node ! Init_Node(neighbors)
    			  		   }
    					   start = System.currentTimeMillis
      					   Net(rand.nextInt(numNodes)) ! Rumor

    			  		   
    case Gossip_Exit => numof_GossipNode += 1
    					 println("finish" + numof_GossipNode)
    					 if(numof_GossipNode == numNodes) {
    					   var duration = (System.currentTimeMillis - start).millis
    					   println("run time =  " + duration)
    					   context.system.scheduler.scheduleOnce(1 seconds) {
    					     context.system.shutdown()
      				       }
    					   
    					 }
    case PushSum_Exit(result) => var duration = (System.currentTimeMillis - start).millis
    				   			  println("run time =  " + duration)
    							  println("sum =  " + result*numNodes)
    							  println(sender.path.name)
        			   			  context.system.scheduler.scheduleOnce(1 seconds) {
    				     			context.system.shutdown()
    							  }
  }
  
  
}

class Full(override val numNodes: Int, override val algorithm: String) extends Network(numNodes,algorithm){
  def Neighbors(id:Int):Array[ActorRef]=
    Array.range(0, numNodes) filter (_!=id) map (Net(_))
}

class Two_D(val rows: Int, val cols: Int, override val algorithm: String) extends Network(rows*cols,algorithm){
  private def inRange(row: Int, col: Int): Boolean = (row >= 0) && (row < rows) && (col >= 0) && (col < cols)
  private def node(row: Int, col: Int): ActorRef = Net(row*cols+col)
  def Neighbors(id:Int):Array[ActorRef] ={
    val row = id/cols
    val col = id%cols
    var neighbors = (Array(row,row,row+1,row-1), Array(col+1,col-1,col,col)).zipped.filter(inRange(_,_)).zipped.map(node(_,_))
    return neighbors
  }
    
}

class Line(override val numNodes: Int, override val algorithm: String) extends Network(numNodes,algorithm){
  private def inRange(id: Int): Boolean = (id >= 0) && (id < numNodes)
  def Neighbors(id:Int):Array[ActorRef] =
    Array(id-1, id+1) filter (inRange(_)) map (Net(_))  
}

class Im_Two_D(override val rows: Int, override val cols: Int, override val algorithm: String) extends Two_D (rows,cols,algorithm){
  val rand1 = new Random
  var neighbors_2D: Array[ActorRef] = Array.empty
  private def inRange(Actor_i: ActorRef): Boolean = {
    for(neighbor <- neighbors_2D) {
      if((neighbor.equals(Actor_i)))
        return false
    }
    return true
  }
  override def Neighbors(id:Int):Array[ActorRef] ={
    neighbors_2D = super.Neighbors(id)
    var Net1 = Net filter(_.path.name.toInt != id)
    var other_actor = Net1 filter (inRange(_))
    
    var neighbors=  neighbors_2D :+ other_actor(rand1.nextInt(other_actor.length))
    return neighbors
  }
  
}


object project2 {
  def main(args: Array[String]) {
    val numNodes = if (args.length > 0) args(0) toInt else 100  // the number of Nodes
    val topology = if (args.length > 1) args(1)  else "line"   // topology
    val algorithm = if (args.length > 2) args(2) else "push-sum" // algorithm
    val rows = List.range(math.sqrt(numNodes) toInt, 0, -1).find(numNodes%_==0).get
    val cols = numNodes/rows
      
    val system = ActorSystem("MySystem")
    
    topology match {
      case "full" => val master = system.actorOf(Props(new Full(numNodes, algorithm)), name = "master")
    		  		  master ! Build_Network
      case "2D" => val master = system.actorOf(Props(new Two_D(rows, cols, algorithm)), name = "master")
    		  		  master ! Build_Network
      case "line" => val master = system.actorOf(Props(new Line(numNodes, algorithm)), name = "master")
    		  		  master ! Build_Network
      case "imp2D" => val master = system.actorOf(Props(new Im_Two_D(rows, cols, algorithm)), name = "master")
    		  		  master ! Build_Network
    }
    

    
  }
}