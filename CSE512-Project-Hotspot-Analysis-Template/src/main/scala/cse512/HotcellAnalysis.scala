package cse512

import org.apache.log4j.{Level, Logger}
import org.apache.spark.sql.{DataFrame, SparkSession}
import org.apache.spark.sql.functions.udf
import org.apache.spark.sql.functions._

object HotcellAnalysis {
  Logger.getLogger("org.spark_project").setLevel(Level.WARN)
  Logger.getLogger("org.apache").setLevel(Level.WARN)
  Logger.getLogger("akka").setLevel(Level.WARN)
  Logger.getLogger("com").setLevel(Level.WARN)

def runHotcellAnalysis(spark: SparkSession, pointPath: String): DataFrame =
{
  // Load the original data from a data source
  var pickupInfo = spark.read.format("com.databricks.spark.csv").option("delimiter",";").option("header","false").load(pointPath);
  pickupInfo.createOrReplaceTempView("nyctaxitrips")
  pickupInfo.show()

  // Assign cell coordinates based on pickup points
  spark.udf.register("CalculateX",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 0)
    )))
  spark.udf.register("CalculateY",(pickupPoint: String)=>((
    HotcellUtils.CalculateCoordinate(pickupPoint, 1)
    )))
  spark.udf.register("CalculateZ",(pickupTime: String)=>((
    HotcellUtils.CalculateCoordinate(pickupTime, 2)
    )))
  pickupInfo = spark.sql("select CalculateX(nyctaxitrips._c5) as x,CalculateY(nyctaxitrips._c5) as y, CalculateZ(nyctaxitrips._c1) as z from nyctaxitrips")
  //var newCoordinateName = Seq("x", "y", "z")
  //pickupInfo = pickupInfo.toDF(newCoordinateName:_*)
  //pickupInfo.show()

  // Define the min and max of x, y, z
  val minX = (-74.50/HotcellUtils.coordinateStep).toInt
  val maxX = (-73.70/HotcellUtils.coordinateStep).toInt
  val minY = (40.50/HotcellUtils.coordinateStep).toInt
  val maxY = (40.90/HotcellUtils.coordinateStep).toInt
  val minZ = 1
  val maxZ = 31
  val numCells = (maxX - minX + 1)*(maxY - minY + 1)*(maxZ - minZ + 1)
  val inputRectangle = "%d,%d,%d,%d".format(minX,minY,maxX,maxY)

  spark.udf.register("ST_Contains",(queryRectangle:String, pointString:String)=>
    HotzoneUtils.ST_Contains(queryRectangle, pointString))
  spark.udf.register("joinCoordinates",(xCoordinate : Int, yCoordinate : Int)=>
    xCoordinate.toString + "," + yCoordinate.toString)

  pickupInfo = spark.sql("select x,y,z, getKey(x,y,z) as key from nyctaxitrips where ST_Contains(" +
    givenRectangle + ",joinCoordinates(x,y))")

  pickupInfo = spark.sql("select x,y,z, count(key) from nyctaxitrips group by key")

  var newCoordinateName = Seq("x", "y", "z")
  pickupInfo = pickupInfo.toDF(newCoordinateName:_*)
  println(pickupInfo.count())
  pickupInfo.show()

  // YOU NEED TO CHANGE THIS PART

  return pickupInfo // YOU NEED TO CHANGE THIS PART
}
}
