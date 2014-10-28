import akka.actor.{ActorSystem, Actor, Props,ActorRef}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import scala.collection.mutable.ArrayBuffer
import akka.util.Timeout
import scala.concurrent.Await
import akka.pattern.ask
import com.typesafe.config.ConfigFactory

sealed trait Message
case class Join(node:ActorRef) extends Message
case object isReady extends Message
case object UpdateAll extends Message
case class Route(msg:String, key:Node_Actor)
case class Deliver(msg:String, key:Node_Actor)
case class insertRoutingTable(routing: ArrayBuffer[Node_Actor],node:Node_Actor)
case class updateRoutingTable(routing: ArrayBuffer[Node_Actor],node:Node_Actor)
case class insertLeafs(finalNode:Node_Actor,leafLarge:ArrayBuffer[Node_Actor],leafSmall:ArrayBuffer[Node_Actor])
case class updateBigLeaf(leaf:Node_Actor)
case class updateSmallLeaf(leaf:Node_Actor)
case class Jumps(jumps:Int)
case class SendRequest(ids:ArrayBuffer[BigInt]) 
case object BuildNetwork extends Message
case object StartWork extends Message
case object DoFail extends Message
case object Stop extends Message
case class Fail(fail: Boolean) extends Message
case class repairSmallLeaf(key:Node_Actor, node:Node_Actor, myLeafSmall:ArrayBuffer[Node_Actor],msg:String)
case class repairLargeLeaf(key:Node_Actor, node:Node_Actor, myLeafLarge:ArrayBuffer[Node_Actor],msg:String)
case class repairYourSmallLeaf(leafnode:Node_Actor, key:Node_Actor,msg:String)
case class repairYourLargeLeaf(leafnode:Node_Actor, key:Node_Actor,msg:String)
case class repairTable(msg:String, key:Node_Actor, mynode:Node_Actor, col:Int, l:Int)
case class repairYourTable(node:Node_Actor, key:Node_Actor,msg:String, col:Int, l:Int)

class Node_Actor {
  var node : ActorRef = null
  var id : BigInt = 0
  var jumps: Int = 0
}

object project3_bonus {
 
 class PastryNode(ID:BigInt,base: Int, Boss:ActorRef) extends Actor {
  var routingTable = ArrayBuffer[Node_Actor]()
  var leafSmall,leafLarge = ArrayBuffer[Node_Actor]()
  var NodeID = ID
  val(rows,cols) = (32, base)
  var joined: Boolean = false 
  
  def receive = {
    case Fail(fail) => {
      if(fail){
        context.stop(self)
      }
    }
    case SendRequest(ids) => {
      val random = new Random()	
	  var msgDest : Node_Actor = new Node_Actor() 
	  msgDest.id = ids(random.nextInt(ids.length))
	  msgDest.jumps = 0 	
	  self ! Route( "hi".toString, msgDest ) 
    }
          
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
      key.jumps = key.jumps + 1
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
        var l = shl(ID,key.id)
        val ll = l
        val key_col = digit(key.id, l) 
        if(routingTable(l*cols+key_col) != null){
          //routingTable(l*cols+key_col).node ! Route(msg, key)
          val node = routingTable(l*cols+key_col)
          if(node.node.isTerminated){
            routingTable(l*cols+key_col) = null
            var myNode = new Node_Actor()
            myNode.id = ID
            myNode.node = self
            var k: Int =0
            var flag: Boolean=true
            while(flag && (l<rows)){
              if(k!=key_col){
                val sel_node = routingTable(l*cols+k)
                if(sel_node != null){
                if(!sel_node.node.isTerminated){
                  implicit val timeout = Timeout(20 seconds)
                  val future = sel_node.node ? repairTable(msg,key,myNode,key_col,ll)
	              val node_alive =  Await.result(future.mapTo[Node_Actor], timeout.duration )
	              if((!node_alive.node.isTerminated)&&(node_alive.id != ID)){
	                routingTable(ll*cols+key_col) = node_alive
	                self ! Route(msg, key)
	                flag = false                
	              }
                }
                } 
              }
              if(flag){
                k=k+1
                if(k==cols){
                  k = 0
                  l = l + 1
                }
              }          
            }     
          }else{
            node.node ! Route(msg, key)
          }
        }else{
          var con: Boolean = true
          for(i<-l until 32){
            for(j<-0 until cols){
              if(con){
                if((routingTable(i*cols+j) != null) && ((routingTable(i*cols+j).id - key.id).abs < (ID-key.id).abs)){
                  con = false
                  //routingTable(i*cols+j).node ! Route(msg, key)
          var i_l = i        
          val node = routingTable(i_l*cols+j)
          if(node.node.isTerminated){
            routingTable(i*cols+j) = null
            var myNode = new Node_Actor()
            myNode.id = ID
            myNode.node = self
            var k: Int =0
            var flag: Boolean=true
            while(flag && (i_l<rows)){
              if(k!=j){
                val sel_node = routingTable(i_l*cols+k)
                if(sel_node != null){
                if(!sel_node.node.isTerminated){
                  implicit val timeout = Timeout(20 seconds)
                  val future = sel_node.node ? repairTable(msg,key,myNode,i,j)
	              val node_alive =  Await.result(future.mapTo[Node_Actor], timeout.duration )
	              if((!node_alive.node.isTerminated)&&(node_alive.id != ID)){
	                routingTable(i*cols+j) = node_alive
	                self ! Route(msg, key)
	                flag = false                
	              }
                }
                } 
              }
              if(flag){
                k=k+1
                if(k==cols){
                  k = 0
                  i_l = i_l + 1
                }
              }          
            }     
          }else{
            node.node ! Route(msg, key)
          }
                  
                  
                }
              }
            }
          }
          if(con){
            fowardToleaf(msg,key)
          }
        }
      }
      
    }
    
