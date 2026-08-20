#version 330 core

in vec2 TexCoord;
in float Shade;
flat in int Layer;

uniform sampler2DArray blockTextures;
uniform float lightLevel;
uniform vec3 sunColor;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(blockTextures, vec3(TexCoord, float(Layer)));

    if (texColor.a < 0.5) discard;

    // No fog here: the held item should never fade with view distance
    vec3 result = texColor.rgb * Shade * lightLevel * sunColor;

    FragColor = vec4(result, 1.0);
}
