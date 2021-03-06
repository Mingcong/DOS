package common
import java.util.Date
import scala.collection.mutable.ArrayBuffer

case class viewHomeTimeline(user_id: Long)
case class viewUserTimeline(user_id: Long)
case class getTweet(t: Tweet)
case class displayUserTimeLine(userHomeTimeLine: ArrayBuffer[String])
case class Tweet(user_id: Long, text: String, time_stamp: Date, var ref_id: String)