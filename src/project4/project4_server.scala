import akka.actor.{ActorSystem, Actor, Props,ActorRef}
import scala.collection.mutable.ArrayBuffer
import java.security.MessageDigest
import akka.util.Timeout
import scala.concurrent.Await
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import java.util.Date
import java.util.Formatter
import java.util.Calendar
import java.text.SimpleDateFormat
import com.typesafe.config.ConfigFactory
import Array._
import scala.util.Random
import scala.util.control.Breaks._
import scala.collection.mutable.ListBuffer
import common._

object project4_server {

  sealed trait Message
  case object BuildFinshed extends Message
  case object InitJob extends Message
  case object IsReady extends Message
  case object IsBossReady extends Message
  case object SendTweet extends Message
  case object ViewTweet extends Message
  case object BuildRelation extends Message

  case class processWorkload(user_id: Long, ref_id: String, time_stamp: Date, workerArray: ArrayBuffer[ActorRef])
  case class processTweet(user_id: Long, time_stamp: Date, ref_id: String, workerArray: ArrayBuffer[ActorRef])
  case class getFollowers(user_id: Long, time_stamp: Date, ref_id: String, followers: ArrayBuffer[Long], workerArray: ArrayBuffer[ActorRef])
  case class updateHomeTimeline(user_id: Long, time_stamp: Date, ref_id: String)
  case class buildFollowers(user_id: Long)
 
  case class getHomeTimeline(user_id: Long, user_actor: ActorRef)

  case class getUserTimeline(user_id: Long, user_actor: ActorRef)

  val numPerWorker: Int = 200
  val numWorkers: Int = 100

  var tweetStorage: Map[String, Tweet] = Map()
  def dateToString(current: Date): String = {
    val formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    val s: String = formatter.format(current)
    return s
  }
 
  case class TimeElement(ref_id: String, time_stamp: Date)

  def insertIntoArray(userTimeline: ArrayBuffer[TimeElement], ref_id: String, time_stamp: Date) {
    if (0 == userTimeline.size) {
      userTimeline.append(TimeElement(ref_id, time_stamp))
    } else {
      var index = 0
      while (index < userTimeline.size && time_stamp.compareTo(userTimeline(index).time_stamp) < 0)
        index += 1
      userTimeline.insert(index, TimeElement(ref_id, time_stamp))
    }
  }
  
  
  

  def genRandFollowerCount(): Int = {
    var prob: ArrayBuffer[Double] = new ArrayBuffer
    //    prob(0) = 0.811
    //    prob(1) = 0.063
    //    prob(2) = 0.092
    //    prob(3) = 0.017
    //    prob(4) = 0.015
    //    prob(5) = 0.0015
    //    prob(6) = 0.0005

    prob.append(0.811)
    prob.append(0.874)
    prob.append(0.966)
    prob.append(0.983)
    prob.append(0.998)
    prob.append(0.9995)
    prob.append(1.000)
    
    var randProb = Random.nextDouble()
    var index = prob.size
    breakable {
      for (i <- 0 until prob.size) {
        if (prob(i) >= randProb) {
          index = i
          break
        }
      }
    }

    if (0 == index) {
      return genRandNumber(0, 50)
    }else if(1 == index) {
      return  genRandNumber(51, 100)
    }else if(2 == index) {
      return genRandNumber(101, 500)
    }else if(3 == index) {
      return genRandNumber(501, 1000)
    }else if(4 == index) {
      return genRandNumber(1001, 5000)
    }else if(5 == index) {
      return genRandNumber(5001, 10000)
    }else if(6 == index) {
      return genRandNumber(10001, 100000)
    }else
      return 100001 + Random.nextInt().abs
      
  }
  

  def genRandNumber(first: Int, last: Int): Int = {
    first + Random.nextInt(last-first)
  }



