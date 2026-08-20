#version 330 core

in vec3 vColor;
in float vFade;

uniform float alpha;
uniform vec3 tint;

out vec4 FragColor;

void main() {
    // Flat white squares at 80% opacity: plain alpha blending, no glow,
    // no gradients, no blur. The tint dims and blue-shifts the deck at
    // night so clouds stop glowing white after sunset.
    FragColor = vec4(vColor * tint, alpha * vFade);
}