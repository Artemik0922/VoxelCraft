#version 330 core

in vec2 TexCoord;
in float Shade;

uniform sampler2D skin;
/** Ambient + sun, so the model sits in the same light as the world. */
uniform float lightLevel;
uniform vec3 sunColor;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(skin, TexCoord);

    if (texColor.a < 0.05) discard;

    // Same per-face shading the blocks use, no ambient occlusion
    vec3 result = texColor.rgb * Shade * lightLevel * sunColor;

    FragColor = vec4(result, texColor.a);
}
