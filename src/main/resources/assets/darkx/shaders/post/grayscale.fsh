#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 color = texture(InSampler, texCoord);
    float luma = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    // слегка приглушаем, чтобы ЧБ-картинка не била по глазам
    fragColor = vec4(vec3(luma * 0.85), 1.0);
}
