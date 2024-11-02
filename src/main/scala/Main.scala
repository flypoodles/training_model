import Generate.generateWords
import com.typesafe.config.ConfigFactory
import org.slf4j.LoggerFactory

object Main {

  val logger = LoggerFactory.getLogger(this.getClass)

  val myConf = ConfigFactory.load()
  def main(args: Array[String]): Unit = {

    if (args.length < 1){
      logger.info("Must run a command")
      return
    }

    args(0) match {

      case "model" =>{
        if(args.length < 4){
          logger.info("USAGE: model inputPath outputPath statisticPath")
        }
        getModel.getModel(args(1),args(2),args(3))
      }
      case "generate" => {
        if(args.length != 4){
          logger.info("USAGE: generate modelPath \"inputString\" outputPath")
        }
        generateWords(args(1),args(2),args(3))
      }
      case "data" => {
        if(args.length < 3){
          logger.info("USAGE: model inputPath outputPath")
        }
        Data.getDataSet(args(1),args(2))
      }

    }

  }
}