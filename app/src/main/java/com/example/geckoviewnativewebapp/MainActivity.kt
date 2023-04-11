package com.example.geckoviewnativewebapp

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.example.geckoviewnativewebapp.databinding.ActivityMainBinding
import org.json.JSONObject
import org.mozilla.geckoview.*
import org.mozilla.geckoview.BuildConfig
import org.mozilla.geckoview.WebExtension.PortDelegate
import java.io.File

class MainActivity : Activity() {
    companion object {
        val TAG: String = MainActivity::class.java.simpleName
        var geckoRuntime: GeckoRuntime? = null
    }

    lateinit var vb: ActivityMainBinding
    private val geckoSession = GeckoSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        vb = ActivityMainBinding.inflate(layoutInflater)
        setContentView(vb.root)

        vb.button.setOnClickListener {
            appWebExtensionPortDelegate.port?.postMessage(JSONObject(mapOf("text" to "Hello Web!")))
        }

        var runtime = geckoRuntime
        if (runtime == null) {
            val builder = GeckoRuntimeSettings.Builder()
            if (BuildConfig.DEBUG) {
                builder.remoteDebuggingEnabled(true)
                builder.consoleOutput(true)
            }
            runtime = GeckoRuntime.create(this, builder.build())
            geckoRuntime = runtime
        }

        val samplePageFolder = File(filesDir, "sample-page")
        if (!samplePageFolder.exists()) {
            // Copy sample page to app's private storage
            // This is required because there is no way to match web extension's content_scripts
            // to resource://android/assets/... and jar:file://... URLs which are used for
            // communication between page and native app (by web extension in-between).
            samplePageFolder.mkdirs()
            for (file in assets.list("sample-page")!!) {
                assets.open("sample-page/$file").copyTo(File(samplePageFolder, file).outputStream())
            }
        }

        geckoSession.open(runtime)
        vb.geckoView.setSession(geckoSession)

        runtime.webExtensionController
            //.installBuiltIn("resource://android/assets/sample-ext/")
            .ensureBuiltIn("resource://android/assets/sample-ext/", "sample@mock.com")
            .accept(object : GeckoResult.Consumer<WebExtension> {
                @SuppressLint("WrongThread")
                override fun accept(extension: WebExtension?) {
                    Log.d(TAG, "extension accepted: ${extension!!.metaData.description}")
                    //geckoSession.loadUri(extension!!.metaData.baseUrl + "page.html")
                    geckoSession.webExtensionController.setMessageDelegate(extension,
                        object : WebExtension.MessageDelegate {
                            override fun onMessage(nativeApp: String, message: Any,
                                                   sender: WebExtension.MessageSender): GeckoResult<Any>? {
                                Log.d(TAG, "onMessage: $nativeApp, $message, $sender")
                                return null
                            }

                            override fun onConnect(port: WebExtension.Port) {
                                Log.d(TAG, "onConnect: $port")
                                port.setDelegate(appWebExtensionPortDelegate)
                                appWebExtensionPortDelegate.port = port
                            }
                        }, "browser")
                }
            }
            ) { e -> Log.e(TAG, "Error registering WebExtension", e) }

        //val apkURI = File(packageResourcePath).toURI()
        //val assetsURL = "jar:$apkURI!/assets/";
        //val myURL = assetsURL + "sample-page/page.html";
        //val myURL = "resource://android/assets/page.html"
        val myURL = "file://${samplePageFolder.absolutePath}/page.html"
        geckoSession.loadUri(myURL)
    }

    private val appWebExtensionPortDelegate = object : PortDelegate {
        var port: WebExtension.Port? = null
        override fun onPortMessage(message: Any, port: WebExtension.Port) {
            Toast.makeText(this@MainActivity, message.toString(), Toast.LENGTH_SHORT).show()
        }

        override fun onDisconnect(port: WebExtension.Port) {
            Log.d(TAG, "onDisconnect: $port")
            this.port = null
        }
    }
}