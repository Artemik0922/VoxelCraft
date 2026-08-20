#version 330 core

in vec2 TexCoord;
in float Shade;

uniform sampler2D skin;
uniform float lightLevel;
uniform vec3 sunColor;

out vec4 FragColor;

void main() {
    vec4 texColor = texture(skin, TexCoord);
    if (texColor.a < 0.5) discard;
    vec3 result = texColor.rgb * Shade * lightLevel * sunColor;
    FragColor = vec4(result, texColor.a);
}