    case insertRoutingTable(preRoutingTable, preNode) => {
      val l = shl(ID, preNode.id)
      val m = digit(ID, l)
      val n = digit(preNode.id , l)
      for(j<-0 until cols){
        if((j != m) && (j != n) ){
          if(preRoutingTable(l*cols+j) != null){
            routingTable(l*cols+j) = preRoutingTable(l*cols+j)
          }
        }
      }
      routingTable(l*cols+n) = preNode
    }
    
    case updateRoutingTable(joinNodeRoutingTable, joinNode) => {
      val l = shl(ID, joinNode.id)
      for(i<-0 until l){
        for(j<-0 until cols){
          if(routingTable(i*cols+j) == null){
            routingTable(i*cols+j) = joinNodeRoutingTable(i*cols+j)
          }     
        }
      }
      val m = digit(ID, l)
      val n = digit(joinNode.id , l)
      for(j<-0 until cols){
        if((j != m) && (j != n) ){
          if(routingTable(l*cols+j) == null){
            routingTable(l*cols+j) = joinNodeRoutingTable(l*cols+j)
          }
        }
      }
      routingTable(l*cols+n) = joinNode      
    }
        
    case Deliver(msg, key) => {
	  if (msg == "Join") {
	    var finalNode : Node_Actor = new Node_Actor()
	    finalNode.id = ID
	    finalNode.node = self
	    key.node ! insertRoutingTable(routingTable, finalNode)
	    key.node ! insertLeafs(finalNode, leafLarge , leafSmall)
	    key.node ! UpdateAll
	  } else{
	    Boss ! Jumps(key.jumps)
	  }
    }
    
    case insertLeafs(finalNode, bigLeaf, smallLeaf) => {
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
    }
    
