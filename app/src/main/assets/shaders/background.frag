#version 300 es
#extension GL_OES_EGL_image_external_essl3 : require

precision mediump float;

uniform samplerExternalOES u_Texture;

in vec2 v_TexCoord;
out vec4 o_FragColor;

void main() {
    vec3 yuv = texture(u_Texture, v_TexCoord).rgb;
    yuv -= vec3(16.0 / 255.0, 128.0 / 255.0, 128.0 / 255.0);

    const mat3 yuvToRgb = mat3(
        1.164, 1.164, 1.164,
        0.0, -0.392, 2.017,
        1.596, -0.813, 0.0
    );

    o_FragColor = vec4(yuvToRgb * yuv, 1.0);
}
