#version 300 es

precision mediump float;

uniform vec4 u_Color;

out vec4 o_FragColor;

void main() {
    vec2 offset = gl_PointCoord - vec2(0.5);
    float radius = length(offset) * 2.0;
    if (radius > 1.0) {
        discard;
    }
    float alpha = 1.0 - smoothstep(0.82, 1.0, radius);
    o_FragColor = vec4(u_Color.rgb, u_Color.a * alpha);
}
