import akka.util.Timeout
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.ActorSystem
import akka.actor.ActorRef
import akka.actor.Props
import akka.pattern.ask
import akka.actor._
import org.json4s.Formats
import org.json4s.DefaultFormats
import spray.httpx.Json4sSupport
import org.json4s.JsonAST.JObject
import spray.routing._
import java.security._
import spray.util._
import scala.util.{Failure, Success}
import FacebookJsonProtocol._
import spray.json.DefaultJsonProtocol._

//case class Post(statuskey:StatusKey, status:String)
case class PostPhoto(uid: Int,  albumId: String, photoId: String, source:String)
case class PostAlbum(uid: Int,  albumId: String, photoId: String, source:String)
case class PostPubKey(uid : Int, pubKey: String)
object Server extends HttpService with Json4sSupport with SimpleRoutingApp {

  implicit def json4sFormats: Formats = DefaultFormats
  var DBList: List[ActorRef] = List()
  implicit val timeout = Timeout(5000.millis)
  implicit val system = ActorSystem("FacebookServer")
  val numServerDB = 4
  val friendsLimit = 5


  var cumulativePostsCount: Int = 0
  var cumulativeRequestsCount: Int = 0


  def main(args: Array[String]) {

    var numUsers: Int = 0
    var numCli: Int = 0
    var myIP: String = "localhost"
    var myPort: Int = 0
    var runTime:Int = 0

    if (args.length == 4) {
      numUsers = (args(0).toInt)      
      myIP = args(1)
      myPort = args(2).toInt
      runTime = args(3).toInt
      for (x <- 0 to numServerDB - 1) {
        var DB = system.actorOf(Props(new ServerDB(numUsers, system, x, numServerDB, friendsLimit)), x.toString)
        if (!DBList.contains(DB)) {
          DBList = DBList :+ DB
        }
      }

      for (DB <- DBList) {
        DB ! INIT()
      }

      startServer(interface = myIP, port = myPort) {        
        getProfileRoute ~ 
        postsRoute ~ 
        getFriendsListRoute ~       	
      	postPhotoRoute ~
	      postAlbumRoute ~
	      getPhotoRoute ~
        updatePublicKeyRoute~
        getTokenRoute~
        getStatusRoute~
        clientAuthenticationRoute~
        getAlbumsRoute~
        getFriendsPublicKeyRoute~
        getFriendsAESKeyTableRoute

      }

      var timer = system.actorOf(Props(new timerActor(system, DBList,runTime)), "timerActor")
      timer ! "start"

      
      val postScheduler = system.scheduler.schedule(0 seconds, 5 seconds)(println("Server Running..."))

    } else {
      println("Usage : Server.scala <Number of Users>  <server IP> <port number> <run time for server>")
    }

  }


  
  val getTokenRoute = {   
     get{
      path("getToken" ){       
          entity(as[UserID]) { userIDObj =>            
          complete {   
            var result = (DBList(userIDObj.userID % numServerDB) ? GenerateToken(userIDObj.userID)).mapTo[Token]              
              //println(res)
              //println(res.asInstanceOf[AnyRef].getClass.getSimpleName)
            result.onComplete {
              case Success(token) => println("")                                  
              case Failure(ex) => println("seriously!")
            }
            result              
          }
          
        }
      }
    }
  }

  val getFriendsListRoute = {
    get {
      path("getFriendsList") {
        entity(as[UserID]) { userIDObj =>
          
          complete {            
            var result = (DBList(userIDObj.userID % numServerDB) ? requestFriendsList(userIDObj.userID)).mapTo[FriendsList]
            result.onComplete {
              case Success(friendsList) => println(friendsList)

              case Failure(ex) => println("seriously!")
            }
            result
          }
        }
      }
    }
  }

  val postsRoute =
    path("postStatus") {
      post {
        entity(as[Status]) { StatusObj =>
          complete {
              var uid:Int = StatusObj.statuskey.userID
              
            DBList(uid % numServerDB) ! Status(StatusObj.statuskey, StatusObj.status)
            StatusObj
          }
        }
      }
    }

