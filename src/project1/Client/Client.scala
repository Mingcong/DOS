
import scala.math._
import scala.actors._
import scala.actors.Actor._
import scala.actors.remote._
import scala.actors.remote.RemoteActor._
import java.net._

object Sha256 {
  private val sha = MessageDigest.getInstance("SHA-256")
   def hex_digest(s: String): String = {
    sha.digest(s.getBytes)
    .foldLeft("")((s: String, b: Byte) => s +
                  Character.forDigit((b & 0xf0) >> 4, 16) +
                  Character.forDigit(b & 0x0f, 16))
  }
}

class RemoteBoss (serverName:String,clientName:String) extends Actor {
  var char_set:Array[Char] = char_table() //a Array storing all valid characters in ASCII characters (from 33 to 126)

  //generate a char_set
  private def char_table ():Array[Char]={ 
    var char_set:Array[Char] = new Array[Char](94)
    for (i<-33 to 126){
      char_set(i-33)=i.toChar   
    }
    return char_set //store all valid characters 
  } 
  def act() {
    alive(9018)
    register('remoteboss, self)
    var str:Array[Char] = new Array[Char](10)
    val boss = select(Node(serverName, 9011), 'boss) 
    val hostname = InetAddress.getLocalHost.getHostName()
    val client = hostname+".cise.ufl.edu"
    println(client)
    boss ! (client,1)
    var done:Int=0
    loop {
      react {
      	case (prefix:String, b:Int, n:Int, m:Int) =>
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
			  	//println(new_str+"\t"+sha_str)
	      	  }
	      	}
	      	boss ! (client)
	    case (signal: String)=>
	    	println("Stop working")	
	    	exit()   
      }
    }
  }
}


object Client{
	def main(args: Array[String]){
		val servername=if (args.length>0) args(0) else "lin114-11.cise.ufl.edu"
		val clientname=if (args.length>1) args(1) else "lin114-00.cise.ufl.edu"
		var r=new RemoteBoss(servername,clientname)
		r.start
	}
}
