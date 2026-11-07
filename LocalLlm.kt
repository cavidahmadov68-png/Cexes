package com.cexes.app

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File

object LocalLlm {
  private var llm: LlmInference? = null

  fun modelFile(c: Context) = File(c.filesDir, "model.task")
  fun isReady(c: Context) = modelFile(c).exists()

  @Synchronized
  fun generate(c: Context, prompt: String): String {
    if (llm == null) {
      val o = LlmInference.LlmInferenceOptions.builder()
        .setModelPath(modelFile(c).absolutePath)
        .setMaxTokens(512)
        .build()
      llm = LlmInference.createFromOptions(c.applicationContext, o)
    }
    return llm!!.generateResponse(prompt).trim()
  }

  @Synchronized
  fun reset() { llm?.close(); llm = null }
}
