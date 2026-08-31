package com.gecko.core.markdown

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.json.JSONObject

/**
 * Renders a ```mermaid fenced block (flowcharts, sequence/class diagrams, and — since Mermaid
 * also supports `pie`/`xychart-beta` — basic charts too) via a local, bundled mermaid.js in a
 * WebView. No network access: the library ships as an asset, not loaded from a CDN.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MermaidDiagram(source: String, isDark: Boolean, modifier: Modifier = Modifier) {
    // WebView's default viewport (no zoom/scale override) already reports scrollHeight in CSS
    // pixels that match Android dp 1:1 — running that through Dp.toPx()/toDp() here would divide
    // by the device's density a second time and truncate the WebView well short of its content.
    var contentHeightDp by remember { mutableIntStateOf(0) }

    AndroidView(
        modifier = modifier.fillMaxWidth().height(if (contentHeightDp > 0) contentHeightDp.dp else 120.dp),
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onRendered(heightPx: Int) {
                            Handler(Looper.getMainLooper()).post {
                                contentHeightDp = (heightPx + 8).coerceAtLeast(1)
                            }
                        }
                    },
                    "AndroidBridge",
                )
            }
        },
        update = { webView ->
            webView.loadDataWithBaseURL(
                "file:///android_asset/",
                buildHtml(source, isDark),
                "text/html",
                "utf-8",
                null,
            )
        },
    )
}

private fun buildHtml(source: String, isDark: Boolean): String {
    val theme = if (isDark) "dark" else "default"
    val encodedSource = JSONObject.quote(source)
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>
          html, body { margin: 0; padding: 0; background: transparent; }
          #container { display: inline-block; }
          svg { max-width: 100%; height: auto; }
        </style>
        </head>
        <body>
        <div id="container"></div>
        <script src="mermaid.min.js"></script>
        <script>
          mermaid.initialize({ startOnLoad: false, theme: '$theme', securityLevel: 'loose' });
          mermaid.render('geckoMermaidDiagram', $encodedSource).then(function (result) {
            document.getElementById('container').innerHTML = result.svg;
            AndroidBridge.onRendered(document.getElementById('container').scrollHeight);
          }).catch(function (err) {
            document.body.innerText = 'Could not render diagram: ' + (err && err.message ? err.message : err);
            AndroidBridge.onRendered(document.body.scrollHeight);
          });
        </script>
        </body>
        </html>
    """.trimIndent()
}
