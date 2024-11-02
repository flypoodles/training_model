import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.{Encoding, EncodingType}
import com.typesafe.config.ConfigFactory
import org.apache.hadoop
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.conf.Configuration
import org.apache.spark.SparkContext
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.apache.spark.storage.StorageLevel.MEMORY_AND_DISK_SER
import org.deeplearning4j.datasets.iterator.utilty.ListDataSetIterator
import org.deeplearning4j.nn.conf.NeuralNetConfiguration
import org.deeplearning4j.nn.conf.layers.{DenseLayer, LSTM, OutputLayer, RnnOutputLayer}
import org.deeplearning4j.nn.conf.preprocessor.RnnToFeedForwardPreProcessor
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork
import org.deeplearning4j.nn.weights.WeightInit
import org.deeplearning4j.optimize.listeners.ScoreIterationListener
import org.nd4j.linalg.api.ndarray.INDArray
import org.nd4j.linalg.dataset.DataSet
import org.nd4j.linalg.factory.Nd4j
import org.slf4j.LoggerFactory
import processTokenData.convertToTuple
import org.deeplearning4j.spark.api.TrainingMaster
import org.deeplearning4j.spark.data.BatchAndExportDataSetsFunction
import org.deeplearning4j.spark.impl.multilayer.SparkDl4jMultiLayer
import org.deeplearning4j.spark.impl.paramavg.ParameterAveragingTrainingMaster
import org.deeplearning4j.util.ModelSerializer
import org.nd4j.linalg.activations.Activation
import org.nd4j.linalg.api.buffer.DataType
import org.nd4j.linalg.learning.config.Adam
import org.nd4j.linalg.lossfunctions.LossFunctions

import java.io.BufferedOutputStream
import scala.jdk.CollectionConverters._
import java.net.URI
import java.nio.charset.StandardCharsets
import scala.io.Source

object getModel {
  val logger = LoggerFactory.getLogger(this.getClass)


  val myConf = ConfigFactory.load()
  val modelConfig = myConf.getConfig("myModelConfig")

  def getModel(inputPath: String, outputPath: String, statPath:String) {
    val conf = new SparkConf().setMaster("local").setAppName("model")
    val sc = new SparkContext(conf)


    val trainingMaster = new ParameterAveragingTrainingMaster.Builder(32)
      .batchSizePerWorker(modelConfig.getInt("batchSizePerWorker")) // Batch size on each Spark worker
      .averagingFrequency(modelConfig.getInt("averagingFrequency")) // Frequency of parameter averaging
      .workerPrefetchNumBatches(modelConfig.getInt("workerPrefetchNumBatches"))
      .build();

    val config = new NeuralNetConfiguration.Builder()
      .weightInit(WeightInit.XAVIER) // Xavier weight initialization
      .updater(new Adam(modelConfig.getDouble("learningRate"))) // Adam optimizer
      .list()
      // First hidden layer (Dense Layer)
      .layer(new DenseLayer.Builder()
        .nIn(modelConfig.getInt("inputSize")) // Input size (from Word2Vec embeddings)
        .nOut(modelConfig.getInt("hiddenLayer")) // Number of nodes in the hidden layer
        .activation(Activation.IDENTITY) // Activation function
        .build())
      // Output layer (for classification)
      .layer(new OutputLayer.Builder(LossFunctions.LossFunction.MCXENT)
        .nIn(modelConfig.getInt("hiddenLayer")) // Input from the hidden layer
        .nOut(modelConfig.getInt("numOutput")) // Output size (number of classes)
        .activation(Activation.SOFTMAX) // Softmax for classification
        .build())
      .build();

    val sparkModel = new SparkDl4jMultiLayer(sc, config, trainingMaster);
    //sparkModel.setListeners(new GradientStatsListener(1));
    sparkModel.setListeners(new ScoreIterationListener(modelConfig.getInt("iteration")));
    sparkModel.setCollectTrainingStats(true)


    val path: String = modelConfig.getString("fileSystem") + inputPath
    logger.info("fitting data")
    // this for loop is nessarily to train model in epoch
    val startTime: Long = System.currentTimeMillis
    (0 to 2).foreach(ds => sparkModel.fit(path))


    val endTime: Long = System.currentTimeMillis

    val epochTime = (endTime- startTime) /2
    val model = sparkModel.getNetwork

    val fs = FileSystem.get(new URI(modelConfig.getString("fileSystem")), new Configuration() )

    val output = fs.create(new Path(statPath))

    output.write(("\nepoch time: " + epochTime).getBytes(StandardCharsets.UTF_8))
    output.write(("\nspark stats: " + sparkModel.getSparkTrainingStats.statsAsString()).getBytes(StandardCharsets.UTF_8))
    output.write((("\nspark score(training loss): " + sparkModel.getScore).getBytes(StandardCharsets.UTF_8)))
    output.write(("\nspark eval (accuracy): "  + sparkModel.evaluate(path)).getBytes(StandardCharsets.UTF_8))
    output.write(("\nlearning rate: "  + modelConfig.getDouble("learningRate")).getBytes(StandardCharsets.UTF_8))
    output.flush()
    output.close()
//    println("spark stats: " + sparkModel.getSparkTrainingStats.statsAsString())
//    println("spark score: " + sparkModel.getScore)
//    println("spark eval: "  + sparkModel.evaluate(path))
//    println("learning rate: "  + modelConfig.getDouble("learningRate"))


        val fileSystem: FileSystem = FileSystem.get( new URI(modelConfig.getString("fileSystem")),sc.hadoopConfiguration);

        val target = new BufferedOutputStream( fileSystem.create(new Path(outputPath)) )

        ModelSerializer.writeModel(model,target , true)
        target.close()



       // println("Total executors: " + sc.getExecutorMemoryStatus.mkString);
    sc.stop()
  }
}
