import scala.math._
import scala.actors._
import scala.actors.Actor._
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
import java.security.MessageDigest

object Sha256 {
  private val sha = MessageDigest.getInstance("SHA-256")
   def hex_digest(s: String): String = {
    sha.digest(s.getBytes)
    .foldLeft("")((s: String, b: Byte) => s +
                  Character.forDigit((b & 0xf0) >> 4, 16) +
                  Character.forDigit(b & 0x0f, 16))
  }
}


class WorkerActor extends Actor {
  var char_set:Array[Char] = char_table() //a Array storing all valid characters in ASCII characters (from 33 to 126)

  //generate a char_set
  private def char_table ():Array[Char]={ 
    var char_set:Array[Char] = new Array[Char](94)
    for (i<-33 to 126){
      char_set(i-33)=i.toChar   
    }
    return char_set //store all valid characters 
  } 
  
  //Implement the behavior of Worker
  def act() {
   var str:Array[Char] = new Array[Char](10)
      react {
      	case (prefix:String, b:Int, n:Int, m:Int,boss:Actor) =>
		      var i:Long=0
	      	for (i <- b*n to b*n+n-1) {
	      	  var k:Int=i
	      	  var len:Int=0
	      	  var new_str=prefix
	      	  while(k>=94){
	      	    str(len)=char_set(k%94)
	      	    k=k/94
	      	    len=len+1 
	      	  }
		        str(len)=char_set(k%94)
            len=len+1
	      	  for(j<-1 to len){
	      	    new_str=new_str+str(len-j)
	      	  }
	
	      		var sha_str=Sha256.hex_digest(new_str)
	      		var sub_str=sha_str.substring(0, m)
	      		var start_num=Integer.parseInt(sub_str, 16)
	      		if (start_num==0){
	      			boss ! (new_str, sha_str) // finds a solution
	      		}
	      	}
	      	boss ! (1)


      }
 }
}


class ServerActor(prefix: String, k:Int, len:Int) extends Actor {
	val N:Int=8836 //the number of work unit
  def act() {
    alive(9011)
    register('boss, self)
    var i_th:Int=0
    var n_actor:Int=0
    var num:Int=1
    for(i<-0 to len-3)
    	 num=num*94
    val worker=new WorkerActor
    worker.start
    worker ! (prefix,i_th, N, k,self)
    i_th=i_th+1
    loop {
    	 react {
    	    case (key:Int) =>  //
    		    val worker=new WorkerActor
    		    worker.start
		        worker ! (prefix,i_th, N, k,self)
		        if((i_th>=num)&&(n_actor==0))
			         exit()
    	      i_th=i_th+1
    	    case (slave: String, key:Int) =>  //
		        n_actor=n_actor+1; 
		        //println(n_actor)
    	      val remoteBoss=select(Node(slave, 9018), 'remoteboss)
		        remoteBoss ! (prefix,i_th, N, k)
    	      i_th=i_th+1
    	    case (slave: String) =>  // 
    	       val remoteBoss=select(Node(slave, 9018), 'remoteboss)
    	       if(i_th<num){
			         remoteBoss ! (prefix,i_th, N, k)
		         }else{
        		   remoteBoss ! "Stop" //send a message to the newly started ClientActor
			         n_actor=n_actor-1
			         //println(n_actor)
		         }
		         if((i_th>=num)&&(n_actor==0))
			           exit()
    	       i_th=i_th+1
       	  case (bitcoin: String, sha_bitcoin: String) =>  // receive a solution
        		println(bitcoin+"\t"+sha_bitcoin)
    	}
    }
  }
}

object Server {
  def main(args: Array[String]) {
    val prefix = if (args.length > 0) args(0) else "huilingzhang"  // the given starting string
    val k = if (args.length > 1) args(1) toInt else 5              // # of 0 at the beginning of the sha-256 value
    val len = if (args.length > 2) args(2) toInt else 4            // the maximum length of string added from the given string
    val w = new ServerActor(prefix,k,len)
    w.start
  }
}
