import akka.actor.{ActorSystem, Actor, Props}
import akka.actor.ActorRef
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import akka.util.Timeout
import scala.concurrent.Await
import akka.pattern.ask

sealed trait Message
case class Join(node:ActorRef) extends Message
case object isReady extends Message
case class Route(msg:String, key:Node_Actor)
case class Deliver(msg:String, key:Node_Actor)
case class insertRoutingTable(routing: ArrayBuffer[Node_Actor],node:Node_Actor)
case class initTable(finalNode: Node_Actor, Table: ArrayBuffer[Node_Actor])
case class insertLeafs(finalNode:Node_Actor,leafLarge:ArrayBuffer[Node_Actor],leafSmall:ArrayBuffer[Node_Actor])
case class updateBigLeaf(leaf:Node_Actor)
case class updateSmallLeaf(leaf:Node_Actor)

class Node_Actor {
  var node : ActorRef = null;
  var id : BigInt = BigInt(-1);
  var jumps: Int = 0

}
object project3_smc {
  
 class PastryNode(ID:BigInt,base: Int) extends Actor {
  var routingTable = ArrayBuffer[Node_Actor]()
  var leafSmall,leafLarge = ArrayBuffer[Node_Actor]()// leaf array, smaller than us , starting with smallest
  val(rows,cols) = (32, base)
  var joined: Boolean = false;
  
  def receive = {
    case Join(node) => {
      var myNode = new Node_Actor()
      routingTable = routingTable.padTo(rows*cols, null)
      if(node == null){
        joined = true    
      } else {
        myNode.id = ID
        myNode.node = self
        node ! Route("Join",myNode)
      }
    }
    
    case Route(msg,key) => {
      if(msg == "Join"){
        var myNode = new Node_Actor()
        myNode.id = ID
        myNode.node = self
        key.node ! insertRoutingTable(routingTable, myNode)
      }
      var isInleaf: Boolean =false
      if(leafLarge.isEmpty){
        if(leafSmall.isEmpty){
          isInleaf = true
        }else{
          isInleaf = (leafSmall(0).id <= key.id)
        }
      }else{
        if(leafSmall.isEmpty){
          isInleaf = (leafLarge(leafLarge.size-1).id >= key.id)
        }else{
          isInleaf = ((leafLarge(leafLarge.size-1).id >= key.id) && (leafSmall(0).id <= key.id))
        }
      }
      if(isInleaf){
        sendToleaf(msg,key)
      }else{
        val l = shl(ID,key.id)
        val key_col = digit(key.id, l) 
        if(routingTable(l*cols+key_col) != null){
          routingTable(l*cols+key_col).node ! Route(msg, key)
        }else{
          var con: Boolean = true
          for(i<-l until 32){
            for(j<-0 until cols){
              if(con){
                if((routingTable(i*cols+j) != null) && ((routingTable(i*cols+j).id - key.id).abs < (ID-key.id).abs)){
                  con = false
                  routingTable(i*cols+j).node ! Route(msg, key)
                }
              }
            }
          }
          if(con){
            fowardToleaf(msg,key)
          }
        }
      }
      key.jumps = key.jumps + 1
    }
    
    case insertRoutingTable(preRoutingTable, preNode) => {
      val l = shl(ID, preNode.id)
      var i: Int =0
      var j: Int =0
      for(i<-0 until l){
        for(j<-0 until cols){
          if(routingTable(i*cols+j) == null){
            routingTable(i*cols+j) = preRoutingTable(i*cols+j)
          }     
        }
      }
      val m = digit(ID, l)
      val n = digit(preNode.id , l)
      for(j<-0 until cols){
        if((j != m) && (j != n) ){
          if(routingTable(i*cols+j) == null){
            routingTable(l*cols+j) = preRoutingTable(l*cols+j)
          }
        }
      }
      routingTable(l*cols+n) = preNode
    }
    
    case Deliver(msg, key) => {
	  if (msg == "Join") {
	    var finalNode : Node_Actor = new Node_Actor()
	    finalNode.id = ID
	    finalNode.node = self
	    key.node ! initTable(finalNode, routingTable)
	    key.node ! insertLeafs(finalNode, leafLarge , leafSmall)
	  } else{
	    key.jumps = key.jumps+ 1
	  }
    }
    
    
    case initTable(finalNode, finalRoutTable) => {
      val l = shl(ID,finalNode.id)
      val m = digit(ID, l)
      val n = digit(finalNode.id , l)
      routingTable(l*cols+n) = finalNode
      for(i<-0 until cols){
        if(i != m){ //&& !=n
          if(finalRoutTable(l*cols+i) != null){
            routingTable(l*cols+i) = finalRoutTable(l*cols+i)
          }
        }
      }
      
    }
    case insertLeafs(finalNode, bigLeaf , smallLeaf) => {
      for(i<-0 until bigLeaf.length){
        if(bigLeaf(i).id > ID){
          addToLargeLeafs(bigLeaf(i))
        }
      }
      for(i<-0 until smallLeaf.length){
        if(smallLeaf(i).id < ID){
          addToSmallLeafs(smallLeaf(i))
        }
      }
      if(finalNode.id > ID){
        addToLargeLeafs(finalNode)
      }else{
        addToSmallLeafs(finalNode)
      }
      val joinNode = new Node_Actor()
      joinNode.id = ID
      joinNode.node = self
      
      for(i<-0 until leafSmall.length){
        leafSmall(i).node ! updateBigLeaf(joinNode)
      }
      for(i<-0 until leafLarge.length){
        leafLarge(i).node ! updateSmallLeaf(joinNode)
      }
      
      for(i<-0 until routingTable.length){
        if(routingTable(i) != null){
          routingTable(i).node ! insertRoutingTable(routingTable, joinNode)
        }
      }
      joined = true
    }
    
    case updateBigLeaf(leaf:Node_Actor) => {
	  if(leaf.id>ID){
	    addToLargeLeafs(leaf)
	  }
    }
    
    case updateSmallLeaf(leaf:Node_Actor) => {
	  if(leaf.id < ID) {
	    addToSmallLeafs(leaf)
	  }
    }
    
    case isReady => {
      sender ! joined
      
    }
    
      
      
    
  }

