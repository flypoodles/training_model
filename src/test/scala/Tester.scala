
import Generate.getContextToken
import com.knuddels.jtokkit.Encodings
import com.knuddels.jtokkit.api.EncodingType
import org.scalatest.flatspec.AnyFlatSpec
import processTokenData.{converToToken, convertToTuple}
import slideWindow.slideWindowHelper


//In a AnyFlatSpec, you name the subject once, with a behavior
// of clause or its shorthand, then write tests for that subject with it should/must/can "do something" phrases.
//Because sometimes the subject could be plural, you can alternatively use they instead of it:

// change it to ignore to ignore the test
class Tester extends AnyFlatSpec {


  class Fixture {
    //val builder = new StringBuilder("ScalaTest is ")
    //val buffer = new ListBuffer[String]
    val stringTester = "hello guys I am testing"
    val doubleArray1 :Array[Double]= Array(4,5,6)
    val tokenEmbedding = "1,2 ,[2.123 523.23112 0.112]"
    val registry = Encodings.newDefaultEncodingRegistry()
    val encoder = registry.getEncoding(EncodingType.CL100K_BASE)



  }

  def fixture = new Fixture

  behavior of "token conversion"
  it should "generate the same token" in {
    val f = fixture
    val expect = f.stringTester.split(" ").flatMap( tok => f.encoder.encode(tok).toArray)


    val actual = converToToken(f.stringTester).split(" ").map( tok => tok.toInt)


    info("expect: " + expect.mkString("["," ","]"))
    info("actual: " + actual.mkString("["," ","]"))
    assert(expect sameElements actual)
  }

  "tuple conversion" should "generate the correct tuple" in {
    val f = fixture

    val embedding = Array(2.123,523.23112,0.112)
    val token = 1

    val tuple  = convertToTuple(f.tokenEmbedding)


    info("actual token: " + tuple._1)
    info("actual embedding: " + tuple._2.mkString("["," ","]"))
    assert(tuple._1 == token)
    assert(tuple._2 sameElements embedding)
  }

  behavior of "dataset"
  it should "generate the correct feature" in {
    val f = fixture

    val tokenList :List[String] = converToToken(f.stringTester).split(" ").filter(t=>t!="").toList

    val expect: List[List[String]] = List(List("15339", "8890", "1065"), List("8890", "1065", "40"), List("1065", "40", "309"))

    val actual: List[(List[String], String)] = slideWindowHelper(tokenList, 3)

    val feature = actual.map(tok => tok._1)

    info("expect: " + expect.mkString(" "))
    info("actual" + feature.mkString(" "))

    assert(expect.length == feature.length)
    assert(expect.flatten == feature.flatten)

  }

  it should "generate the correct label" in {
    val f = fixture

    val tokenList :List[String] = converToToken(f.stringTester).split(" ").filter(t=>t!="").toList

    val expect = Array("40", "309", "9016")

    val actual: List[(List[String], String)] = slideWindowHelper(tokenList, 3)
    val label :Array[String]= actual.map(tok => tok._2).toArray

    info("expect: " + expect.mkString(" "))
    info("actual: " + label.mkString(" "))

    assert(expect.length == label.length)
    assert(expect.sameElements(label))

  }

  behavior of "context window"
  it should "generate the correct token input size" in {

    val arr = Array(23 , 23 ,45 ,56,23)

    val actual = getContextToken(arr,3)


    val expect = Array(45,56,23)

    assert(expect sameElements actual)
  }

  it should "generate the correct token input size if less than expected input" in {

    val arr = Array(56,23)

    val actual = getContextToken(arr,5)


    val expect = Array(1,1,1,56,23)

    assert(expect sameElements actual)
  }



}