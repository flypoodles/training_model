
import com.typesafe.config.ConfigFactory
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j
import org.slf4j.Logger

object slideWindow {

  val myConf = ConfigFactory.load()
  val slideConfig = myConf.getConfig("mySlideConfig")
  def computePositionalEmbedding (windowSize: Integer, embeddingSize : Integer): INDArray ={

    val positionalEncoding = Nd4j.zeros(windowSize, embeddingSize);


    // this nested for loop is needed in order to fill the INDarray from Nd4j with values. This allows me to easily compute positional embedding.
    (0 until windowSize).foreach( pos => {

      (0 until embeddingSize ).foreach( i => {

        val angle = pos / Math.pow(10000, (2.0 * i) / embeddingSize);


        positionalEncoding.putScalar( Array(pos,i), Math.sin(angle));

        if(embeddingSize > i+1){
          positionalEncoding.putScalar(Array(pos,i+1), Math.cos(angle))
        }

      })
    })
    positionalEncoding
  }
  def DataSetCreator(tokenList:List[String], windowSize: Integer, curIndex:Integer) : List[String] = {

    tokenList match {

      case head :: tail if curIndex < windowSize => {
        head:: DataSetCreator(tail, windowSize, curIndex+1)
      }
      case _ => Nil
    }

  }

  def slideWindowHelper(tokenList: List[String], windowSize: Integer) : List[(List[String], String)] = {
    tokenList match {

      case head :: tail if tokenList.length > windowSize => {
        val target = tokenList(windowSize)
        val dataSet = DataSetCreator(tokenList, windowSize, 0)
        //println("dataSet2: " + dataSet.mkString(" "))
        (dataSet,target) :: slideWindowHelper(tail, windowSize)
      }
      case _ => Nil

    }

  }



  def slideWindow(tuple:(List[String],String), tokenList:List[(Integer, Array[Double])],logger:Logger ): DataSet = {
    val window = slideConfig.getInt("windowSize")
    val embed = slideConfig.getInt("embedSize")
    val total = slideConfig.getInt("totalVocab")

    val postionalEmbed : INDArray = computePositionalEmbedding(window,embed)
    val embedding: INDArray = tuple._1.map(token => {


      val tok = token.toInt
      val result = tokenList.filter(tup => tup._1 == tok)
      if (result.nonEmpty) {
       // logger.info("found {}", result.head._2)
        Nd4j.create(result.head._2).reshape(1,embed)
      } else {
        val randArr = Nd4j.randn(1, embed).castTo(DataType.DOUBLE)

        // there will be time where the word does not appear in my tokenEmbeddings, so I will generate a random embedding for it
        //logger.info("create a random IndArray because it cannot find token embedding {}", randArr)
        randArr
      }
    }).reduce( (op1, op2) =>
    { Nd4j.vstack(op1,op2)
    }).add(postionalEmbed).reshape(1,window * embed)



    val target = tuple._2.toInt % total

    val targetArr = Nd4j.zeros(1,total)
    targetArr.putScalar(target,1)
    new DataSet(embedding.reshape(1, window * embed), targetArr.reshape(1,total))
    //new DataSet(Nd4j.rand(1,25, 1), Nd4j.rand(1))

  }



}
