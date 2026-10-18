#version 300 es

layout(location = 0) in vec3 a_Position;

uniform mat4 u_Mvp;
uniform float u_PointSize;

void main() {
    gl_Position = u_Mvp * vec4(a_Position, 1.0);
    gl_PointSize = u_PointSize;
}
