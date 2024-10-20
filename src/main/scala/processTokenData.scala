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
  def convertToTuple(line : String): (Integer,Array[Double]) ={
    val arr = line.split(",")
    val tokenId = arr(0).toInt;
    val embedding = getTokenEmbedding(arr(2))
    (tokenId, embedding)
  }
}
