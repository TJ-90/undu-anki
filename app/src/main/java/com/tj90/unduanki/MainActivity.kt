package com.tj90.unduanki

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Mischief Island is under construction. Study and gameplay are not yet available."
            textSize = 22f
            setPadding(32, 48, 32, 32)
        })
    }
}