  def addToSmallLeafs(leaf:Node_Actor){ 
      var i : Int = 0;
      leafSmall.prepend( leaf );
      while (i < leafSmall.size - 1) {
	    if (leafSmall(i).id > leafSmall(i + 1).id) {
	      var tmp = leafSmall(i)
	      leafSmall(i) = leafSmall(i + 1)
	      leafSmall(i + 1) = tmp
	    }
	    i += 1;
      }

      if (leafSmall.size > base) {
	    leafSmall = leafSmall.drop(1);
      }
  }
  
    def addToLargeLeafs(leaf:Node_Actor){
      var i : Int = 0;
      leafLarge.prepend( leaf );
      while (i < leafLarge.size - 1) {
	    if(leafLarge(i).id > leafLarge(i + 1).id) {
	      var tmp = leafLarge(i)
	      leafLarge(i) = leafLarge(i + 1)
	      leafLarge(i + 1) = tmp  
	    }
	    i += 1;
      }
    }
      
   def sendToleaf(msg:String, key: Node_Actor) {
      var minDist : BigInt = (ID - key.id).abs;
      var minNode :Node_Actor = new Node_Actor();
      minNode.id = ID;
      minNode.node = self;
      var dist: BigInt =0
      for (i <- 0 until leafSmall.size) {
        dist = (leafSmall(i).id - key.id).abs;
	    if (dist < minDist) {
	      minDist = dist;
	      minNode = leafSmall(i);
	    }
      }
      
      for (i <- 0 until leafLarge.size) {
	    dist = (leafLarge(i).id - key.id).abs;
	    if (dist < minDist) {
	      minDist = dist;
	      minNode = leafLarge(i);
	    }
      }
   
      minNode.node ! Deliver(msg, key );
   }
    
