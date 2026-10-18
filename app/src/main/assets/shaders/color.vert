#version 300 es

layout(location = 0) in vec3 a_Position;

uniform mat4 u_Mvp;

void main() {
    gl_Position = u_Mvp * vec4(a_Position, 1.0);
}
