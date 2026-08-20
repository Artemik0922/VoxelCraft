#version 330 core

in vec2 TexCoord;
in vec3 VertexColor;
in float Shade;

uniform sampler2D mobTexture;
uniform vec3 tint;

out vec4 FragColor;

void main() {
    // Use texture if available, otherwise just color
    vec4 tex = texture(mobTexture, TexCoord);

    // If texture is mostly transparent/white, use vertex color
    vec3 color;
    if (tex.a < 0.1) {
        color = VertexColor * Shade * tint;
    } else {
        color = tex.rgb * VertexColor * Shade * tint;
    }

    FragColor = vec4(color, 1.0);
}
