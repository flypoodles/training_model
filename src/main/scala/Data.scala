import com.typesafe.config.ConfigFactory
import getModel.logger
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.rdd.RDD
import org.deeplearning4j.spark.data.BatchAndExportDataSetsFunction
import org.nd4j.linalg.dataset.DataSet
import org.slf4j.LoggerFactory
import processTokenData.convertToTuple

import scala.jdk.CollectionConverters.{asJavaIteratorConverter, asScalaIteratorConverter}

object Data {


  val myConf = ConfigFactory.load()

  val logger = LoggerFactory.getLogger(this.getClass)
   def getDataSet(input: String, output:String) = {




     val dataConf = myConf.getConfig("myDataConfig")
     logger.info("compute sliding window dataset")
     val conf = new SparkConf().setMaster("local").setAppName("data")
     val sc = new SparkContext(conf)
     val text = sc.textFile(dataConf.getString("fileSystem") + input)

     val tokenList:List[(Integer, Array[Double])] = sc.textFile( dataConf.getString("fileSystem")+ dataConf.getString("tokenList")).map(line => convertToTuple(line)).collect().toList
     val sentencesRDD:RDD[String] = sc.parallelize(text.map(line => line.trim() + " ").reduce((x,y) => x+y).split("[.?!]"))
     val result  = sentencesRDD.map(sentence => processTokenData.converToToken(sentence))

     val result2:RDD[(List[String], String)] = result.flatMap( sentenceToken => slideWindow.slideWindowHelper(sentenceToken.split(" ").filter(t=>t!="").toList, dataConf.getInt("windowSize")))

     val dataSetList : RDD[DataSet] = result2.map( tok => slideWindow.slideWindow(tok, tokenList,logger))

     //rintln("shape")
     //dataSetList.collect().foreach( data => println(data.getFeatures.shape().mkString + "  labelL "+  data.getLabels.shape().mkString))
     logger.info("write to the dataset to file")
         val  minibatchSize = dataConf.getInt("batchSize");     //Minibatch size of the saved DataSet objects
         val exportPath =dataConf.getString("fileSystem") + output;
         val paths :RDD[String]= dataSetList.mapPartitionsWithIndex( (index, iterator) => {
            (new BatchAndExportDataSetsFunction(minibatchSize,exportPath).call(index,iterator.asJava).asScala)
         });


//
         paths.collect()
     sc.stop()
   }
}
