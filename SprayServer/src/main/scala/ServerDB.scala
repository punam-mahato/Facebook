import akka.actor.{ ActorRef, ActorSystem, Props, Actor }
import scala.collection.mutable.HashMap
import scala.collection.mutable.ListBuffer
import scala.collection.mutable.Queue
import scala.collection.mutable.ArrayBuffer
import spray.json._
import DefaultJsonProtocol._
import java.security.PublicKey
import FacebookJsonProtocol._
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
import java.math.BigInteger





//case class postReceived(statuskey:StatusKey, text:String)
case class updateTimeline(postText: String, userID: Int, friendsublist: ListBuffer[Int])
case class requestProfile(userID: Int)
case class requestPage(userID: Int)
case class requestFriendsList(userID: Int)
case class photoReceived(userID: Int,  albumId: String, photoId: String, source:String)
case class PutAlbum(userID: Int,  albumId: String,encryptedToken:String)
case class requestPhoto(imagekey: ImageKey)
case class INIT()
case class STOP()
case class PutPublicKey(userID:Int, pubKey:String)
case class GenerateToken(userID:Int)
case class RequestStatus(statuskey: StatusKey)
case class GetProfile(userID: Int)
case class GetAlbums(userID:Int)
case class GetFriendsPublicKey(userID:Int)
case class GetFriendsAESKeyTable(userID:Int)




class ServerDB(numUsers: Int, ac: ActorSystem, myID: Int, numServerDBs: Int, friendsLimit: Int) extends Actor {

// Metadata

  var tokenTable = new HashMap[Int,ListBuffer[String]]
  var friendsListTable = new HashMap[Int, ListBuffer[Int]]
  var albumsTable = new HashMap[Int, ListBuffer[String]]
  var publicKeysTable = new HashMap[Int, String]
  var userAESTable = new HashMap[Int,ListBuffer[UserAESKey]]
  


// Data
  var statusTable = new HashMap[StatusKey, StatusText]
  var photosTable = new HashMap[ImageKey, ImageSource]




  def receive = {
    case INIT() => init(sender)




    case ClientToken(userID:Int, encryptedToken:String) =>
      var key = publicKeysTable(userID)
          var tknList = tokenTable(userID).toList
          var decToken = decrypt(encryptedToken,key)
          if(tknList.contains(decToken)){
            var msg = "yes"
            var authMess = new AuthMessage(userID,msg)
            sender ! authMess
          }

    case Status(statuskey: StatusKey, text:StatusText) =>
        var enckey = statuskey.enckey
        var encID = statuskey.statusID
        var keyObj = UserAESKey(encID,enckey)
        var l1 = userAESTable.getOrElse(statuskey.userID,null)
          if(l1== null){
            var newl1 =new ListBuffer[UserAESKey]
            newl1 += keyObj
            userAESTable(statuskey.userID) = newl1
          }
          else{
            userAESTable(statuskey.userID) += keyObj
          }
          println("AES keys table:"+userAESTable)

        statusTable(statuskey)= text
        println(statusTable)

	     


      case Image(imagekey:ImageKey, source: ImageSource) =>
      photosTable(imagekey) = source
      println(photosTable)

      case Album(userID:Int, albumid: String, encryptedToken:String) =>
        var key = publicKeysTable(userID)
          var tknList = tokenTable(userID).toList
          var decToken = decrypt(encryptedToken,key)
          if(tknList.contains(decToken.toInt))
          
          {var album = albumsTable.getOrElse(userID, null)
          if(album == null){
            var newalbum =new ListBuffer[String]
            newalbum += albumid
            albumsTable(userID) = newalbum
          }
          else{
            albumsTable(userID) += albumid
          }
          println("\nalbumsTable")
          println("Server Number: "+ myID)
          println(albumsTable)}

    case GenerateToken(userID:Int) => 
                var token: String= generateToken()

                var tokenNew = Token(token)
                var tknList = tokenTable.getOrElse(userID,null)
                if(tknList == null){
                  var newTkn = new ListBuffer[String]
                  newTkn+=token
                  tokenTable(userID)=newTkn
                }
                else{
                  tokenTable(userID)+=token
                }
                
                sender ! tokenNew




    case PutPublicKey(userID:Int, pubKey:String) =>
            publicKeysTable(userID) = pubKey
            //println("\npublicKeysTable")
            //println("Server Number: "+ myID)
            //println(publicKeysTable) 



    case requestFriendsList(userID: Int) => returnFriends(userID: Int)
    case requestPhoto(imagekey: ImageKey) => 
        var imageSource: ImageSource = photosTable(imagekey)
        sender ! imageSource

    case RequestStatus(statuskey: StatusKey) =>
      
      var status:StatusText = statusTable(statuskey)
      sender ! status

    case GetAlbums(userID:Int) =>
        var albums= albumsTable(userID).toList
         var albumslist = AlbumsList(userID, albums)
         sender ! albumslist



    case GetProfile(userID:Int) =>
      var mytimeline = statusTable.filterKeys(_.userID==userID)
      if (mytimeline != null) {  
        println("----------------")
        println("mytimeline: "+mytimeline)
        var myTimeline = new ListBuffer[Status]
        for ((key,value) <- mytimeline) {
          var status = Status(key,value)
          myTimeline+= status

        }

       var myTimeLine = (myTimeline.toList)
        println("myTimeline: "+myTimeLine)

        sender ! UserTimeline(userID,myTimeLine)        
      }
      else
      {
        sender ! List("No posts on timeline!")
      }

    case GetFriendsPublicKey(userID:Int)=>
      var pbkey = publicKeysTable(userID)
      var friendpubkey = FriendPublicKey(userID, pbkey)
      sender ! friendpubkey


    case GetFriendsAESKeyTable(userID: Int) =>
      var table1 = userAESTable(userID).toList
      var friendsAEStable = FriendAESKeyTable(userID, table1)
      sender ! friendsAEStable

    
    case STOP() => context.stop(self)

  }