  def genRandExceptCur(first: Long, last: Long, current: Long, randomNumber: Int): ArrayBuffer[Long] = {
    var followers: ArrayBuffer[Long] = new ArrayBuffer()
    followers.append(current)

    var randomFollower: Long = 0
    var counter: Int = 0
    //print("cur: " + current)
    while (counter < randomNumber) {
      randomFollower = genRandNumber(first.toInt, last.toInt).toLong
      while (followers.contains(randomFollower))
        randomFollower = genRandNumber(first.toInt, last.toInt).toLong
      followers.append(randomFollower)
      //print(" " + randomFollower)

      counter += 1
    }
    followers.remove(0)
    followers

  }
  
  
  class scheduleActor(numWorkers: Int, workerArray: ArrayBuffer[ActorRef]) extends Actor {
    var count: Int = 0
    var ready: Boolean = false
    val i: Long = 0
    var countFinished : Long = 0
    def receive = {
      
      case msg:String => {
        println(sender + " " + sender.path.name + " " + msg)
      }
            
      case getTweet(t) => {
//        println(sender.path.name + " " + t.text )
        //save message
        tweetStorage += t.ref_id -> t
        workerArray(count) ! processWorkload(t.user_id ,t.ref_id, t.time_stamp , workerArray)
        count = count +1
        if(count == numWorkers){
          count = 0
        }
      }
      
      case viewHomeTimeline(i) => {
        workerArray((i/numPerWorker).toInt) ! getHomeTimeline(i, sender)
      }
      case viewUserTimeline(i) => {
        workerArray((i/numPerWorker).toInt) ! getUserTimeline(i, sender)
      }
      
      case BuildRelation => {
//        var randomFollowers = 0
        for(i<-0 until numWorkers*numPerWorker) {
//          var followers = ArrayBuffer[Long]() 
//          randomFollowers = genRandFollowerCount()
//          if(randomFollowers >= numWorkers*numPerWorker)
//            randomFollowers = numWorkers*numPerWorker - 1
//          println(randomFollowers)
//          followers = genRandExceptCur(0, numWorkers*numPerWorker, i, randomFollowers)
          workerArray((i/numPerWorker).toInt) ! buildFollowers(i)
        }
      }
      
      case BuildFinshed => {
        countFinished = countFinished + 1
        println(countFinished)
        if(countFinished >= numWorkers*numPerWorker)
          ready = true
      }
      
      case IsBossReady => {
        sender ! ready
      }
      
    }
    
  }
  
  class workerActor() extends Actor {
    var userTimeline = new Array[ArrayBuffer[TimeElement]](numPerWorker)
    var homeTimeline = new Array[ArrayBuffer[TimeElement]](numPerWorker)
    

    
    var followers = new Array[ArrayBuffer[Long]](numPerWorker)
    
    var ready: Boolean = false
    
