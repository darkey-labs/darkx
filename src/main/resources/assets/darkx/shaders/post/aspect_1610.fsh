#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Растянутое разрешение: берём центральную часть кадра шириной TARGET / aspect и растягиваем на весь экран.
const float TARGET = 1.6;

void main() {
    vec2 size = vec2(textureSize(InSampler, 0));
    float f = min(1.0, TARGET / (size.x / size.y));
    vec2 uv = vec2((texCoord.x - 0.5) * f + 0.5, texCoord.y);
    fragColor = vec4(texture(InSampler, uv).rgb, 1.0);
}
