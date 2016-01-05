import akka.actor._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.actor.ActorSystem
import akka.actor.Props
import com.typesafe.config.ConfigFactory
import scala.collection.mutable.Queue
import spray.client.pipelining._
import java.net.URI
import spray.json._
import spray.httpx.SprayJsonSupport
import spray.json.AdditionalFormats
import org.json4s.Formats
import org.json4s.DefaultFormats
import DefaultJsonProtocol._
import scala.collection.mutable.ListBuffer
object Client
{

	def main(args: Array[String]) 
	{
		val system = ActorSystem("Facebook")
		var users : Int = args(0).toInt
		//var start_id : Int = args(1).toInt
		var ip : String = args(1)
		var port : String = args(2)
		var timeout : Int = args(3).toInt

		val minder = system.actorOf(Props(new ClientMinder(users, ip, port,system,timeout)),name = "ClientMinder")
		minder ! "begin"
	}
	

	var userList: ListBuffer[ActorRef] = new ListBuffer[ActorRef]
	class ClientMinder(users: Int, ip: String, port: String, system: ActorSystem, timeout: Int) extends Actor 
	{
	 var timer = system.actorOf(Props(new timeMinder(system,timeout)), "timeMinder")
	 def receive = 
	 {
		case "begin" =>
		{
			for ( i : Int <- 0 to users-1)
			{
			var acref = context.actorOf(Props(new Worker(i.toInt,users,ip,port,system)), name = i.toString)	
				userList += acref
				

	        }
	        for(child <- context.children) 
			{

				child ! "begin"
				child ! UpdateUsersList(userList)
			}
			timer ! "startTimer"
		}
		
		case "stop" =>
		{
			println("Total runtime finished: Timeout encountered!")
			for (child <- context.children) 
			{
				child ! "stop"
			}
			system.shutdown()
		}
	 }
    }
	
	class timeMinder(system : ActorSystem, timeout: Int) extends Actor
	{
		def receive =
		{
			case "startTimer" =>
				var timerStart = System.currentTimeMillis()
				while(System.currentTimeMillis() < timerStart + timeout * 1000) {
				
				}
				sender ! "stop"
				//system.shutdown
		}
	}
}
