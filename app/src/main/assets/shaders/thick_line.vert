#version 300 es

layout(location = 0) in vec3 a_Start;
layout(location = 1) in vec3 a_End;
layout(location = 2) in float a_Side;
layout(location = 3) in float a_EndSelect;

uniform mat4 u_Mvp;
uniform vec2 u_Viewport;
uniform float u_ThicknessPx;

out float v_Side;

void main() {
    vec4 startClip = u_Mvp * vec4(a_Start, 1.0);
    vec4 endClip = u_Mvp * vec4(a_End, 1.0);

    bool atEnd = a_EndSelect > 0.5;
    vec4 posClip = atEnd ? endClip : startClip;

    vec2 viewport = max(u_Viewport, vec2(1.0));
    vec2 startNdc = startClip.xy / max(startClip.w, 0.0001);
    vec2 endNdc = endClip.xy / max(endClip.w, 0.0001);

    vec2 direction = normalize((endNdc - startNdc) * viewport);
    vec2 normal = vec2(-direction.y, direction.x);
    vec2 offsetPx = normal * (u_ThicknessPx * 0.5) * a_Side;
    vec2 offsetNdc = (offsetPx / viewport) * 2.0;

    posClip.xy += offsetNdc * posClip.w;
    gl_Position = posClip;
    v_Side = a_Side;
}
