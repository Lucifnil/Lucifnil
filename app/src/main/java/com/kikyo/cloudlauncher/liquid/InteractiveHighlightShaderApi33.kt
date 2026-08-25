package com.kikyo.cloudlauncher.liquid

import android.annotation.SuppressLint
import android.graphics.RuntimeShader
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.toArgb

/** Android 13+ implementation kept out of the API-26 class verifier path. */
@RequiresApi(33)
@SuppressLint("NewApi")
internal class InteractiveHighlightShaderApi33 {
    private val runtime = RuntimeShader(
        """
            uniform float2 size;
            layout(color) uniform half4 color;
            uniform float radius;
            uniform float2 position;
            half4 main(float2 coord) {
                float dist = distance(coord, position);
                float intensity = smoothstep(radius, radius * 0.5, dist);
                return color * intensity;
            }
        """.trimIndent()
    )

    fun brush(
        size: Size,
        color: Color,
        radius: Float,
        position: Offset,
    ): ShaderBrush {
        runtime.setFloatUniform("size", size.width, size.height)
        runtime.setColorUniform("color", color.toArgb())
        runtime.setFloatUniform("radius", radius)
        runtime.setFloatUniform("position", position.x, position.y)
        return ShaderBrush(runtime)
    }
}