  def init(router: ActorRef) {
    var rand = new scala.util.Random
    

    for (i <- 0 to numUsers - 1) {
      if ((i % numServerDBs) == myID) {

        var numFriends = rand.nextInt(friendsLimit)
        var friendsList: ListBuffer[Int] = new ListBuffer

        for (j <- 0 to numFriends) {
          var friendID = rand.nextInt(numUsers - 1)
          while ((friendID < 0) || (friendsList.contains(friendID))) {
            friendID = rand.nextInt(numUsers)
          }
          friendsList += friendID

        }
        if (!friendsList.contains(i)) {
          friendsList += i
        }

        if (!(friendsList.isEmpty)) {
          friendsListTable.put(i, friendsList)
        }
      }
    }
      //println("\nfriendsListTable")
      //println("Server Number: "+ myID)      
      //println(friendsListTable) 
        
    
  }



  def returnFriends(userID: Int) {
    var friendsList = friendsListTable(userID).toList 
    println(friendsList)
    var friendsListData = FriendsList(userID, friendsList)
    sender ! friendsListData
  }



def generateToken():String={
  var rand: SecureRandom = SecureRandom.getInstance("SHA1PRNG")
  var token = new BigInteger(128, rand)
  return (token.toString)
}

def decrypt(text :String,  key:String ):String = {
      var publicKey = decodePublicKey(key)
    decrypt(text,publicKey)     
    }

def decrypt(text :String,  key:PublicKey):String  ={
    var dectyptedText = Base64.decodeBase64(text)
    var cipher = Cipher.getInstance("RSA");

    cipher.init(Cipher.DECRYPT_MODE, key);
    dectyptedText = cipher.doFinal(dectyptedText);

    new String(dectyptedText);
  }

  def decodePublicKey(encodedKey: String): PublicKey = { 
      var publicBytes = Base64.decodeBase64(encodedKey);
    var keySpec = new X509EncodedKeySpec(publicBytes);
    var keyFactory = KeyFactory.getInstance("RSA");
    var pubKey = keyFactory.generatePublic(keySpec);
    pubKey   
    }


}

class timerActor(actorsys: ActorSystem, dbList : List[ActorRef],runTime : Int) extends Actor{

  def receive={
    
    case start => 
      var startTime = System.currentTimeMillis()
      while(System.currentTimeMillis() < startTime + runTime*1000){
        
      } 
       
      for (db <- dbList){
        db ! "stop"
      }
      
      actorsys.shutdown
  }
}