  def fowardToleaf(msg:String, key: Node_Actor) {
      var minDist : BigInt = (ID - key.id).abs;
      var minNode :Node_Actor = new Node_Actor();
      minNode.id = ID;
      minNode.node = self;
      var dist: BigInt =0
      for (i <- 0 until leafSmall.size) {
        dist = (leafSmall(i).id - key.id).abs;
	    if (dist < minDist) {
	      minDist = dist;
	      minNode = leafSmall(i);
	    }
      }
      
      for (i <- 0 until leafLarge.size) {
	    dist = (leafLarge(i).id - key.id).abs;
	    if (dist < minDist) {
	      minDist = dist;
	      minNode = leafLarge(i);
	    }
      }
   
      minNode.node ! Route( msg, key );
   }
      
  
  def shl( x: BigInt, y: BigInt ) : Int = {
    var Array_x : ArrayBuffer[Int] = BigtoArray(x, 16)
    var Array_y : ArrayBuffer[Int] = BigtoArray(y, 16)
    var i : Int = 0
    var equal : Boolean = (Array_x(31 - i) == Array_y(31 - i))
    while (equal) {
      if (i < 31) {
	    i += 1;
	    equal = (Array_x(31 - i) == Array_y(31 - i))
      }else {
	    i += 1;
	    equal = false;
	  }
    }
    return i;  
  }
  
  def digit(id: BigInt, l: Int) : Int = {
      var k : Int = 31 - l;
      var tmp : BigInt  = id % BigInt(16).pow(k + 1)
      tmp /= BigInt(16).pow(k)
      val col : Int = tmp.toInt     
      return col; 
    }
  

}


  def main(args: Array[String]) {
    val numNodes = if (args.length > 0) args(0) toInt else 100  // the number of Nodes
    val numRequests = if (args.length > 1) args(1)  else 1   // the number of Requests for each node
      
    val system = ActorSystem("PastrySystem")
    val b = 4 
    val base = math.pow(2,b).toInt //base
    var ID:BigInt = 0
    var IDs : ArrayBuffer[BigInt] = ArrayBuffer();
    var counter: Int =0
    var nodeArray = ArrayBuffer[ActorRef]()
    while(counter<numNodes){ 
      ID = genID(base) 
      while (IDs.contains( ID )) {
	    ID = genID(base) 
      }
      IDs.append( ID );
      var node = system.actorOf(Props(classOf[PastryNode],ID,base), counter.toString)
      nodeArray.append(node)
      counter += 1
    }
    
      //add first node
    nodeArray(0) ! Join(null)  
  
    //add other nodes
    var i : Int = 0;
    for (i <- 1 until numNodes) {
      nodeArray(i) ! Join(nodeArray(i -1)) 
      //wait for join process to complete
      implicit val timeout = Timeout(20 seconds)
      var node_ready: Boolean = false
      while (!node_ready) {
	    val future = nodeArray(i) ? isReady
	    node_ready =  Await.result(future.mapTo[Boolean], timeout.duration )
	    println(i)
      }
    }
    
    system.shutdown  
  }


  
  def genID(base:Int): BigInt = { 
    val random = new Random()
    var ID = new ArrayBuffer[Int]()

    var counter = 0
    while(counter<32){ 
      ID = ID += (random.nextInt(base.toInt))
      counter += 1
    } 
    return Array_to_Big(ID, base);
  }
  
  def Array_to_Big(ID:ArrayBuffer[Int], base:Int):BigInt = {
    var ID_Big:BigInt = 0
    var index = 0
    while(index < ID.length){
      var a = (BigInt(ID(index)))*(BigInt(base).pow(index))
      ID_Big += a
      index += 1
    }
    return ID_Big
  }
  
  def BigtoArray(ID: BigInt, base:Int):ArrayBuffer[Int]={
    var ID_Array = ArrayBuffer[Int]()
    var counter = 0
    var id = ID
    val bbase = BigInt(base)
    while(counter<32){
      ID_Array = ID_Array += (id.mod(bbase)).toInt
      id = id/(bbase)
      counter += 1
      }
    return ID_Array
  }
    
}