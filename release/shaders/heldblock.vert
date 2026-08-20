#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in float aShade;
layout (location = 3) in float aFace;

uniform mat4 projection;
uniform mat4 view;
uniform mat4 model;
/** Atlas array layer for each of the six faces. */
uniform int faceLayers[6];

out vec2 TexCoord;
out float Shade;
flat out int Layer;

void main() {
    gl_Position = projection * view * model * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    Shade = aShade;
    Layer = faceLayers[int(aFace + 0.5)];
}
