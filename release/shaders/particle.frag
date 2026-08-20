#version 330 core

in vec2 TexCoord;
flat in int Layer;
in float Shade;

uniform sampler2DArray blockTextures;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(blockTextures, vec3(TexCoord, float(Layer)));

    // Chips inherit the source block's cut-outs
    if (texColor.a < 0.1) discard;

    FragColor = vec4(texColor.rgb * Shade, 1.0);
}
