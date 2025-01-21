package com.kazumaproject.markdownhelperkeyboard.ime_service

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.BlockThreshold
import com.google.ai.client.generativeai.type.GenerateContentResponse
import com.google.ai.client.generativeai.type.HarmCategory
import com.google.ai.client.generativeai.type.SafetySetting
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

class Gemini() {

    private var model: GenerativeModel
    private var response=""

    private var prompt_base = """
    "contents": [{
    "parts":[{
      "text": "%s"
    }],
    "role": "user"
  }],
    "systemInstruction":{
    "parts":[{
      "text": "この文章の言葉自体がかなり不快な思いをさせるなら2,全体的に不快な思いをさせるなら1,問題ないなら0として適切度を答えてください。言い換えた文章はなるべく一部分だけを言い換えてください。
      解答はJSON Schemaで出力してください。出力の改行は削除してください。
      {"type": "object",
         "properties": {
         "適切度": {
            "type": "int"
         },
         "文章": {
            "type": "String"
         },
        }
      }"
    }],
    "role": "model"
  }
    """.trimIndent()

    init
    {
        model = GenerativeModel(
            "gemini-1.5-pro-latest",
            "AIzaSyDAXlRJ8fNjN2dufhssDh8_WCb4Tmoyjcs",
            generationConfig {
                temperature = 0.15f
                topK = 32
                topP = 1f
                maxOutputTokens = 4096
                responseMimeType = "application/json"
            },
            safetySettings =listOf(
                SafetySetting(HarmCategory.HARASSMENT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.HATE_SPEECH, BlockThreshold.NONE),
                SafetySetting(HarmCategory.SEXUALLY_EXPLICIT, BlockThreshold.NONE),
                SafetySetting(HarmCategory.DANGEROUS_CONTENT, BlockThreshold.NONE),
            )

        )


        println("Gemini initialized");
    }

    public fun getResponse(prompt:String? ) :String? {
        println("getResponse")
        var restext :String? = ""
        var response :GenerateContentResponse
        try {
            runBlocking {
                withTimeout(30000L) {
                    withContext(Dispatchers.IO) {
                        val send_prpmpt = String.format(prompt_base,prompt)
                        //println("REQ:"+send_prpmpt)
                        response = model.generateContent(send_prpmpt)
                        restext=response.text
                        //("RES:"+restext)
                        print(restext)
                    }
                }
            }
        } catch (e: Exception) {
            println(e)
            println("Gemini Time out.")
        }
        return restext
    }
}
