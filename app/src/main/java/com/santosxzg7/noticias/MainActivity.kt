package com.santosxzg7.noticias
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.util.Log
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
class MainActivity : ComponentActivity() {
private lateinit var webView: WebView
private lateinit var root: FrameLayout
private lateinit var loadingView: View
private lateinit var errorView: View
private var initialLoadFinished = false
private var mainFrameFailed = false

// Guarda o pedido de arquivo feito pelo site.
private var filePathCallback: ValueCallback\<Array<Uri>>? = null

// Abre o seletor nativo do Android e devolve o arquivo escolhido ao site.
private val fileChooserLauncher =
    registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->

        val resultado = if (result.resultCode == RESULT_OK) {
            WebChromeClient.FileChooserParams.parseResult(
                result.resultCode,
                result.data
            )
        } else {
            null
        }

        filePathCallback?.onReceiveValue(resultado)
        filePathCallback = null
    }

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
        if (!task.isSuccessful) {
            Log.e(
                "SANTOS_FCM",
                "Não foi possível obter o token",
                task.exception
            )
            return@addOnCompleteListener
        }

        val token = task.result
        Log.d("SANTOS_FCM", "Token atual do aplicativo: $token")

        val dados = hashMapOf(
            "token" to token,
            "tipo" to "android_app",
            "atualizadoEm" to System.currentTimeMillis()
        )

        FirebaseFirestore.getInstance()
            .collection("subscribers")
            .document(token)
            .set(dados)
            .addOnSuccessListener {
                Log.d(
                    "SANTOS_FCM",
                    "Token salvo no Firestore com sucesso"
                )
            }
            .addOnFailureListener { erro ->
                Log.e(
                    "SANTOS_FCM",
                    "Erro ao salvar token no Firestore",
                    erro
                )
            }
    }

    if (
        android.os.Build.VERSION.SDK_INT >= 33 &&
        ActivityCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            1001
        )
    }

    root = FrameLayout(this).apply {
        setBackgroundColor(Color.rgb(5, 12, 8))
    }

    webView = WebView(this).apply {
        setBackgroundColor(Color.rgb(5, 12, 8))
        // Renderiza desde o primeiro frame por baixo da tela de carregamento.
        // O loading continua cobrindo o conteúdo até onPageFinished().
        visibility = View.VISIBLE
    }

    loadingView = createLoadingView()
    errorView = createErrorView()

    root.addView(
        webView,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    root.addView(
        loadingView,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )
    root.addView(
        errorView,
        FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
    )

    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true

    webView.settings.userAgentString =
        webView.settings.userAgentString + " SANTOSXZG7-APP"

    /*
     * Mantém o WebChromeClient que o app já usava,
     * acrescentando somente o suporte ao \<input type="file">.
     */
    webView.webChromeClient = object : WebChromeClient() {

        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback\<Array<Uri>>?,
            fileChooserParams: FileChooserParams?
        ): Boolean {

            // Cancela qualquer pedido anterior que tenha ficado aberto.
            this@MainActivity.filePathCallback?.onReceiveValue(null)

            this@MainActivity.filePathCallback = filePathCallback

            return try {
                val chooserIntent =
                    fileChooserParams?.createIntent()
                        ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }

                fileChooserLauncher.launch(chooserIntent)
                true

            } catch (erro: Exception) {
                Log.e(
                    "SANTOS_FILE",
                    "Erro ao abrir seletor de arquivos",
                    erro
                )

                this@MainActivity.filePathCallback?.onReceiveValue(null)
                this@MainActivity.filePathCallback = null
                false
            }
        }
    }

    webView.webViewClient = object : WebViewClient() {

        override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
            super.onPageStarted(view, url, favicon)
            mainFrameFailed = false
            if (!initialLoadFinished) showLoading()
        }

        override fun onReceivedError(
            view: WebView?,
            request: WebResourceRequest?,
            error: WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            if (request?.isForMainFrame == true) {
                mainFrameFailed = true
                showError()
            }
        }

        override fun onPageFinished(
            view: WebView?,
            url: String?
        ) {
            super.onPageFinished(view, url)

            if (!mainFrameFailed) {
                initialLoadFinished = true
                showContent()
            }

            view?.evaluateJavascript(
                """
                (function() {
                    if (!navigator.userAgent.includes('SANTOSXZG7-APP')) {
                        return;
                    }

                    const botao = document.getElementById('notify');

                    if (botao) {
                        const bloco = botao.parentElement;

                        if (bloco) {
                            bloco.style.display = 'none';
                        } else {
                            botao.style.display = 'none';
                        }
                    }
                })();
                """.trimIndent(),
                null
            )
        }
    }

    val urlInicial =
        intent.getStringExtra("url")
            ?: "https://santosxzg7-noticias.netlify.app/"

    // Anexa a interface primeiro. Isso evita iniciar o carregamento da WebView
    // antes de ela estar ligada à janela da Activity.
    setContentView(root)

    // Só depois inicia a navegação.
    webView.loadUrl(urlInicial)

    onBackPressedDispatcher.addCallback(
        this,
        object : OnBackPressedCallback(true) {

            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
    )
}

private fun createLoadingView(): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        setBackgroundColor(Color.rgb(5, 12, 8))

        addView(TextView(this@MainActivity).apply {
            text = "SANTOSXZG7"
            textSize = 28f
            setTextColor(Color.rgb(72, 255, 128))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        addView(TextView(this@MainActivity).apply {
            text = "CENTRAL OFICIAL"
            textSize = 13f
            setTextColor(Color.rgb(190, 202, 194))
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 28)
        })

        addView(ProgressBar(this@MainActivity).apply {
            isIndeterminate = true
        })

        addView(TextView(this@MainActivity).apply {
            text = "Carregando novidades..."
            textSize = 14f
            setTextColor(Color.rgb(190, 202, 194))
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        })
    }
}

private fun createErrorView(): View {
    return LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(48, 48, 48, 48)
        setBackgroundColor(Color.rgb(5, 12, 8))
        visibility = View.GONE

        addView(TextView(this@MainActivity).apply {
            text = "SANTOSXZG7"
            textSize = 26f
            setTextColor(Color.rgb(72, 255, 128))
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
        })

        addView(TextView(this@MainActivity).apply {
            text = "Não foi possível carregar a Central.\nConfira sua conexão e tente novamente."
            textSize = 15f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            setPadding(0, 18, 0, 24)
        })

        addView(Button(this@MainActivity).apply {
            text = "TENTAR NOVAMENTE"
            setOnClickListener {
                initialLoadFinished = false
                mainFrameFailed = false
                showLoading()
                webView.reload()
            }
        })
    }
}

private fun showLoading() {
    webView.visibility = View.VISIBLE
    errorView.visibility = View.GONE
    loadingView.visibility = View.VISIBLE
}

private fun showContent() {
    loadingView.visibility = View.GONE
    errorView.visibility = View.GONE
    webView.visibility = View.VISIBLE
}

private fun showError() {
    webView.visibility = View.INVISIBLE
    loadingView.visibility = View.GONE
    errorView.visibility = View.VISIBLE
}

override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)

    val url = intent.getStringExtra("url")

    if (url != null && ::webView.isInitialized) {
        webView.loadUrl(url)
    }
}
}