  val getStatusRoute =
    get {
      path("getStatus") {   
           entity(as[StatusKey]) { StatusKeyObj =>
            complete {
                var uid:Int = StatusKeyObj.userID
               
                var result= (DBList(uid % numServerDB) ? RequestStatus(StatusKeyObj)).mapTo[StatusText]
                result.onComplete {
                  case Success(status) => println(status)
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }



  
  val getProfileRoute = {
    get {
      path("getProfile") {   
           entity(as[UserID]) { userIDObj =>
            complete {
                var uid:Int = userIDObj.userID
               
                var result= (DBList(uid % numServerDB) ? GetProfile(uid)).mapTo[UserTimeline]
                result.onComplete {
                  case Success(usertimeline) => println(usertimeline)
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }
    }

  val getAlbumsRoute = {
    get {
      path("getAlbums") {   
           entity(as[UserID]) { userIDObj =>
            complete {
                var uid:Int = userIDObj.userID
               
                var result= (DBList(uid % numServerDB) ? GetAlbums(uid)).mapTo[AlbumsList]
                result.onComplete {
                  case Success(albumslist) => println(albumslist)
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }
    }


  val getPhotoRoute =
    get {
      path("getPhoto") {   
           entity(as[ImageKey]) { ImageKeyObj =>
            complete {
                var uid:Int = ImageKeyObj.userID
               
                var result= (DBList(uid % numServerDB) ? requestPhoto(ImageKeyObj)).mapTo[ImageSource]
                result.onComplete {
                  case Success(photo) => println(photo)
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }

  val postPhotoRoute =
    path("postPhoto") {
      post {
        entity(as[Image]) { ImageObj =>
          complete {
              var uid:Int = ImageObj.imagekey.userID
              
            DBList(uid % numServerDB) ! Image(ImageObj.imagekey, ImageObj.imagesource)
            ImageObj
          }
        }
      }
    }

  val postAlbumRoute =
    path("postAlbum") {
      post {
        entity(as[Album]) { AlbumObj =>
          complete {
              var uid:Int = AlbumObj.userID
              
            DBList(uid % numServerDB) ! Album(uid, AlbumObj.albumid, AlbumObj.encryptedToken)
            AlbumObj
          }
        }
      }
    }








  val updatePublicKeyRoute =
    path("updatePublicKey") {
      post {
        entity(as[JObject]) { postObj =>
          complete {
              var jpost = postObj.extract[PostPubKey]
              
              var ownerID = jpost.uid
              
              var pubKey = jpost.pubKey
              //println("Server.scala updatePublicKey")
              //println(pubKey)
            DBList(ownerID % numServerDB) ! PutPublicKey(ownerID,  pubKey)
            pubKey.toString()
          }
        }
      }
    }

  val updateUsersListRoute =
     path("updateUsersList") {
      post {
        entity(as[UserAcref]) { UserAcrefObj =>
          complete {
            var uid:Int = UserAcrefObj.userID

            DBList(uid % numServerDB) ! UserAcref(UserAcrefObj.userID, UserAcrefObj.acref)
            UserAcrefObj
          }
        }
      }
    }

  val clientAuthenticationRoute=
      get {
      path("clientAuthentication") {   
           entity(as[ClientToken]) { ClientTokenObj =>
            complete {
                var uid:Int = ClientTokenObj.userID
               
                var result= (DBList(uid % numServerDB) ? ClientToken(uid, ClientTokenObj.encryptedToken)).mapTo[AuthMessage]
                result.onComplete {
                  case Success(authmessage) => println(authmessage)
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }

  val getFriendsPublicKeyRoute=
      get {
      path("getFriendsPublicKey") {   
           entity(as[UserID]) { UserIDObj =>
            complete {
                var uid:Int = UserIDObj.userID
               
                var result= (DBList(uid % numServerDB) ? GetFriendsPublicKey(uid)).mapTo[FriendPublicKey]
                result.onComplete {
                  case Success(friendsPublicKey) => println("")
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }


  val getFriendsAESKeyTableRoute=
      get {
      path("getFriendsAESKeyTable") {   
           entity(as[UserID]) { UserIDObj =>
            complete {
                var uid:Int = UserIDObj.userID
               
                var result= (DBList(uid % numServerDB) ? GetFriendsAESKeyTable(uid)).mapTo[FriendAESKeyTable]
                result.onComplete {
                  case Success(friendsPublicKey) => println("")
                                    
                  case Failure(ex) => println("seriously!")
                }
                result 
            }
          }
        }
      }   
 

}
