import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{Encoding, EncodingType}
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.{DenseLayer, OutputLayer}
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j
import org.slf4j.LoggerFactory
import processTokenData.convertToTuple
import org.deeplearning4j.spark.api.TrainingMaster
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer
import org.deeplearning4j.spark.impl.paramavg.ParameterAveragingTrainingMaster
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.lossfunctions.LossFunctions
import scala.jdk.CollectionConverters._

import java.net.URI
import scala.io.Source

object Main {

  val logger = LoggerFactory.getLogger(this.getClass)
  val registry = Encodings.newDefaultEncodingRegistry()
  val encoder = registry.getEncoding(EncodingType.CL100K_BASE)

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


  def converToToken(sentence :String ) = {
    val sentenceToken: String = sentence.split(" ").fold("")((strA: String, strB: String) => {

      val arr = encoder.encode(strB).toArray
      val result = arr.map(token => token.toString).fold("")((a: String, b: String) => a + " " + b)

        strA + " " + result


    }).trim
    sentenceToken
  }
  def slideWindow(tuple:(List[String],String), tokenList:List[(Integer, Array[Double])] ): DataSet = {


      val embedding: INDArray = tuple._1.map(token => {

        val tok = token.toInt
        val result = tokenList.filter(tup => tup._1 == tok)
        if (result.nonEmpty) {
          logger.info("found {}", result.head._2)
          Nd4j.create(result.head._2).reshape(1,5)
        } else {
          val randArr = Nd4j.create(Nd4j.randn(1, 5).data().asDouble()).reshape(1,5)

          // there will be time where the word does not appear in my tokenEmbeddings, so I will generate a random embedding for it
          logger.info("create a random IndArray because it cannot find token embedding {}", randArr)
          randArr
        }
      }).reduce( (op1, op2) =>
        { Nd4j.vstack(op1,op2)
        }).reshape(1,5 * 5)

      val targetEmbeddingResult = tokenList.filter(tup => tup._1 == tuple._2.toInt )
      if(targetEmbeddingResult.nonEmpty) {
        new DataSet(embedding, Nd4j.create(targetEmbeddingResult.head._2).reshape(1,5))
        //new DataSet(embedding, Nd4j.rand(1,5))
      } else{
        new DataSet(embedding, Nd4j.rand(1,5))
      }

  }

  def main(args: Array[String]): Unit = {






    //slideWindow("hello world", 23, encoding)
    val conf = new SparkConf().setAppName("appName").setMaster("local")
    val sc = new SparkContext(conf)

    val text = sc.textFile("hdfs://localhost:9000/smalldata.txt")
    val tokenList:List[(Integer, Array[Double])] = sc.textFile("hdfs://localhost:9000/token.txt").map(line => convertToTuple(line)).collect().toList


    val sentencesRDD:RDD[String] = sc.parallelize(text.map(line => line.trim() + " ").reduce((x,y) => x+y).split("[.?!]"))



    val result  = sentencesRDD.map(sentence => converToToken(sentence))

    val result2:RDD[(List[String], String)] = result.flatMap( sentenceToken => slideWindowHelper(sentenceToken.split(" ").filter(t=>t!="").toList, 5))
    //result2.collect().foreach(tup => println(tup._1.mkString(" ") + " 2nd entry:" + tup._2))
    val dataSetList : RDD[DataSet] = result2.map( tok => slideWindow(tok, tokenList))
    dataSetList.persist()
    val myData = dataSetList.collect()

    val trainingMaster = new ParameterAveragingTrainingMaster.Builder(32)
      .batchSizePerWorker(1)  // Batch size on each Spark worker
      .averagingFrequency(5)   // Frequency of parameter averaging
      .workerPrefetchNumBatches(2)
      .build();

    val  config = new NeuralNetConfiguration.Builder()
      .weightInit(WeightInit.XAVIER) // Xavier weight initialization
      .updater(new Adam(0.001))      // Adam optimizer
      .list()
      // First hidden layer (Dense Layer)
      .layer(new DenseLayer.Builder()
        .nIn(25) // Input size (from Word2Vec embeddings)
        .nOut(100)       // Number of nodes in the hidden layer
        .activation(Activation.IDENTITY) // Activation function
        .build())
      // Output layer (for classification)
      .layer(new OutputLayer.Builder(LossFunctions.LossFunction.NEGATIVELOGLIKELIHOOD)
        .nIn(100) // Input from the hidden layer
        .nOut(100000) // Output size (number of classes)
        .activation(Activation.SOFTMAX) // Softmax for classification
        .build())
      .build();

    val sparkModel = new SparkDl4jMultiLayer(sc, config, trainingMaster);
    //sparkModel.setListeners(new GradientStatsListener(1));
    sparkModel.setListeners(new ScoreIterationListener(10));



    val model = sparkModel.fit(dataSetList);

    val dataSetIterator = new ListDataSetIterator(dataSetList.collect().toIterable.asJavaCollection, 1);

    model.setListeners(new ScoreIterationListener(10))
    (0 to 10).foreach(ds => model.fit(dataSetIterator))

    val res = model.output(Nd4j.rand(1,25))
    println("final" + Nd4j.argMax(res, 1).getInt(0))
    println("final" + res)










    //val distData = sc.parallelize(data)

    //println("result: "+ tokenList.collect().foreach(entry => println(entry._1 + " " + entry._2.mkString("["," ", "]"))))
    sc.stop()

  }
}