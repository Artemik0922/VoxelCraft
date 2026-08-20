#version 330 core

layout (location = 0) in vec3 aPos;
layout (location = 1) in vec2 aTexCoord;
layout (location = 2) in float aLayer;
layout (location = 3) in float aShade;

uniform mat4 projection;
uniform mat4 view;

out vec2 TexCoord;
flat out int Layer;
out float Shade;

void main() {
    // Positions already carry the billboard offset, built on the CPU from
    // the camera basis, so no per-vertex rotation is needed here.
    gl_Position = projection * view * vec4(aPos, 1.0);
    TexCoord = aTexCoord;
    Layer = int(aLayer + 0.5);
    Shade = aShade;
}
