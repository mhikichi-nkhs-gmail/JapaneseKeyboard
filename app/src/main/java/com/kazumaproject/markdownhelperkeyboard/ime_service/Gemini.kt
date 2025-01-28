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
import com.google.gson.Gson

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
      "text": "この文章が未成年が検索してはいけない、相手に送信してはいけないものなら2,文章の表現を少し改善すべきものは1,問題ないなら0として適切度を答えてください。
      また、文章を置き換えるべきなら置き換え案を提案してください。その言葉が嫌がらせ、ヘイトスピーチ、性的な発言、危険なコンテンツ、問題なし のどのカテゴリに分類されるか答えてください。説明は不要です。
      "解答はJSON Schemaで出力してください。出力の改行は削除してください。
      {"type": "object",
         "properties": {
         "tekisetudo": {
            "type": "int"
         },
         "okikaeAn": {
            "type": "String"
         },
         "category": {
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
                //HARASSMENT(嫌がらせ)
                //HATE_SPEECH(ヘイトスピーチ)
                //SEXUALLY_EXPLICIT(性的表現)
                //DANGEROUS_CONTENT(危険なコンテンツ)
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
                    }
                }
            }
        } catch (e: Exception) {
            println(e)
            println("Gemini Time out.")
        }
        //以下テスト用
        /*val gson = Gson()
        //val json = """{"tekisetudo": 2, "word": "死んじゃった", "置き換え案": "亡くなった", "カテゴリ": "暴言"}"""
        val data = gson.fromJson(restext, Data::class.java)
        println(data.tekisetudo)
        println(data.word)
        if(2 == data.tekisetudo) {
            
        }*/
        return restext
    }
}

data class Data(val tekisetudo: Int, val word: String, val okikaeAn: String, val category: String)
