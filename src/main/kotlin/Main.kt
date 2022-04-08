import com.github.kittinunf.fuel.Fuel
import com.github.kittinunf.fuel.gson.responseObject
import com.google.gson.Gson
import kotlinx.serialization.Serializable
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVPrinter
import java.io.File
import java.util.*
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

val geminiApiKey = System.getenv("GEMINI_API_KEY") ?: throw Exception("GEMINI_API_KEY environment variable must be set")
val geminiSecret = System.getenv("GEMINI_SECRET") ?: throw Exception("GEMINI_SECRET environment variable must be set")
val geminiHeaders = mapOf(
    "Content-Length" to "0",
    "Content-Type" to "text/plain",
    "X-GEMINI-APIKEY" to geminiApiKey,
    "Cache-Control" to "no-cache"
)

val HMAC_SHA1_ALGORITHM = "HmacSHA384"

fun main(args: Array<String>) {
    println("Hello World!")

    val pastTrades = getPastTrades()

    val writer = File("all_gemini_trades.csv").bufferedWriter()
    val printer = CSVPrinter(writer, CSVFormat.DEFAULT.withHeader("Timestamp", "Price", "Amount", "Type", "FeeCurrency", "FeeAmount", "Symbol"))
    pastTrades.forEach { printer.printRecord(listOf(it.timestamp, it.price, it.amount, it.type, it.fee_currency, it.fee_amount, it.symbol)) }
    printer.flush()
    printer.close()
}


fun getPastTrades(): List<PastTradesResponse> {
    val allTrades = mutableListOf<PastTradesResponse>()
    var currentTimestamp = 0L
    do {
        val pastTradesRequest = PastTradesRequest(timestamp = currentTimestamp)
        val pastTradesRequestBody = Gson().toJson(pastTradesRequest)
        val base64EncodedPayload = Base64.getEncoder().encodeToString(pastTradesRequestBody.toByteArray())
        val geminiSignature = generateGeminiSignature(base64EncodedPayload)
        val geminiRequest =  geminiHeaders + mapOf(
            "X-GEMINI-PAYLOAD" to base64EncodedPayload,
            "X-GEMINI-SIGNATURE" to geminiSignature
        )

        val (_, response, result) = Fuel.post("https://api.gemini.com/v1/mytrades")
            .header(geminiRequest)
            .responseObject<List<PastTradesResponse>>()
        val pastTradesResponses = result.get()
        allTrades.addAll(pastTradesResponses)

        if(pastTradesResponses.isNotEmpty()) {
            currentTimestamp = pastTradesResponses[0].timestamp + 1
        }

        Thread.sleep(1000)
        println("retrieved ${pastTradesResponses.size} results")
    } while(pastTradesResponses.isNotEmpty())

   return allTrades
}

@Serializable
data class PastTradesRequest(val request: String = "/v1/mytrades",
                             val nonce: Long = System.currentTimeMillis(),
                             val limit_trades: Int = 500,
                             val timestamp: Long = 0,
                             var symbol: String? = null,
                             var account: String?= null)

data class PastTradesResponse(val price: String, val amount: Double, val timestamp: Long, val type: String, val fee_currency: String, val fee_amount: Double, val symbol: String)

fun generateGeminiSignature(base64EncodedPayload: String): String {
    val signingKey = SecretKeySpec(geminiSecret.toByteArray(), HMAC_SHA1_ALGORITHM)
    val mac = Mac.getInstance(HMAC_SHA1_ALGORITHM)
    mac.init(signingKey)
    return mac.doFinal(base64EncodedPayload.toByteArray()).toHex()
}

fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }