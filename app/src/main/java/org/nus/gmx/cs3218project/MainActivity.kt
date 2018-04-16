package org.nus.gmx.cs3218project

import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import kotlinx.android.synthetic.main.activity_main.*
import kotlin.system.exitProcess

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        init()
    }

    private fun init() {
        btn_broadcast.setOnClickListener {
            val intent = Intent(this, BroadcastActivity::class.java)
            startActivity(intent)
        }

        btn_receive.setOnClickListener {
            val intent = Intent(this, ReceiverActivity::class.java)
            startActivity(intent)
        }

        btn_exit.setOnClickListener {
            exitProcess(1)
        }
    }
}