    def receive = {
      case processWorkload(user_id, ref_id, time_stamp, workerArray) => {
        workerArray((user_id/numPerWorker).toInt) ! processTweet(user_id, time_stamp, ref_id, workerArray)
      }
      
      case processTweet(user_id, time_stamp, ref_id, workerArray) => {
        insertIntoArray(userTimeline((user_id-numPerWorker*self.path.name.toInt).toInt), ref_id, time_stamp)
 //       userTimeline((user_id-numPerWorker*self.path.name.toInt).toInt).append(ref_id)
//        var userT = userTimeline((user_id-numPerWorker*self.path.name.toInt).toInt)
//        println(user_id + " send tweet: " + tweetStorage(ref_id).text  )
        sender ! getFollowers(user_id, time_stamp, ref_id, followers((user_id-numPerWorker*self.path.name.toInt).toInt), workerArray)
      }
      
      case getFollowers(user_id, time_stamp, ref_id, followers, workerArray) => {
        for(node<-followers){
          workerArray((node/numPerWorker).toInt) ! updateHomeTimeline(node, time_stamp, ref_id)
        }
      }
      
      case updateHomeTimeline(user_id, time_stamp, ref_id) => {
        insertIntoArray(homeTimeline((user_id-numPerWorker*self.path.name.toInt).toInt), ref_id, time_stamp)
//        homeTimeline((user_id-numPerWorker*self.path.name.toInt).toInt).append(ref_id)
//        println(user_id + " following: " + tweetStorage(ref_id).text  )

      }
      
      case buildFollowers(user_id) => {
        var randomFollowers = genRandFollowerCount()
        if (randomFollowers >= numWorkers * numPerWorker)
          randomFollowers = numWorkers * numPerWorker - 1
        //println(randomFollowers)      
        followers((user_id-numPerWorker*self.path.name.toInt).toInt) = genRandExceptCur(0, numWorkers * numPerWorker, user_id, randomFollowers) 
        sender ! BuildFinshed
      }
      
      case getHomeTimeline(i, user_actor) => {
        var line = homeTimeline((i-numPerWorker*self.path.name.toInt).toInt)
        var userLine = line.dropRight(line.size-10)
        println(i + " homeTimeline")
        for(ele<-userLine){
          println(tweetStorage(ele.ref_id ).user_id   + " at " + dateToString(tweetStorage(ele.ref_id ).time_stamp)  + " : "+ tweetStorage(ele.ref_id ).text )
        }
      }
 
      case getUserTimeline(i, user_actor) => {
        var timeLine =  ArrayBuffer[String]()
        var line = userTimeline((i-numPerWorker*self.path.name.toInt).toInt)
        var userLine = line.dropRight(line.size-10)
        
        println(i + " userTimeline")
        for(ele<-userLine){
          var message = tweetStorage(ele.ref_id ).user_id   + " at " + dateToString(tweetStorage(ele.ref_id ).time_stamp)  + " : "+ tweetStorage(ele.ref_id ).text
          println(message)
          timeLine.append(message)
        }
        user_actor ! displayUserTimeLine(timeLine)
      }
            
      case InitJob => {
        for(i <- 0 until numPerWorker){
          userTimeline(i) = new ArrayBuffer()
          homeTimeline(i) = new ArrayBuffer()
          followers(i) = new ArrayBuffer()
        }
        ready = true
      }
      
     case IsReady => {
       sender ! ready  
     }
        
    }
    
  }
  
//  class clientActor(boss:ActorRef) extends Actor {
//    def receive = {
//      case SendTweet => {
//        val t = new Tweet(self.path.name.substring(6).toInt, "what are you doing?", getCurrentTime)
//        boss ! getTweet(t)
//      }
//      
//      
//      case ViewTweet => {
//        //boss ! viewHomeTimeline(self.path.name.substring(6).toInt)
//        boss ! viewUserTimeline(self.path.name.substring(6).toInt)
//      }
//          
//    }
//
//    
//  }
  
  def main(args: Array[String]) {
//    val numWorkers = if (args.length > 0) args(0) toInt else 100  // the number of workers in server
    var workerArray = ArrayBuffer[ActorRef]()
//    var clientArray = ArrayBuffer[ActorRef]()
    val system = ActorSystem("TwitterSystem")
    val numUsers = numWorkers * numPerWorker
    var counter: Int =0
    while(counter < numWorkers){
      val worker = system.actorOf(Props(classOf[workerActor]), counter.toString)
      workerArray.append(worker)
      counter += 1
    }
    val boss = system.actorOf(Props(classOf[scheduleActor],numWorkers,workerArray), "boss")
    

//    counter = 0
//    while(counter < numUsers){
//      val client = system.actorOf(Props(classOf[clientActor],boss), "client" + counter.toString)
//      clientArray.append(client)
//      counter += 1
//    }
    
    for (node <- workerArray) {
      node ! InitJob
      implicit val timeout = Timeout(20 seconds)
      var ready: Boolean = false
      while (!ready) {
        val future = node ? IsReady
        ready = Await.result(future.mapTo[Boolean], timeout.duration)
      }
    }
    
    boss ! BuildRelation
    implicit val timeout = Timeout(100 seconds)
    var ready: Boolean = false
    while (!ready) {
      val future = boss ? IsBossReady
      ready = Await.result(future.mapTo[Boolean], timeout.duration)
    }
    println("Build Finished")
    boss ! "server start"
//    for(i<-0 until numUsers){
//      var send = math.random < 0.6
//      if(send)
//        system.scheduler.schedule(1 seconds, 1 seconds, clientArray(i), SendTweet ) 
//    }
    

//    system.scheduler.scheduleOnce(10 seconds) {
//      clientArray(0) ! ViewTweet
//      clientArray(1) ! ViewTweet
//    }
//    
//    system.scheduler.scheduleOnce(100 seconds) {
//      clientArray(0) ! ViewTweet
//      clientArray(1) ! ViewTweet
//    }
//    
//    system.scheduler.scheduleOnce(100 seconds) {
//    	system.shutdown
//    }
  }

}