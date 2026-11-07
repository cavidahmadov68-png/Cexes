package com.cexes.app

import android.content.Context

object Prefs {
  const val OFF = 0
  const val DRAFT = 1
  const val AUTO = 2
  private fun sp(c: Context) = c.getSharedPreferences("cexes", Context.MODE_PRIVATE)
  fun mode(c: Context) = sp(c).getInt("mode", DRAFT)
  fun setMode(c: Context, m: Int) = sp(c).edit().putInt("mode", m).apply()
}
