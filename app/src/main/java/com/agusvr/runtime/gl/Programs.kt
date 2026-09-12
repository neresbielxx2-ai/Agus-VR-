package com.agusvr.runtime.gl

import android.opengl.GLES30
import android.util.Log

/**
 * Agus VR Runtime — programas de shader (GLSL ES 3.0).
 * Quatro programas enxutos:
 *  LIT   → objetos 3D com luz direcional + fog
 *  UNLIT → linhas, feixes, marcadores
 *  TEX   → painéis de UI (textura de canvas 2D)
 *  EXT   → passthrough de câmera (sampler externo)
 */
class Programs {

    var lit = 0; var unlit = 0; var tex = 0; var ext = 0
        private set

    // Cache de uniforms
    var litMvp = 0; var litModel = 0; var litColor = 0; var litLight = 0
    var litEmissive = 0; var litEye = 0; var litFogColor = 0; var litFogDensity = 0
    var unlitMvp = 0; var unlitColor = 0
    var texMvp = 0; var texSampler = 0; var texAlpha = 0
    var extMvp = 0; var extSampler = 0; var extAlpha = 0; var extTexMat = 0

    fun compile() {
        lit = link(VS_LIT, FS_LIT)
        unlit = link(VS_UNLIT, FS_UNLIT)
        tex = link(VS_TEX, FS_TEX)
        // O programa de passthrough depende de extensão específica do driver
        // (GL_OES_EGL_image_external_essl3). Se não existir, o app segue sem
        // passthrough — jamais deve fechar por isso.
        ext = try {
            link(VS_TEX, FS_EXT)
        } catch (e: Throwable) {
            Log.w("AgusGL", "Shader de passthrough indisponível neste GPU: ${e.message}")
            0
        }

        litMvp = GLES30.glGetUniformLocation(lit, "uMVP")
        litModel = GLES30.glGetUniformLocation(lit, "uModel")
        litColor = GLES30.glGetUniformLocation(lit, "uColor")
        litLight = GLES30.glGetUniformLocation(lit, "uLightDir")
        litEmissive = GLES30.glGetUniformLocation(lit, "uEmissive")
        litEye = GLES30.glGetUniformLocation(lit, "uEye")
        litFogColor = GLES30.glGetUniformLocation(lit, "uFogColor")
        litFogDensity = GLES30.glGetUniformLocation(lit, "uFogDensity")

        unlitMvp = GLES30.glGetUniformLocation(unlit, "uMVP")
        unlitColor = GLES30.glGetUniformLocation(unlit, "uColor")

        texMvp = GLES30.glGetUniformLocation(tex, "uMVP")
        texSampler = GLES30.glGetUniformLocation(tex, "uTex")
        texAlpha = GLES30.glGetUniformLocation(tex, "uAlpha")

        if (ext != 0) {
            extMvp = GLES30.glGetUniformLocation(ext, "uMVP")
            extSampler = GLES30.glGetUniformLocation(ext, "uTex")
            extAlpha = GLES30.glGetUniformLocation(ext, "uAlpha")
            extTexMat = GLES30.glGetUniformLocation(ext, "uTexMat")
        }
    }

    private fun shader(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("AgusGL", "Shader erro: " + GLES30.glGetShaderInfoLog(s))
            throw RuntimeException("Shader compile failed")
        }
        return s
    }

    private fun link(vs: String, fs: String): Int {
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, shader(GLES30.GL_VERTEX_SHADER, vs))
        GLES30.glAttachShader(p, shader(GLES30.GL_FRAGMENT_SHADER, fs))
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            Log.e("AgusGL", "Link erro: " + GLES30.glGetProgramInfoLog(p))
            throw RuntimeException("Program link failed")
        }
        return p
    }

    companion object {
        // Atributos fixos: 0=pos, 1=normal, 2=uv
        const val A_POS = 0
        const val A_NORMAL = 1
        const val A_UV = 2

        private const val HEADER_V = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec2 aUV;
uniform mat4 uMVP;
uniform mat4 uModel;
"""

        const val VS_LIT = HEADER_V + """
out vec3 vNormal;
out vec3 vWorld;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

        const val FS_LIT = """#version 300 es
precision mediump float;
in vec3 vNormal;
in vec3 vWorld;
uniform vec4 uColor;
uniform vec3 uLightDir;
uniform float uEmissive;
uniform vec3 uEye;
uniform vec3 uFogColor;
uniform float uFogDensity;
out vec4 fragColor;
void main() {
    vec3 n = normalize(vNormal);
    float diff = max(dot(n, -uLightDir), 0.0);
    vec3 base = uColor.rgb * (0.38 + 0.62 * diff) + uColor.rgb * uEmissive;
    float d = distance(vWorld, uEye);
    float fog = clamp(1.0 - exp(-uFogDensity * d), 0.0, 1.0);
    fragColor = vec4(mix(base, uFogColor, fog), uColor.a);
}
"""

        const val VS_UNLIT = """#version 300 es
layout(location=0) in vec3 aPos;
uniform mat4 uMVP;
void main() {
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

        const val FS_UNLIT = """#version 300 es
precision mediump float;
uniform vec4 uColor;
out vec4 fragColor;
void main() {
    fragColor = uColor;
}
"""

        const val VS_TEX = """#version 300 es
layout(location=0) in vec3 aPos;
layout(location=2) in vec2 aUV;
uniform mat4 uMVP;
out vec2 vUV;
void main() {
    vUV = aUV;
    gl_Position = uMVP * vec4(aPos, 1.0);
}
"""

        const val FS_TEX = """#version 300 es
precision mediump float;
in vec2 vUV;
uniform sampler2D uTex;
uniform float uAlpha;
out vec4 fragColor;
void main() {
    vec4 c = texture(uTex, vUV);
    fragColor = vec4(c.rgb, c.a * uAlpha);
}
"""

        const val FS_EXT = """#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require
precision mediump float;
in vec2 vUV;
uniform samplerExternalOES uTex;
uniform mat4 uTexMat;
uniform float uAlpha;
out vec4 fragColor;
void main() {
    vec4 c = texture(uTex, (uTexMat * vec4(vUV, 0.0, 1.0)).xy);
    fragColor = vec4(c.rgb, uAlpha);
}
"""
    }
}
