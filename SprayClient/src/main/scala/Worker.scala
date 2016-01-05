import akka.actor._
import scala.concurrent._
import scala.concurrent.duration._
import akka.util.Timeout
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random
import akka.actor.ActorSystem
import scala.collection.mutable.Queue
import spray.client.pipelining._
import java.net.URI
import spray.json._
import spray.httpx.SprayJsonSupport
import spray.json.AdditionalFormats
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import org.json4s.Formats
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JInt
import DefaultJsonProtocol._
import java.security._
import spray.json.DefaultJsonProtocol._
import javax.crypto._
import org.apache.commons.codec.binary.Base64
import java.math.BigInteger; 
import java.security.KeyFactory; 
import java.security.interfaces.RSAPublicKey; 
import java.security.spec.RSAPublicKeySpec; 
import spray.util._
import scala.util.{Failure, Success}
import akka.event.Logging
import akka.io.IO
import FacebookJsonProtocol._
import akka.actor._
import spray.http._
import spray.httpx.SprayJsonSupport._
import spray.client.pipelining._
import akka.pattern.ask
import java.security._
import java.security.spec.X509EncodedKeySpec
import javax.crypto._
import org.apache.commons.codec.binary.Base64
import org.apache.commons.codec.binary.Hex
import javax.xml.bind.DatatypeConverter
import java.math.BigInteger;
import java.security.KeyFactory;
import java.security.Security;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAPrivateKeySpec;
import java.security.spec.RSAPublicKeySpec;
import javax.crypto.spec.SecretKeySpec;



case class UpdateUsersList(usersList: ListBuffer[ActorRef])
case class GetFriendsKeys()

class Worker( id: Int, users: Int, ip: String, port: String, system: ActorSystem) extends SprayJsonSupport with AdditionalFormats with Actor 
{   var usersList = new ListBuffer[ActorRef]
	val myid: Int = id
	val postMsg : String = "Hi from user "+myid.toString
	val pipeline = sendReceive
	val pipelineToken: HttpRequest => Future[Token] = (sendReceive ~> unmarshal[Token])
	val pipelineFriendsList: HttpRequest => Future[FriendsList] = (sendReceive ~> unmarshal[FriendsList])
	val pipelineAlbumsList: HttpRequest => Future[AlbumsList] = (sendReceive ~> unmarshal[AlbumsList])
	val pipelineStatus: HttpRequest => Future[StatusText] = (sendReceive ~> unmarshal[StatusText])
	val pipelinePhoto : HttpRequest => Future[ImageSource] = (sendReceive ~> unmarshal[ImageSource])
	val pipelineProfile: HttpRequest => Future[UserTimeline] = (sendReceive ~> unmarshal[UserTimeline])
	val pipelineAuthentication: HttpRequest => Future[AuthMessage] = (sendReceive ~> unmarshal[AuthMessage])
	val pipelinePublicKey: HttpRequest => Future[FriendPublicKey] = (sendReceive ~> unmarshal[FriendPublicKey]) 
	val pipelineGetFriendsAESKeyTable: HttpRequest => Future[FriendAESKeyTable] = (sendReceive ~> unmarshal[FriendAESKeyTable])
	

	
	var keyPair: KeyPair = null
	var keygen : KeyPairGenerator = KeyPairGenerator.getInstance("RSA")
	keygen.initialize(2048)
	keyPair=keygen.genKeyPair()
	var pubKey: PublicKey = keyPair.getPublic()
	var privKey: PrivateKey = keyPair.getPrivate()
	var myAcref :ActorRef = null
	var statuskeyList:ListBuffer[StatusKey] = new ListBuffer
	var imagekeyList:ListBuffer[ImageKey] = new ListBuffer
	var tracker : Int =0
	var photoNum : Int = 0
	var albumNum : Int = 0
	var albumsList: ListBuffer[String]= new ListBuffer
	var keyMap = new HashMap[String, String]

def generateSymetricKey():String = {
  		var generator = KeyGenerator.getInstance("AES");
		generator.init(128);
		var key = generator.generateKey();
		Base64.encodeBase64String(key.getEncoded());
  	}

