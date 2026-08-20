#version 330 core

in vec3 vColor;
in float vFade;

uniform float alpha;

out vec4 FragColor;

void main() {
    // Flat white squares at 80% opacity: plain alpha blending, no glow,
    // no gradients, no blur.
    FragColor = vec4(vColor, alpha * vFade);
}