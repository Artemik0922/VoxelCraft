#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in float aShade;

uniform mat4 mvp;
uniform float lightLevel;
uniform vec3 sunColor;

out vec2 TexCoord;
out float Shade;

void main() {
    gl_Position = mvp * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    Shade = aShade;
}
