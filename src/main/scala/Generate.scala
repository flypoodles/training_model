import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{EncodingType, IntArrayList}
import com.typesafe.config.ConfigFactory
import org.apache.hadoop.conf.Configuration
import org.slf4j.LoggerFactory
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.apache.hadoop.fs.{FileSystem, Path}
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.factory.Nd4j
import processTokenData.convertToTuple
import java.io.BufferedInputStream
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.collection.immutable.List
import scala.io.Source
object Generate {

  val logger = LoggerFactory.getLogger(this.getClass)
  val conf = new Configuration();
  val myConf = ConfigFactory.load()
  val generateConfig = myConf.getConfig("myGenerateConfig")

  val fileSystem: FileSystem = FileSystem.get(new URI(generateConfig.getString("fileSystem")), conf);

  val registry = Encodings.newDefaultEncodingRegistry()
  val encoder = registry.getEncoding(EncodingType.CL100K_BASE)

  def loadMLP(filePath: String): MultiLayerNetwork = {

    val is = new BufferedInputStream(fileSystem.open(new Path(filePath)))
    val net = ModelSerializer.restoreMultiLayerNetwork(is);

    is.close()
    net
  }



  def prepend(remain:Integer, currentInput:Seq[Int]): Seq[Int] = {

    remain match {
      case x if x >0 => { 1 +: prepend(x-1, currentInput)}
      case x if x==0 => {currentInput}
    }
  }
  def getContextToken(currentInput : Array[Int], windowSize: Integer) : Array[Int]= {

      if(currentInput.length > windowSize){
        return currentInput.slice(currentInput.length-windowSize, currentInput.length).toArray
      }

    val remain = windowSize-currentInput.length;

    prepend(remain, currentInput).toArray
  }


  // get the correct token embedding for each words
  def getActualInput(currentInput : Array[Int], windowSize:Integer, tokenList:List[(Integer, Array[Double])]):INDArray={

    val batchSize = generateConfig.getInt("batchSize")
    val windowSize = generateConfig.getInt("windowSize")
    val embedSize = generateConfig.getInt("embedSize")
    val one =  generateConfig.getInt("one")
    val embedding: INDArray = currentInput.map(token => {
      val tok = token.toInt
      val result = tokenList.filter(tup => tup._1 == tok)
      if (result.nonEmpty) {
        logger.info("found the token Embedding {}", result.head._2)
        Nd4j.create(result.head._2).reshape(one,embedSize)
      } else {
        val randArr = Nd4j.randn(one, embedSize).castTo(DataType.DOUBLE)
        // there will be time where the word does not appear in my tokenEmbeddings, so I will generate a random embedding for it
        logger.info("create a random IndArray because it cannot find token embedding {}", randArr)
        randArr
      }
    }).reduce( (op1, op2) =>
    { Nd4j.vstack(op1,op2)
    }).reshape(batchSize,windowSize * embedSize)
    embedding
  }




  def generatNextWord(curInput:String ,  tokenText:List[(Integer, Array[Double])], model:MultiLayerNetwork , size:Integer):String = {

    if (size == 0) {
      return curInput
    }

    val tokenized: Array[Int] = encoder.encode(curInput).toArray
    val correctToken = getContextToken(tokenized, generateConfig.getInt("windowSize"))
    val actualInput = getActualInput(correctToken, generateConfig.getInt("windowSize"), tokenText)
    val res = model.output(actualInput)

    val tokenID = Nd4j.argMax(res, 1).getInt(0)
    val arr = new IntArrayList()
    arr.add(tokenID)
    val str = encoder.decode(arr)
    generatNextWord(curInput+" "+str,tokenText, model, size-1)

  }


  def generateWords(modelLocation: String, input: String , outputPath: String) {


    println("myInput:" + input)
    logger.info("Reading in token list")
    val tokenText:List[(Integer, Array[Double])] = Source.fromInputStream(fileSystem.open(new Path(generateConfig.getString("tokenList")))).getLines().map(line => convertToTuple(line)).toList



    logger.info("load multilayer network")
    val model = loadMLP(modelLocation)


    val fs = FileSystem.get(new URI(generateConfig.getString("fileSystem")), new Configuration() )

    val output= fs.create(new Path(outputPath))

    val str: String = "result: " + generatNextWord(input, tokenText = tokenText, model = model, generateConfig.getInt("AmountofWord"))
    println("string:" +str)
    output.write(str.getBytes(StandardCharsets.UTF_8))
    output.flush()
    output.close()

  }

}