#version 330 core

in vec2 TexCoord;
in vec4 TintColor;

uniform sampler2D uiTexture;
uniform int useTexture;

out vec4 FragColor;

void main() {
    vec4 color = TintColor;

    if (useTexture == 1) {
        color *= texture(uiTexture, TexCoord);
    }

    if (color.a < 0.002) discard;

    FragColor = color;
}