    case UpdateAll  => {
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
          routingTable(i).node ! updateRoutingTable(routingTable, joinNode)
        }
      }
      joined = true  
    }
    
    case updateBigLeaf(leaf) => {
	  if(leaf.id>ID){
	    addToLargeLeafs(leaf)
	  }
    }
    
    case updateSmallLeaf(leaf) => {
	  if(leaf.id < ID) {
	    addToSmallLeafs(leaf)
	  }
    }
    
    case repairSmallLeaf(key,myNode,myLeafSmall,msg) => {
      var leafBuffer = ArrayBuffer[Node_Actor]()
      val leaf = leafSmall ++ leafLarge
      for(node <- leaf) {
        if((!node.node.isTerminated) && (!myLeafSmall.contains(node.node)) &&(node.id < myNode.id)){
          leafBuffer.append(node)
        }
      }
      if(!leafBuffer.isEmpty){
      var min = (leafBuffer(0).id - myNode.id).abs
      var leafNode = leafBuffer(0)
      for(node <- leafBuffer){
        if((node.id - myNode.id).abs < min){
          min = (node.id - myNode.id).abs
          leafNode = node
        }
      }
      myNode.node ! repairYourSmallLeaf(leafNode,key,msg)
      }
    } 
    
    case repairLargeLeaf(key,myNode,myLeafLarge,msg) => {
      var leafBuffer = ArrayBuffer[Node_Actor]()
      val leaf = leafSmall ++ leafLarge
      for(node <- leaf) {
        if((!node.node.isTerminated) && (!myLeafLarge.contains(node.node)) &&(node.id > myNode.id)){
          leafBuffer.append(node)
        }
      } 
      if(!leafBuffer.isEmpty){
      var min = (leafBuffer(0).id - myNode.id).abs
      var leafNode = leafBuffer(0)
      for(node <- leafBuffer){
        if((node.id - myNode.id).abs < min){
          min = (node.id - myNode.id).abs
          leafNode = node
        }
      }
      myNode.node ! repairYourLargeLeaf(leafNode,key,msg)
      }
    }
    
    case repairTable(msg,key,myNode,key_col,l) => {
      val node = routingTable(l*cols+key_col)
      if(node!=null){
        sender ! node
      }else{
        sender ! myNode
      }
      
    }
    
    case repairYourSmallLeaf(leafNode,key,msg) =>{
      addToSmallLeafs(leafNode)
      self ! Route(msg,key)    
    }
    
    case repairYourLargeLeaf(leafNode,key,msg) =>{
      addToLargeLeafs(leafNode)
      self ! Route(msg,key)    
    }
    
    
    case isReady => {
      sender ! joined  
    }   
    

    
  }

  def addToSmallLeafs(leaf:Node_Actor){ 
      var i : Int = 0
      leafSmall.prepend( leaf )
      while (i < leafSmall.size - 1) {
	    if (leafSmall(i).id > leafSmall(i + 1).id) {
	      var tmp = leafSmall(i)
	      leafSmall(i) = leafSmall(i + 1)
	      leafSmall(i + 1) = tmp
	    }
	    i += 1
      }

      if (leafSmall.size > base) {
	    leafSmall = leafSmall.drop(1)
      }
  }
  
    def addToLargeLeafs(leaf:Node_Actor){
      var i : Int = 0
      leafLarge.prepend( leaf )
      while (i < leafLarge.size - 1) {
	    if(leafLarge(i).id > leafLarge(i + 1).id) {
	      var tmp = leafLarge(i)
	      leafLarge(i) = leafLarge(i + 1)
	      leafLarge(i + 1) = tmp  
	    }
	    i += 1
      }
      if (leafLarge.size > base) {
	    leafLarge = leafLarge.dropRight(1)
      }
      
    }
   def findNum(key:Node_Actor,buffer:ArrayBuffer[Node_Actor]):Int={
     var i: Int =0
     var flag: Boolean = true
     while(flag){
       if(buffer(i).id == key.id){
         flag = false
       }else{
         i=i+1
       }
     }
     return i
   }   
   def sendToleaf(msg:String, key: Node_Actor) {
//      findMin(key).node ! Deliver(msg, key )
     if(findMin(key).node.isTerminated){
       var myNode = new Node_Actor()
       myNode.id = ID
       myNode.node = self
       if(findMin(key).id < ID){
         leafSmall.remove(findNum(findMin(key),leafSmall))
         if(!leafSmall.isEmpty){
           leafSmall(0).node ! repairSmallLeaf(key,myNode,leafSmall,msg)
         }
       }else{
         if(leafLarge(leafLarge.length-1) != null){
           leafLarge(leafLarge.length-1).node ! repairLargeLeaf(key,myNode,leafLarge,msg)
         }
       }
     }else{
      val findNode = findMin(key)
      if(findNode.id == key.id || msg=="Join"){
        findNode.node ! Deliver(msg, key ) 
      }else{
        findNode.node ! Route(msg, key ) 
      }
     }
   }
    
  def fowardToleaf(msg:String, key: Node_Actor) {
     if(findMin(key).node.isTerminated){
       var myNode = new Node_Actor()
       myNode.id = ID
       myNode.node = self
       if(findMin(key).id < ID){
         leafSmall.remove(findNum(findMin(key),leafSmall))
         if(!leafSmall.isEmpty){
           leafSmall(0).node ! repairSmallLeaf(key,myNode,leafSmall,msg)
         }
         
       }else{
         if(leafLarge(leafLarge.length-1) != null){
           leafLarge(leafLarge.length-1).node ! repairLargeLeaf(key,myNode,leafLarge,msg)
         }
         
       }
     }else{
      findMin(key).node ! Route( msg, key )
     }
   }
  
  def findMin(key: Node_Actor): Node_Actor = {
    var minDist : BigInt = (ID - key.id).abs
    var minNode :Node_Actor = new Node_Actor()
    minNode.id = ID
    minNode.node = self
    var dist: BigInt =0
    val leaf = leafSmall ++ leafLarge
    for (i <- 0 until leaf.size) {
	  dist = (leaf(i).id - key.id).abs
	  if (dist < minDist) {
	    minDist = dist
	    minNode = leaf(i)
	  }
    } 
    return minNode
  }
      
  
  def shl( x: BigInt, y: BigInt ) : Int = {
    var Array_x : ArrayBuffer[Int] = BigtoArray(x, 16)
    var Array_y : ArrayBuffer[Int] = BigtoArray(y, 16)
    var i : Int = 0
    var equal : Boolean = (Array_x(31 - i) == Array_y(31 - i))
    while (equal) {
      if (i < 31) {
	    i += 1
	    equal = (Array_x(31 - i) == Array_y(31 - i))
      }else {
	    i += 1
	    equal = false
	  }
    }
    return i  
  }
  
  def digit(id: BigInt, l: Int) = BigtoArray(id, 16)(31-l)
 
  
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
 
  class pastryBoss(numNodes:Int, numRequests:Int, base:Int) extends Actor {
    var nodeArray = ArrayBuffer[ActorRef]()
    var nodeAlive = ArrayBuffer[BigInt]()
    var sum: Double = 0 
    var messages: Double = 0 
    var average: Double = 0 
    var IDs : ArrayBuffer[BigInt] = ArrayBuffer() 
    
    def receive = {
      case BuildNetwork => {
        var ID:BigInt = 0
        var counter: Int =0
        while(counter<numNodes){ 
          ID = genID(base) 
          while (IDs.contains( ID )) {
	        ID = genID(base) 
          }
          IDs.append( ID ) 
          var node = context.actorOf(Props(classOf[PastryNode],ID,base,self), counter.toString)
          nodeArray.append(node)
          counter += 1
        } 
        nodeArray(0) ! Join(null) 
        for (i <- 1 until numNodes) {
          nodeArray(i) ! Join(nodeArray(i -1)) 
          implicit val timeout = Timeout(20 seconds)
          var node_ready: Boolean = false
          while (!node_ready) {
	        val future = nodeArray(i) ? isReady
	        node_ready =  Await.result(future.mapTo[Boolean], timeout.duration )
          }
        }  
      }
      
      case StartWork => {
        for(i<-0 until numNodes){
          if(!nodeArray(i).isTerminated){
            nodeAlive.append(IDs(i))
          }
        }
        var flag: Boolean = true
        var i: Int = 0
        var num: Int =0
        while(flag){
          if(!nodeArray(i).isTerminated){
            context.system.scheduler.schedule(0 seconds, 25 milliseconds, nodeArray(i), SendRequest(nodeAlive) )
            num = num + 1
            if(num>=2){
              flag = false
            }
          }
          i = i +1
        }
      }
      case Jumps(jumps : Int) => {
	    sum += jumps
	    messages += 1
	    if(messages == numRequests*2){
	      average = sum/messages
	      println("Num = " + numNodes + "  Average jumps: " + average)
	      context.system.shutdown
	    }
      }
      case DoFail => {
        for(i<-0 until numNodes){
          var fail = math.random < 0.1
          nodeArray(i) ! Fail(fail)
        }
      }
    }
  }
  
  def main(args: Array[String]) {
    val numNodes = if (args.length > 0) args(0) toInt else 5000  // the number of Nodes
    val numRequests = if (args.length > 1) args(1) toInt else 2000   // the number of Requests for each node
    val b = 4 
    val base = math.pow(2,b).toInt //base
    //val system = ActorSystem("PastrySystem")
    val system = ActorSystem("PastrySystem", ConfigFactory.parseString("""
        akka{
          log-dead-letters-during-shutdown = off 
          log-dead-letters = off
          akka.jvm-exit-on-fatal-error = off
        }
        """))
        
    val pastryBoss = system.actorOf(Props(classOf[pastryBoss],numNodes,numRequests,base), "pastryBoss")
    pastryBoss ! BuildNetwork
    pastryBoss ! DoFail
    system.scheduler.scheduleOnce(1000 milliseconds) {
      pastryBoss ! StartWork
    }
  }
  
  def genID(base:Int): BigInt = { 
    val random = new Random()
    var ID_digit:Int =0
    var ID_Big:BigInt = 0
    var counter = 0
    while(counter<32){ 
      ID_digit = (random.nextInt(base.toInt))
      var a = (BigInt(ID_digit))*(BigInt(base).pow(counter))
      ID_Big += a
      counter += 1
    } 
    return ID_Big
  }  
}