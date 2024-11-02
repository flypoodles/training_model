import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{Encoding, EncodingType}

object processTokenData {




  def processStringArray(str: String) : Array[Double] = {

    val result = str.split(" ").map((value) => {value.toDouble})


    result
  }

  def getTokenEmbedding(str:String) : Array[Double] = {

    val begin = str.indexOf("[")
    val end = str.length- str.indexOf("]")

    processStringArray(str.dropRight(end).drop(begin+1))
  }

  // read and convert token embddings from tokenlist
  def convertToTuple(line : String): (Integer,Array[Double]) ={
    val arr = line.split(",")
    val tokenId = arr(0).toInt;
    val embedding = getTokenEmbedding(arr(2))
    (tokenId, embedding)
  }


  // convert a sentence "like hello" to "12 23 42"
  def converToToken(sentence :String) = {
    val registry = Encodings.newDefaultEncodingRegistry()
    val encoder = registry.getEncoding(EncodingType.CL100K_BASE)
    val sentenceToken: String = sentence.split(" ").fold("")((strA: String, strB: String) => {

      val arr = encoder.encode(strB).toArray
      val result = arr.map(token => token.toString).fold("")((a: String, b: String) => a + " " + b)

      strA + " " + result


    }).trim
    sentenceToken.split(" ").filter(tok => "" != tok).mkString(" ")
  }
}