	def encryptWithAESKey(data:String,key:String):String  = {
		var secretKey = new SecretKeySpec(Base64.decodeBase64(key),"AES");
		var cipher = Cipher.getInstance("AES");
		cipher.init(Cipher.ENCRYPT_MODE, secretKey);
		var newdata = cipher.doFinal(data.getBytes());
		Base64.encodeBase64String(newdata);
	}

	def decryptWithAESKey(data: String, key:String) = {
		var cipher = Cipher.getInstance("AES");
		var secretKey = new SecretKeySpec(Base64.decodeBase64(key), "AES");
		cipher.init(Cipher.DECRYPT_MODE, secretKey);
		var newdata = cipher.doFinal(Base64.decodeBase64(data.getBytes()));
		new String(newdata);
	}
	def encrypt(data:String,privkey:PrivateKey):String = {
		var cipher = Cipher.getInstance("RSA");
		cipher.init(Cipher.ENCRYPT_MODE, privkey);
		var cipherText = cipher.doFinal(data.getBytes());
		var temp = Base64.encodeBase64String(cipherText);
		temp
	}

	def decrypt(data :String,  pubkey:PublicKey):String  ={
		var decryptedText = Base64.decodeBase64(data)
		var cipher = Cipher.getInstance("RSA");

		cipher.init(Cipher.DECRYPT_MODE, pubkey);
		decryptedText = cipher.doFinal(decryptedText);

		new String(decryptedText);
	}
	def decodePublicKey(encodedKey: String): PublicKey = { 
    	var publicBytes = Base64.decodeBase64(encodedKey);
		var keySpec = new X509EncodedKeySpec(publicBytes);
		var keyFactory = KeyFactory.getInstance("RSA");
		var pubKey = keyFactory.generatePublic(keySpec);
		pubKey   
  	}
	var postScheduler:Cancellable = new Cancellable {override def isCancelled: Boolean = false
      override def cancel(): Boolean = false
	}
	var profileScheduler:Cancellable = new Cancellable {override def isCancelled: Boolean = false
      override def cancel(): Boolean = false
	}
	var pageScheduler:Cancellable = new Cancellable {override def isCancelled: Boolean = false
      override def cancel(): Boolean = false
	}
	var friendScheduler:Cancellable = new Cancellable {override def isCancelled: Boolean = false
      override def cancel(): Boolean = false
	}
	var photoReqScheduler:Cancellable = new Cancellable {override def isCancelled: Boolean = false
      override def cancel(): Boolean = false
	}

	updatePublicKey()

	def updatePublicKey() ={
		var keypub = (Base64.encodeBase64String(pubKey.getEncoded()).toString)
			
			pipeline(Post("http://" + ip + ":" + port + "/updatePublicKey", s"""{
			"uid": $myid,
			"pubKey": "$keypub"		
			}""".asJson.asJsObject))
	}


	def postStatus() ={

		var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){

								var keyStatus = generateSymetricKey()
								var start: Int =0
								var end: Int = Random.nextInt(postMsg.length())		
								var text: StatusText = new StatusText(encryptWithAESKey(postMsg,keyStatus))
								var time: Double = System.currentTimeMillis().toDouble
								var uid = myid
								var s :String = "STATUS"
								tracker = tracker+1
								var statusID : String = s+tracker.toString
								//println(statusID)
								keyMap(statusID)= encrypt(keyStatus,privKey)
								var encKey = encrypt(keyStatus,privKey)
								println("Status Keys:"+ myid +""+keyMap)
								var editable: String ="yes"
								var statuskey = new StatusKey(uid, time, editable, statusID,encKey)
								statuskeyList+=statuskey
								var Status = new Status(statuskey, text)		

								pipeline(Post("http://" + ip + ":" + port + "/postStatus", Status))
	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	


	}
	
