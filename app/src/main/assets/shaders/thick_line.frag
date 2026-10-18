#version 300 es

precision mediump float;

uniform vec4 u_Color;

in float v_Side;
out vec4 o_FragColor;

void main() {
    float alpha = 1.0 - smoothstep(0.75, 1.0, abs(v_Side));
    o_FragColor = vec4(u_Color.rgb, u_Color.a * alpha);
}
