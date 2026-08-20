#version 330 core

layout (location = 0) in vec3 aPos;

uniform mat4 projection;
uniform mat4 view;

out vec3 Direction;

void main() {
    Direction = aPos;

    // Strip translation from the view matrix so the sky never moves with the
    // camera, then force z = w so the cube always lands on the far plane.
    mat4 rotView = mat4(mat3(view));
    vec4 pos = projection * rotView * vec4(aPos, 1.0);
    gl_Position = pos.xyww;
}