	def postPhoto() ={

				var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){

							photoNum = photoNum+1
							var photoId : String = "IMG"+photoNum.toString
							var albumNum: Int = Random.nextInt(20)
							var keyPhoto = generateSymetricKey()
							var albumId : String = "Album"+albumNum.toString
							var time: Double = System.currentTimeMillis().toDouble
							var imagekey = new ImageKey(myid, time, albumId, photoId)
							imagekeyList += imagekey
							keyMap(photoId)=encrypt(keyPhoto,privKey)
							var s: String = encryptWithAESKey("Cloud",keyPhoto)
							var source : ImageSource = ImageSource(s)

							var Image = new Image(imagekey, source)

							pipeline(Post("http://" + ip + ":" + port + "/postPhoto", Image))
	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}


	}
	
	def postAlbum() ={
		var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			albumNum = albumNum+1
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			println(encToken)

			       var albumId : String = "Album"+albumNum.toString
			       albumsList += albumId
			       var Album = new Album(myid, albumId, encToken)
			       pipeline(Post("http://" + ip + ":" + port + "/postAlbum", Album))


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	




	}
	
		
	def getProfile() = 
	{	
		var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){
	      					var userID = new UserID(myid)

							var result2: Future[UserTimeline]= pipelineProfile{Get("http://" + ip + ":" + port + "/getProfile" , userID)}

						  	result2.onComplete {
						    	case Success(usertimeline)=>
						      	println("The profile/user's timeline is: "+ usertimeline)	  
						      	var timeline = usertimeline.postsList
						      	for(i <- timeline){
						      		//println(x.statuskey)
						      		var statKey = i.statuskey
						      		var encText1 = i.status
						      		var encText = encText1.statustext
						      		var mystatusID = statKey.statusID
						      		var timelineKey = decrypt(keyMap(mystatusID),pubKey)
						      		//println(timelineKey)
						      		var decText = decryptWithAESKey(encText,timelineKey)
						      		println("*****"+decText)

						      	}
						      	   

						    	case Failure(error) =>
						      	println("Couldn't get profile")	      
						  	}

						
	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	
		

	}

	def getStatus() =	
	{
	var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){

										var len = statuskeyList.length
										var temp = Random.nextInt(len)
										var statuskey = statuskeyList(temp)
										val result2 = pipelineStatus(Get("http://" + ip + ":" + port + "/getStatus", statuskey))
										  result2.onComplete {
										    case Success(StatusText(status))=>
										    //println(statuskey.statusID)
										    var keyDecrypt = decrypt(keyMap(statuskey.statusID),pubKey)
										    var mydecrypt = decryptWithAESKey(status, keyDecrypt)
										      println("Status: "+ mydecrypt)		      

										    case Failure(error) =>
										      println("Couldn't get status")
										      
										  }
									      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}		


	}
	

	
	def getFriendsList() = 
	{		var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){
	      						var userID = new UserID(myid)		
								 val result2: Future[FriendsList] = pipelineFriendsList(Get("http://" + ip + ":" + port + "/getFriendsList", userID))
								 result2.onComplete {
								    case Success(friendsListData)=>		      
								      println("The friendsList is: "+ friendsListData)
								      //println(friendsListData.friendsList)		      

								    case Failure(error) =>
								      println("Couldn't get friendsList")
								      
								  }


	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	

	}

	def getAlbums() = 
	{			var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){
	      							var userID = new UserID(myid)		
									 val result2: Future[AlbumsList] = pipelineAlbumsList(Get("http://" + ip + ":" + port + "/getAlbums", userID))
									 result2.onComplete {
									    case Success(albumsData)=>
									      var albums = albumsData.albumsList		      
									      println("The albumsList is: "+ albumsData)
									      		      

									      case Failure(error) =>
									      println("Couldn't get albums!")
									      
									  }

	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	

	}

	def getPhoto() =	
	{			var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){
	      							var len = imagekeyList.length
									var temp = Random.nextInt(len)
									var imagekey = imagekeyList(temp)
									val result2 = pipelinePhoto(Get("http://" + ip + ":" + port + "/getPhoto", imagekey))
									  result2.onComplete {
									    case Success(ImageSource(image))=>
									    
									    var keyDecrypt = decrypt(keyMap(imagekey.photoID),pubKey)
									    var mydecrypt = decryptWithAESKey(image, keyDecrypt)
									      println("The image is: "+ mydecrypt)		      

									    case Failure(error) =>
									      println("Couldn't get status")
									      
									  }


	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}


	}

	def getFriendsProfile() =	
	{


		var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){

	      							var fid : Int =0
		implicit val timeout = Timeout(5000.millis)
		var userID = new UserID(myid)		
		 val result2: Future[FriendsList] = pipelineFriendsList(Get("http://" + ip + ":" + port + "/getFriendsList", userID))
		 result2.onComplete {
		    case Success(friendsListData)=>		      

		      var friendsList =	friendsListData.friendsList
		      var temp = Random.nextInt(friendsList.length)
		  	  var friendID = friendsList(temp)
		  	  fid = friendID

		  	   var friend = (usersList(friendID))



		  	   //println("****friendskeys:"+friendskeys)
		  	   var friendUID = UserID(fid)
		  	   val result3: Future[FriendAESKeyTable] = pipelineGetFriendsAESKeyTable(Get("http://" + ip + ":" + port + "/getFriendsAESKeyTable", friendUID))
		  	   //val friendskeys = Await.result(result3, timeout.duration).asInstanceOf[HashMap[String,String]]
		  	   result3.onComplete{
		  	   	case Success(tabledata) =>
		  	   		var friendskeys = tabledata.tableAES
		  	   		//var statusID = tabledata.statusID		  	   
		  	   val result5 = pipelinePublicKey(Get("http://" + ip + ":" + port + "/getFriendsPublicKey", friendUID))
		  	   result5.onComplete{
		  	   	case Success(fpubKey)=>
		  	   	var friendPubKey: PublicKey = decodePublicKey(fpubKey.publickey)
				val result4 = pipelineProfile(Get("http://" + ip + ":" + port + "/getProfile", friendUID))
							result4.onComplete {
							    case Success(friendsTimeLine)=>
							      println("Friends timeline : "+ friendsTimeLine)
							      var timeline = friendsTimeLine.postsList
							      for(i <- timeline){
	      		
	      								var statKey = i.statuskey
	      								var encText1 = i.status
	      								var encText = encText1.statustext
	      								var mystatusID = statKey.statusID
	      								var timelineKey = decrypt(statKey.enckey,friendPubKey)
	      								//println(timelineKey)
	      								var decText = decryptWithAESKey(encText,timelineKey)
	      								println(decText)

	      							}	      

							    case Failure(error) =>
							      println("Couldn't get friends timeline!")
							      
							  }
							   case Failure(error) =>
							      println("Couldn't get friends timeline!")

}
case Failure(error) =>
							      println("Couldn't get friends timeline!")
}

		    case Failure(error) =>
		      println("Couldn't get friendsList")		      
		  }


	      				}

	      				case Failure(error) =>
	      				println("Client couldn't be authenticated!")
	      			}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}		

		


	}

	def getHomePage()= {

				var userID = new UserID(myid)
		var result: Future[Token]= pipelineToken{Get("http://" + ip + ":" + port + "/getToken" , userID)}	
	  	result.onComplete {
	    	case Success(Token(token))=>
	      	println("Token obtained")	
	      			var decToken = token.toString
	      			var encToken = encrypt(decToken,privKey)
	      			//println(encToken)
	      			var clientToken = ClientToken(myid, encToken)
	      			var result1: Future[AuthMessage]= pipelineAuthentication{Get("http://" + ip + ":" + port + "/clientAuthentication" , clientToken)}

	      			result1.onComplete {
	      				case Success(message)=>
	      				//println(message)
	      				var authMsg = message.authmessage
	      				if(authMsg=="yes"){
	      							implicit val timeout = Timeout(5000.millis)
		var userID = new UserID(myid)		
		 val result2: Future[FriendsList] = pipelineFriendsList(Get("http://" + ip + ":" + port + "/getFriendsList", userID))
		 result2.onComplete {
		    case Success(friendsListData)=>		      

		      var friendsList =	friendsListData.friendsList
		      //var temp = Random.nextInt(friendsList.length)
		  	  //var friendID = friendsList(temp)
		  	  //fid = friendID
		  	  for (friendID <- friendsList){
		  	   var friend = (usersList(friendID))

		  	   val result3 = friend ? GetFriendsKeys()
		  	   val friendskeys = Await.result(result3, timeout.duration).asInstanceOf[HashMap[String,String]]
		  	   //println("****friendskeys:"+friendskeys)
		  	   var friendUID = UserID(friendID)
		  	   val result5 = pipelinePublicKey(Get("http://" + ip + ":" + port + "/getFriendsPublicKey", friendUID))
		  	  result5.onComplete{
		  	  	case Success(fpubKey) =>
		  	  		var friendPubKey: PublicKey = decodePublicKey(fpubKey.publickey)
				val result4 = pipelineProfile(Get("http://" + ip + ":" + port + "/getProfile", friendUID))
							result4.onComplete {
							    case Success(friendsTimeLine)=>
							      println("Friends timeline : "+ friendsTimeLine)
							      var timeline = friendsTimeLine.postsList
							      for(i <- timeline){
	      		
	      								var statKey = i.statuskey
	      								var encText1 = i.status
	      								var encText = encText1.statustext
	      								var mystatusID = statKey.statusID
	      								var timelineKey = decrypt(friendskeys(mystatusID),friendPubKey)
	      								//println(timelineKey)
	      								var decText = decryptWithAESKey(encText,timelineKey)
	      								println(decText)

	      							}	      

							    case Failure(error) =>
							      println("Couldn't get friends timeline!")
							      
							  }
							  	case Failure(error) =>
							      println("Couldn't get friends timeline!")

						}
					}
		    	case Failure(error) =>
		     	println("Couldn't get friendsList")
		     	      
		  		}

	      	}

	      	case Failure(error) =>
	      		println("Client couldn't be authenticated!")
	      	}


	  	      

	    	case Failure(error) =>
	      	println("Couldn't get token")	      
	  	}	



	}




	
	def begin()={
		var value : Int = myid % 4
		var x: Double =0
		var y: Double= 0
		value match
		{
			case 0 => 
			{
				y = 0.8
			}
			case 1 => 
			{
				y = 0.325 
			}
			case 2 =>
			{
				y = 0.125 
			}
			case 3 => 
			{
				y = 0.45 
			}
         
		}
		//friendScheduler = context.system.scheduler.scheduleOnce((myid.toDouble/users.toDouble).toInt seconds)(getFriendsList())
		//pageScheduler = context.system.scheduler.scheduleOnce((myid.toDouble/users.toDouble*10).toInt seconds)(getPage())
		//postScheduler = context.system.scheduler.schedule(0 seconds,y seconds)(postStatus())

		//getFriendsList()
		postStatus()
		Thread.sleep(10000)
		//getStatus()
		//postPhoto()
		//Thread.sleep(10000)
		//getPhoto()
		//postAlbum()
		//Thread.sleep(10000)
		//getAlbums()
		//getProfile()
		getFriendsProfile()
		//getHomePage()

	}
	
	def receive =
	{
		case "begin" => begin()
		case UpdateUsersList(usersList: ListBuffer[ActorRef]) =>
		this.usersList = usersList
		//println(this.usersList)

		case GetFriendsKeys() =>
		 sender ! keyMap
		case "stop" => 
		  postScheduler.cancel()
		  profileScheduler.cancel()
		  pageScheduler.cancel()
		  friendScheduler.cancel()
		  photoReqScheduler.cancel()
	}
	
}
