package com.zhy20.teleprompter.app

import android.app.Application

class TeleprompterApplication : Application() {
    val container: AppContainer by lazy { DefaultAppContainer(applicationContext) }
}
