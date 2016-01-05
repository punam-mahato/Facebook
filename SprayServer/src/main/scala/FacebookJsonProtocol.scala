import akka.actor.Actor
import spray.routing._
import spray.http._
import MediaTypes._
import akka.actor.Props
import spray.httpx.Json4sSupport
import spray.json._
import spray.httpx.Json4sSupport
import org.json4s.{DefaultFormats, Formats}
import org.json4s.CustomSerializer
import org.json4s.JsonAST.{JInt, JNull}
import scala.Tuple4
import akka.actor.ActorSystem
import akka.actor.ActorRef
import scala.collection.immutable.List

object FacebookJsonProtocol extends DefaultJsonProtocol {

implicit val tokenFormat = jsonFormat1(Token)
implicit val statuskeyFormat = jsonFormat5(StatusKey)
implicit val statusTextFormat = jsonFormat1(StatusText)
implicit val statusFormat = jsonFormat2(Status)

implicit val imagekeyFormat = jsonFormat4(ImageKey)
implicit val imageSourceFormat = jsonFormat1(ImageSource)
implicit val imageFormat = jsonFormat2(Image)

implicit val userIDFormat = jsonFormat1(UserID)
implicit val friendsListFormat = jsonFormat2(FriendsList)

implicit val albumFormat = jsonFormat3(Album)

implicit val userTimelineFormat = jsonFormat2(UserTimeline)

implicit val authmessageFormat = jsonFormat2(AuthMessage)
implicit val clientTokenFormat = jsonFormat2(ClientToken)
implicit val albumsListFormat = jsonFormat2(AlbumsList)
implicit val friendPublicKeyFormat = jsonFormat2(FriendPublicKey)
implicit val userAESKeyFormat = jsonFormat2(UserAESKey)

implicit val friendAESKeyTableFormat = jsonFormat2(FriendAESKeyTable)

}
  case class Token(token: String)
  case class StatusText(statustext: String)
  case class StatusKey(userID:Int, time:Double, editable:String, statusID:String, enckey:String)
  case class Status(statuskey:StatusKey, status:StatusText)
  case class UserAcref(userID:Int, acref: ActorRef)
  case class UserID(userID:Int)
  case class FriendsList(userID: Int, friendsList:List[Int])
  case class AlbumsList(userID:Int, albumsList:List[String])
  case class ImageKey(userID:Int, time:Double, albumID:String, photoID:String)
  case class ImageSource(imagesource: String)
  case class Image(imagekey: ImageKey, imagesource:ImageSource)
  case class UserTimeline(userID:Int, postsList:List[Status])

  case class Album(userID:Int, albumid:String, encryptedToken:String)
  case class AuthMessage(userID:Int, authmessage:String)
  case class ClientToken(userID:Int, encryptedToken:String)
  case class FriendPublicKey(userID:Int, publickey:String)
  case class UserAESKey(statusID:String, AESkey:String)
  case class FriendAESKeyTable(userID:Int, tableAES:List[UserAESKey])


