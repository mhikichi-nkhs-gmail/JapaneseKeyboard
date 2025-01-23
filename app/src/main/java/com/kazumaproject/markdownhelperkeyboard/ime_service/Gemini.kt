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
      "text": "この文章が検索してはいけない、相手に送信してはいけないものなら2,文章の表現を少し改善すべきものは1,問題ないなら0として適切度を答えてください。言い換えた文章はなるべく一部分だけを言い換えてください。
      また、文章を置き換えるべきなら置き換え案を提案してください。その言葉が暴言、性的な発言、コンプライアンス違反、問題なし のどのカテゴリに分類されるか答えてください。説明は不要です。
      "解答はJSON Schemaで出力してください。出力の改行は削除してください。
      {"type": "object",
         "properties": {
         "適切度": {
            "type": "int"
         },
         "文章": {
            "type": "String"
         },
         "置き換え案": {
            "type": "String"
         },
         "カテゴリ": {
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
                        //print(restext)
                        //val tt = responce
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
